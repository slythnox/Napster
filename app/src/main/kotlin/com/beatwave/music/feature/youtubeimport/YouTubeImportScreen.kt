package com.beatwave.music.feature.youtubeimport

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.beatwave.music.LocalPlayerAwareWindowInsets
import com.beatwave.music.R
import com.beatwave.music.ui.component.DefaultDialog
import com.beatwave.music.ui.component.IconButton
import com.beatwave.music.ui.component.Material3SettingsGroup
import com.beatwave.music.ui.component.Material3SettingsItem
import com.beatwave.music.ui.utils.appTopBarWindowInsets
import com.beatwave.music.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeImportScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: YouTubeImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var playlistLink by rememberSaveable { mutableStateOf("") }
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        Text(
            text = "YouTube Music",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 16.dp)
        )

        Material3SettingsGroup(
            title = "Public playlist import",
            items = listOf(
                Material3SettingsItem(
                    title = { Text("No YouTube login required") },
                    description = { Text("Paste a public or unlisted YouTube Music playlist link below to import all tracks into your library.") },
                    icon = painterResource(R.drawable.music_note),
                    onClick = {}
                )
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Material3SettingsGroup(
            title = "Import via Playlist Link",
            items = listOf(
                Material3SettingsItem(
                    title = {
                        OutlinedTextField(
                            value = playlistLink,
                            onValueChange = { playlistLink = it },
                            label = { Text("YouTube Music Playlist Link or ID") },
                            placeholder = { Text("https://music.youtube.com/playlist?list=...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            trailingIcon = {
                                if (playlistLink.isNotEmpty()) {
                                    IconButton(
                                        onClick = { playlistLink = "" },
                                        onLongClick = {},
                                    ) {
                                        Icon(painterResource(R.drawable.close), contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    },
                    icon = painterResource(R.drawable.link),
                    onClick = {}
                )
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (playlistLink.isBlank()) {
                    android.widget.Toast.makeText(context, "Please enter a YouTube Music playlist link or ID first", android.widget.Toast.LENGTH_SHORT).show()
                    return@Button
                }
                viewModel.importPlaylistByLink(playlistLink)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !state.isLoading && playlistLink.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Importing...",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            } else {
                Text(
                    text = "Import Playlist",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 24.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(R.drawable.info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Playlists imported from YouTube Music are saved locally in your library with all tracks and metadata. You can play, edit, or cache them offline at any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(36.dp))
    }

    TopAppBar(
        windowInsets = appTopBarWindowInsets(),
        title = {},
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        )
    )

    importProgress?.let { progress ->
        val isFinished = progress.isFinished || progress.percent >= 1f
        DefaultDialog(
            onDismiss = {
                if (isFinished) {
                    viewModel.dismissImportProgress()
                } else {
                    viewModel.cancelImport()
                }
            },
            title = {
                Text(
                    text = if (isFinished) {
                        "Import Complete"
                    } else {
                        "Importing Playlist"
                    }
                )
            },
            buttons = {
                if (isFinished) {
                    Button(
                        onClick = { viewModel.dismissImportProgress() },
                        shape = CircleShape
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                } else {
                    TextButton(onClick = { viewModel.cancelImport() }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.playlists) + ": " + progress.playlistName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isFinished) {
                        "Imported ${progress.currentSongIndex} of ${progress.totalSongs} songs"
                    } else {
                        "Importing song ${progress.currentSongIndex} of ${progress.totalSongs}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { progress.percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }

    state.errorMessage?.let { error ->
        DefaultDialog(
            onDismiss = { viewModel.dismissError() },
            title = { Text("Error") },
            buttons = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
