/**
 * Napster Music Player
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.feature.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.beatwave.music.BuildConfig
import com.beatwave.music.LocalPlayerAwareWindowInsets
import com.beatwave.music.R
import com.beatwave.music.ui.component.IconButton
import com.beatwave.music.ui.component.Material3SettingsGroup
import com.beatwave.music.ui.component.Material3SettingsItem
import com.beatwave.music.ui.theme.rememberBrandFontFamily
import com.beatwave.music.ui.utils.appTopBarWindowInsets
import com.beatwave.music.ui.utils.backToMain
import com.beatwave.music.ui.utils.safeOpenUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val unknownString = stringResource(R.string.unknown)

    val cookieShape = MaterialShapes.Cookie7Sided.toShape()

    val installedDate = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val installTime = packageInfo.firstInstallTime
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(installTime))
        } catch (_: Exception) {
            unknownString
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // App Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    border = BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    ),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(92.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.napster_logo),
                            contentDescription = stringResource(R.string.wrapped_logo_content_description),
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            Text(
                text = "Napster",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = rememberBrandFontFamily(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.napster_logo),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )

                Text(
                    text = "v${BuildConfig.VERSION_NAME} • ${stringResource(if (BuildConfig.IS_NIGHTLY) R.string.build_nightly else R.string.build_stable)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Product Description Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "About Napster",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Napster is a free and open-source music player for Android featuring YouTube Music streaming, Liquid Glass interface, time-synced lyrics, offline downloads, queue management, and public Spotify playlist import.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }

        // Upstream Attribution & Open Source
        Material3SettingsGroup(
            title = "OPEN SOURCE & ATTRIBUTION",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.license_vivi),
                    title = { Text("Upstream Attribution") },
                    description = { Text("Built upon foundational work from BeatWave, CONVX, and vivi-music under GPL-3.0.") },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/beatlabs790/BeatWave/blob/main/LICENSE") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.link),
                    title = { Text("Core Technologies") },
                    description = { Text("Media3 ExoPlayer, Jetpack Compose, Room, Coil, and Kyant0/backdrop.") }
                )
            )
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Community Section
        Material3SettingsGroup(
            title = stringResource(R.string.community_section),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.github),
                    title = { Text(stringResource(R.string.github_repository)) },
                    description = { Text(stringResource(R.string.view_source_code)) },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/slythnox/Napster") }
                )
            )
        )
        Spacer(modifier = Modifier.height(20.dp))

        // App Information Section
        Material3SettingsGroup(
            title = stringResource(R.string.app_info_section),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.security),
                    title = { Text(stringResource(R.string.privacy)) },
                    description = { Text("View data storage, network flows, and privacy information") },
                    onClick = { navController.navigate("settings/privacy") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.deployed_app_update),
                    title = { Text(stringResource(R.string.installed_date_title)) },
                    description = { Text(installedDate) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.version_code)) },
                    description = { Text(BuildConfig.VERSION_CODE.toString()) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.license_vivi),
                    title = { Text(stringResource(R.string.license)) },
                    description = { Text("GPL-3.0 • Free Open Source Software") },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/beatlabs790/BeatWave/blob/main/LICENSE") }
                ),
            )
        )
        Spacer(modifier = Modifier.height(20.dp))
    }

    TopAppBar(
        windowInsets = appTopBarWindowInsets(),
        title = { Text(stringResource(R.string.about)) },
        navigationIcon = {
            IconButton(
                onClick = { onBack?.invoke() ?: navController.navigateUp() },
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
