/**
 * Napster Privacy & Data Information
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.beatwave.music.LocalDatabase
import com.beatwave.music.LocalPlayerAwareWindowInsets
import com.beatwave.music.R
import com.beatwave.music.constants.DisableScreenshotKey
import com.beatwave.music.constants.PauseListenHistoryKey
import com.beatwave.music.constants.PauseSearchHistoryKey
import com.beatwave.music.ui.component.DefaultDialog
import com.beatwave.music.ui.component.GlassSwitchCompat as Switch
import com.beatwave.music.ui.component.IconButton
import com.beatwave.music.ui.component.Material3SettingsGroup
import com.beatwave.music.ui.component.Material3SettingsItem
import com.beatwave.music.ui.utils.appTopBarWindowInsets
import com.beatwave.music.ui.utils.backToMain
import com.beatwave.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val database = LocalDatabase.current
    val (pauseListenHistory, onPauseListenHistoryChange) = rememberPreference(
        key = PauseListenHistoryKey,
        defaultValue = false
    )
    val (pauseSearchHistory, onPauseSearchHistoryChange) = rememberPreference(
        key = PauseSearchHistoryKey,
        defaultValue = false
    )
    val (disableScreenshot, onDisableScreenshotChange) = rememberPreference(
        key = DisableScreenshotKey,
        defaultValue = false
    )

    var showClearListenHistoryDialog by remember { mutableStateOf(false) }
    var showClearSearchHistoryDialog by remember { mutableStateOf(false) }

    if (showClearListenHistoryDialog) {
        DefaultDialog(
            onDismiss = { showClearListenHistoryDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.clear_listen_history_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showClearListenHistoryDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showClearListenHistoryDialog = false
                        database.query { clearListenHistory() }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showClearSearchHistoryDialog) {
        DefaultDialog(
            onDismiss = { showClearSearchHistoryDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.clear_search_history_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showClearSearchHistoryDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showClearSearchHistoryDialog = false
                        database.query { clearSearchHistory() }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

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

        // Privacy Overview Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Privacy & Data Disclosure",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Napster operates with zero analytics and zero tracking. All your library data, listening history, and preferences are stored locally on your device. Below is an accurate summary of how data is stored, requested, and managed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }

        // Section: User Privacy Controls
        Material3SettingsGroup(
            title = stringResource(R.string.listen_history),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.history),
                    title = { Text(stringResource(R.string.pause_listen_history)) },
                    trailingContent = {
                        Switch(
                            checked = pauseListenHistory,
                            onCheckedChange = onPauseListenHistoryChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (pauseListenHistory) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPauseListenHistoryChange(!pauseListenHistory) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.delete_history),
                    title = { Text(stringResource(R.string.clear_listen_history)) },
                    onClick = { showClearListenHistoryDialog = true }
                )
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.search_history),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.search_off),
                    title = { Text(stringResource(R.string.pause_search_history)) },
                    trailingContent = {
                        Switch(
                            checked = pauseSearchHistory,
                            onCheckedChange = onPauseSearchHistoryChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (pauseSearchHistory) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPauseSearchHistoryChange(!pauseSearchHistory) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.clear_all),
                    title = { Text(stringResource(R.string.clear_search_history)) },
                    onClick = { showClearSearchHistoryDialog = true }
                )
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.misc),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.screenshot),
                    title = { Text(stringResource(R.string.disable_screenshot)) },
                    description = { Text(stringResource(R.string.disable_screenshot_desc)) },
                    trailingContent = {
                        Switch(
                            checked = disableScreenshot,
                            onCheckedChange = onDisableScreenshotChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (disableScreenshot) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onDisableScreenshotChange(!disableScreenshot) }
                )
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Data Storage & External Flows Disclosure
        Material3SettingsGroup(
            title = "DATA STORAGE & NETWORK FLOWS",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.storage),
                    title = { Text("Local Database & Cache") },
                    description = { Text("Playlists, favorites, and history reside in a local Room SQLite database on your device. Audio caches and downloads are stored in app private storage.") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.network_update),
                    title = { Text("YouTube & InnerTube Requests") },
                    description = { Text("Search queries, metadata, audio streams, and artwork are fetched directly from YouTube/InnerTube servers. Subject to Google's Privacy Policy.") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.spotify),
                    title = { Text("Spotify Public Playlist Import") },
                    description = { Text("Spotify URLs are scraped anonymously for track names and artists. No Spotify account, login, or personal tokens are ever requested.") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.integration),
                    title = { Text("Optional Scrobblers & Integrations") },
                    description = { Text("Last.fm and ListenBrainz integrations transmit song scrobbles only when explicitly enabled and configured with your user token.") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.security),
                    title = { Text("Telemetry & Analytics") },
                    description = { Text("Napster contains zero analytics SDKs, advertising trackers, or telemetry frameworks. Crash logs remain entirely on your device unless manually shared.") }
                )
            )
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    TopAppBar(
        windowInsets = appTopBarWindowInsets(),
        title = { Text(stringResource(R.string.privacy)) },
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
        scrollBehavior = scrollBehavior
    )
}
