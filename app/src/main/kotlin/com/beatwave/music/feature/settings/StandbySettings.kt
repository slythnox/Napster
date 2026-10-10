/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.beatwave.music.ui.component.GlassSwitchCompat as Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.beatwave.music.LocalPlayerAwareWindowInsets
import com.beatwave.music.R
import com.beatwave.music.constants.AodDimLevelKey
import com.beatwave.music.constants.AodModeEnabledKey
import com.beatwave.music.constants.AodPixelShiftEnabledKey
import com.beatwave.music.ui.component.IconButton
import com.beatwave.music.ui.component.Material3SettingsGroup
import com.beatwave.music.ui.component.Material3SettingsItem
import com.beatwave.music.ui.utils.appTopBarWindowInsets
import com.beatwave.music.ui.utils.backToMain
import com.beatwave.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandbySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (aodModeEnabled, onAodModeEnabledChange) = rememberPreference(
        AodModeEnabledKey,
        defaultValue = false
    )
    val (aodPixelShiftEnabled, onAodPixelShiftEnabledChange) = rememberPreference(
        AodPixelShiftEnabledKey,
        defaultValue = true
    )
    val (aodDimLevel, onAodDimLevelChange) = rememberPreference(
        AodDimLevelKey,
        defaultValue = 0.75f
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.standby),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.play),
                    title = { Text(stringResource(R.string.launch_standby)) },
                    description = { Text(stringResource(R.string.launch_standby_desc)) },
                    onClick = { navController.navigate("standby") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.bedtime),
                    title = { Text(stringResource(R.string.standby_mode)) },
                    description = { Text(stringResource(R.string.standby_mode_desc)) },
                    trailingContent = {
                        Switch(
                            checked = aodModeEnabled,
                            onCheckedChange = onAodModeEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (aodModeEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAodModeEnabledChange(!aodModeEnabled) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.sync),
                    title = { Text(stringResource(R.string.pixel_shift)) },
                    description = { Text(stringResource(R.string.pixel_shift_desc)) },
                    trailingContent = {
                        Switch(
                            checked = aodPixelShiftEnabled,
                            onCheckedChange = onAodPixelShiftEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (aodPixelShiftEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAodPixelShiftEnabledChange(!aodPixelShiftEnabled) }
                )
            )
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    TopAppBar(
        windowInsets = appTopBarWindowInsets(),
        title = { Text(stringResource(R.string.standby)) },
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
        }
    )
}
