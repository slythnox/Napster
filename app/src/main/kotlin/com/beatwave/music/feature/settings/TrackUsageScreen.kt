/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.feature.settings

import android.app.ActivityManager
import android.net.TrafficStats
import android.os.Debug
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import com.beatwave.music.LocalDatabase
import com.beatwave.music.LocalPlayerAwareWindowInsets
import com.beatwave.music.LocalPlayerConnection
import com.beatwave.music.R
import com.beatwave.music.core.extensions.tryOrNull
import com.beatwave.music.ui.component.ActionPromptDialog
import com.beatwave.music.ui.component.IconButton
import com.beatwave.music.ui.component.Material3SettingsGroup
import com.beatwave.music.ui.component.Material3SettingsItem
import com.beatwave.music.ui.utils.appTopBarWindowInsets
import com.beatwave.music.ui.utils.backToMain
import com.beatwave.music.ui.utils.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class, DelicateCoilApi::class)
@Composable
fun TrackUsageScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val imageDiskCache = context.imageLoader.diskCache
    val playerCache = LocalPlayerConnection.current?.service?.playerCache
    val downloadCache = LocalPlayerConnection.current?.service?.downloadCache
    val coroutineScope = rememberCoroutineScope()

    // ── Storage ──────────────────────────────────────────────────────────────
    var imageCacheSize by remember { mutableLongStateOf(imageDiskCache?.size ?: 0L) }
    var playerCacheSize by remember { mutableLongStateOf(tryOrNull { playerCache?.cacheSpace } ?: 0L) }
    var downloadCacheSize by remember { mutableLongStateOf(tryOrNull { downloadCache?.cacheSpace } ?: 0L) }

    LaunchedEffect(imageDiskCache) {
        while (isActive) {
            delay(1000)
            imageCacheSize = imageDiskCache?.size ?: 0L
        }
    }
    LaunchedEffect(playerCache) {
        while (isActive) {
            delay(1000)
            playerCacheSize = tryOrNull { playerCache?.cacheSpace } ?: 0L
        }
    }
    LaunchedEffect(downloadCache) {
        while (isActive) {
            delay(1000)
            downloadCacheSize = tryOrNull { downloadCache?.cacheSpace } ?: 0L
        }
    }

    // ── Memory (RAM) ──────────────────────────────────────────────────────────
    val activityManager = context.getSystemService(ActivityManager::class.java)
    var totalRam by remember { mutableLongStateOf(0L) }
    var availRam by remember { mutableLongStateOf(0L) }
    var appPssKb by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            totalRam = memInfo.totalMem
            availRam = memInfo.availMem
            val dbgInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(dbgInfo)
            appPssKb = dbgInfo.totalPss.toLong()
            delay(2000)
        }
    }

    val ramProgress by animateFloatAsState(
        targetValue = if (totalRam > 0) {
            ((totalRam - availRam).toFloat() / totalRam.toFloat()).coerceIn(0f, 1f)
        } else 0f,
        label = "ramProgress",
    )

    // ── Network (current session via TrafficStats) ────────────────────────────
    val uid = context.applicationInfo.uid
    val rxStart = remember { TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L) }
    val txStart = remember { TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L) }
    var sessionRx by remember { mutableLongStateOf(0L) }
    var sessionTx by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2000)
            sessionRx = (TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L) - rxStart).coerceAtLeast(0L)
            sessionTx = (TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L) - txStart).coerceAtLeast(0L)
        }
    }

    // ── Listening stats ───────────────────────────────────────────────────────
    val nowMs = remember { System.currentTimeMillis() }
    val dayAgoMs = remember { nowMs - 24L * 3_600_000L }

    val todaySongCount by database
        .getUniqueSongCountInRange(dayAgoMs, nowMs)
        .collectAsState(initial = 0)
    val todayPlayTimeMs by database
        .getTotalPlayTimeInRange(dayAgoMs, nowMs)
        .collectAsState(initial = null)
    val librarySongCount by database
        .inLibrarySongsCount()
        .collectAsState(initial = 0)
    val totalEventCount by database
        .eventCount()
        .collectAsState(initial = 0)

    // ── Dialog ────────────────────────────────────────────────────────────────
    var showClearCacheDialog by remember { mutableStateOf(false) }

    if (showClearCacheDialog) {
        ActionPromptDialog(
            title = "Clear all caches",
            onDismiss = { showClearCacheDialog = false },
            onConfirm = {
                coroutineScope.launch(Dispatchers.IO) {
                    playerCache?.keys?.forEach { key -> playerCache.removeResource(key) }
                    imageDiskCache?.clear()
                }
                showClearCacheDialog = false
            },
            onCancel = { showClearCacheDialog = false },
            content = {
                Text("This will clear the song cache and image cache. Downloads will not be affected.")
            },
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        // Storage
        Material3SettingsGroup(
            title = "Storage",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text("Song cache") },
                    description = {
                        Column {
                            Text(formatFileSize(playerCacheSize))
                            UsageBar(used = playerCacheSize, max = 1024L * 1024 * 1024)
                        }
                    },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text("Image cache") },
                    description = {
                        Column {
                            Text(formatFileSize(imageCacheSize))
                            UsageBar(used = imageCacheSize, max = 512L * 1024 * 1024)
                        }
                    },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.download),
                    title = { Text("Downloaded songs") },
                    description = { Text(formatFileSize(downloadCacheSize)) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.library_music_outlined),
                    title = { Text("Library songs") },
                    description = { Text("$librarySongCount songs in library") },
                ),
            )
        )

        Spacer(Modifier.height(16.dp))

        // Memory
        Material3SettingsGroup(
            title = "Memory (RAM)",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.storage),
                    title = { Text("System RAM used") },
                    description = {
                        Column {
                            Text(
                                if (totalRam > 0) {
                                    "${formatFileSize(totalRam - availRam)} / ${formatFileSize(totalRam)}"
                                } else "Loading…"
                            )
                            if (totalRam > 0) {
                                LinearProgressIndicator(
                                    progress = { ramProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    strokeCap = StrokeCap.Round,
                                )
                            }
                        }
                    },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text("App memory (PSS)") },
                    description = { Text(formatFileSize(appPssKb * 1024L)) },
                ),
            )
        )

        Spacer(Modifier.height(16.dp))

        // Network
        Material3SettingsGroup(
            title = "Network (this session)",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.network_node),
                    title = { Text("Downloaded") },
                    description = { Text(formatFileSize(sessionRx)) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.network_update),
                    title = { Text("Uploaded / sent") },
                    description = { Text(formatFileSize(sessionTx)) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.wifi_proxy),
                    title = { Text("Note") },
                    description = {
                        Text(
                            "Session data only — traffic from before opening this screen is not counted. " +
                                "Daily totals require the Usage Access permission.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ),
            )
        )

        Spacer(Modifier.height(16.dp))

        // Listening
        Material3SettingsGroup(
            title = "Listening (last 24 h)",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.history),
                    title = { Text("Unique tracks listened") },
                    description = { Text("$todaySongCount tracks") },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.timer),
                    title = { Text("Total listening time") },
                    description = { Text(formatListenDuration(todayPlayTimeMs ?: 0L)) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_history),
                    title = { Text("All-time play events") },
                    description = { Text("$totalEventCount plays recorded") },
                ),
            )
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = { showClearCacheDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("Clear song & image cache")
            }
        }

        Spacer(Modifier.padding(bottom = 30.dp))
    }

    TopAppBar(
        windowInsets = appTopBarWindowInsets(),
        title = { Text("Track Usage") },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

/** Thin animated progress bar clamped to used/max. */
@Composable
private fun UsageBar(used: Long, max: Long) {
    val progress by animateFloatAsState(
        targetValue = if (max > 0) (used.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f,
        label = "usageBar",
    )
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        strokeCap = StrokeCap.Round,
    )
}

/** Converts milliseconds to a human-readable duration string, e.g. "1h 23m 45s". */
private fun formatListenDuration(ms: Long): String {
    if (ms <= 0L) return "0 s"
    val totalSec = ms / 1000L
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return buildString {
        if (h > 0) append("${h}h ")
        if (m > 0 || h > 0) append("${m}m ")
        if (h == 0L) append("${s}s")
    }.trim()
}
