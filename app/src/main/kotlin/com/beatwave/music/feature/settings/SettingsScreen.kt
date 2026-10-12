/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.feature.settings

import com.beatwave.music.R
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.beatwave.music.BuildConfig
import com.beatwave.music.LocalPlayerAwareWindowInsets
import com.beatwave.music.ui.component.IconButton
import com.beatwave.music.ui.component.Material3SettingsGroup
import com.beatwave.music.ui.component.Material3SettingsItem
import com.beatwave.music.ui.navigation.Screens
import com.beatwave.music.ui.utils.appTopBarWindowInsets
import com.beatwave.music.ui.utils.backToMain
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchLower = searchQuery.trim().lowercase()

    androidx.compose.foundation.lazy.LazyColumn(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
    ) {
        item(key = "top_spacer") {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
                )
            )
        }

        // Search field — filters the settings catalog and jumps to the matching screen.
        item(key = "search_field") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = stringResource(R.string.search)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (searchLower.isEmpty()) {
        // Section: General
        item(key = "general_header") { SettingsSectionHeader("GENERAL") }
        item(key = "general_section") {
            SettingsSection {
                SettingsNavItem(
                    icon = painterResource(R.drawable.palette),
                    iconTint = Color(0xFFAF52DE),
                    title = stringResource(R.string.appearance),
                    subtitle = "Themes, fonts, icons, and visual style",
                    onClick = { navController.navigate("settings/appearance") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.play),
                    iconTint = Color(0xFFFF375F),
                    title = stringResource(R.string.player_and_audio),
                    subtitle = "Equaliser, crossfade, quality, and playback",
                    onClick = { navController.navigate("settings/player") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.bedtime),
                    iconTint = Color(0xFFFF9F0A),
                    title = stringResource(R.string.standby),
                    subtitle = stringResource(R.string.standby_description),
                    onClick = { navController.navigate("settings/standby") },
                )
            }
        }

        item(key = "spacer_1") { Spacer(Modifier.height(24.dp)) }

        // Section: Activity — history/stats moved here from the top bar, which now
        // only shows the logo and this settings/profile pill.
        item(key = "activity_header") { SettingsSectionHeader("ACTIVITY") }
        item(key = "activity_section") {
            SettingsSection {
                SettingsNavItem(
                    icon = painterResource(R.drawable.music_history),
                    iconTint = Color(0xFF32ADE6),
                    title = stringResource(R.string.history),
                    subtitle = "Your recently played tracks",
                    onClick = { navController.navigate("history") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.stats),
                    iconTint = Color(0xFFFFCC00),
                    title = stringResource(R.string.stats),
                    subtitle = "Listening insights and trends",
                    onClick = { navController.navigate("stats") },
                )
            }
        }

        item(key = "spacer_2") { Spacer(Modifier.height(24.dp)) }

        // Section: Content
        item(key = "content_header") { SettingsSectionHeader("CONTENT") }
        item(key = "content_section") {
            SettingsSection {
                SettingsNavItem(
                    icon = painterResource(R.drawable.language),
                    iconTint = Color(0xFF007AFF),
                    title = stringResource(R.string.content),
                    subtitle = "Language, sources, and search filters",
                    onClick = { navController.navigate("settings/content") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.link),
                    iconTint = Color(0xFF5856D6),
                    title = stringResource(R.string.modules),
                    subtitle = "Third-party integrations and extensions",
                    onClick = { navController.navigate("settings/modules") },
                )
            }
        }

        item(key = "spacer_3") { Spacer(Modifier.height(24.dp)) }

        // Section: Data & Privacy
        item(key = "privacy_header") { SettingsSectionHeader("DATA & PRIVACY") }
        item(key = "privacy_section") {
            SettingsSection {
                SettingsNavItem(
                    icon = painterResource(R.drawable.security),
                    iconTint = Color(0xFF007AFF),
                    title = stringResource(R.string.privacy),
                    subtitle = "Data handling and permissions",
                    onClick = { navController.navigate("settings/privacy") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.storage),
                    iconTint = Color(0xFF8E8E93),
                    title = stringResource(R.string.storage),
                    subtitle = "Cache, downloads, and local files",
                    onClick = { navController.navigate("settings/storage") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    iconTint = Color(0xFF30B0C7),
                    title = "Track Usage",
                    subtitle = "Storage, RAM, network and listening stats",
                    onClick = { navController.navigate("settings/track_usage") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.restore),
                    iconTint = Color(0xFF34C759),
                    title = stringResource(R.string.backup_restore),
                    subtitle = "Export or import your library",
                    onClick = { navController.navigate("settings/backup_restore") },
                )
            }
        }

        item(key = "spacer_4") { Spacer(Modifier.height(24.dp)) }

        // Section: About
        item(key = "about_header") { SettingsSectionHeader("ABOUT") }
        item(key = "about_section") {
            SettingsSection {
                SettingsNavItem(
                    icon = painterResource(R.drawable.info),
                    iconTint = Color(0xFF007AFF),
                    title = stringResource(R.string.about),
                    subtitle = "Version, licences, and credits",
                    onClick = { navController.navigate("settings/about") },
                )
            }
        }

        item(key = "spacer_5") { Spacer(Modifier.height(24.dp)) }

        item(key = "bottom_spacer") {
            Spacer(
                Modifier
                    .height(50.dp)
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))
            )
        }
        } else {
            item(key = "search_results") {
                val results = getAllSearchableSettings()
                    .filter {
                        it.title.lowercase().contains(searchLower) ||
                            it.description?.lowercase()?.contains(searchLower) == true
                    }
                if (results.isEmpty()) {
                    Text(
                        text = "No settings found for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 32.dp)
                    )
                } else {
                    Material3SettingsGroup(
                        items = results.map { setting ->
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.search),
                                title = { Text(setting.title) },
                                description = { Text(setting.category) },
                                onClick = { navController.navigate(setting.route) }
                            )
                        }
                    )
                }
            }
        }
    }

    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        windowInsets = appTopBarWindowInsets(),
    )
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = 20.dp,
            top = 8.dp,
            bottom = 8.dp,
        ),
    )
}

@Composable
private fun SettingsSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
private fun SettingsNavItem(
    icon: Painter,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon in rounded square
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(16.dp))

        // Title and optional subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Badge or chevron
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(8.dp))
        }

        Icon(
            painter = painterResource(R.drawable.chevron_right_px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
    )
}
