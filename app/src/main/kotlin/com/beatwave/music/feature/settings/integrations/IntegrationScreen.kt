/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.feature.settings.integrations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.beatwave.music.constants.ListenBrainzEnabledKey
import com.beatwave.music.constants.ListenBrainzTokenKey
import com.beatwave.music.utils.rememberPreference
import com.beatwave.music.LocalPlayerAwareWindowInsets
import com.beatwave.music.R
import com.beatwave.music.ui.component.IconButton
import com.beatwave.music.ui.component.IntegrationCard
import com.beatwave.music.ui.component.IntegrationCardItem
import com.beatwave.music.ui.utils.appTopBarWindowInsets
import com.beatwave.music.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (listenBrainzEnabled, onListenBrainzEnabledChange) = rememberPreference(
        key = ListenBrainzEnabledKey,
        defaultValue = false
    )
    val (listenBrainzToken, onListenBrainzTokenChange) = rememberPreference(
        key = ListenBrainzTokenKey,
        defaultValue = ""
    )
    var showListenBrainzTokenEditor by remember { mutableStateOf(false) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        IntegrationCard(
            title = stringResource(R.string.general),
            items = listOf(
                IntegrationCardItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.lastfm_integration)) },
                    onClick = {
                        navController.navigate("settings/integrations/lastfm")
                    }
                ),
                IntegrationCardItem(
                    icon = painterResource(R.drawable.edit),
                    title = { Text(stringResource(R.string.listenbrainz_scrobbling)) },
                    description = {
                        Text(
                            if (listenBrainzToken.isBlank()) {
                                stringResource(R.string.set_listenbrainz_token)
                            } else {
                                stringResource(R.string.listenbrainz_token_set)
                            }
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.Switch(
                            checked = listenBrainzEnabled,
                            onCheckedChange = onListenBrainzEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (listenBrainzEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = {
                        showListenBrainzTokenEditor = true
                    }
                )
            )
        )
    }

    if (showListenBrainzTokenEditor) {
        com.beatwave.music.ui.component.TextFieldDialog(
            initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(listenBrainzToken),
            onDone = { data ->
                onListenBrainzTokenChange(data)
                showListenBrainzTokenEditor = false
            },
            onDismiss = { showListenBrainzTokenEditor = false },
            singleLine = true,
            maxLines = 1,
            isInputValid = { it.isNotEmpty() },
            extraContent = {
                com.beatwave.music.ui.component.InfoLabel(text = stringResource(R.string.listenbrainz_scrobbling_description))
            }
        )
    }

    TopAppBar(
        windowInsets = appTopBarWindowInsets(),
        title = { Text(stringResource(R.string.integrations)) },
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
        }
    )
}
