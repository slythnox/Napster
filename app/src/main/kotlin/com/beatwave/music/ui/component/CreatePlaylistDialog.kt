/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.component

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.beatwave.music.LocalDatabase
import com.beatwave.music.R
import com.beatwave.music.db.entities.PlaylistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = false,
    onPlaylistCreated: ((String) -> Unit)? = null,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    TextFieldDialog(
        icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
        title = { Text(text = stringResource(R.string.create_playlist)) },
        initialTextFieldValue = TextFieldValue(initialTextFieldValue ?: ""),
        onDismiss = onDismiss,
        onDone = { playlistName ->
            coroutineScope.launch(Dispatchers.IO) {
                val playlistEntity = PlaylistEntity(
                    name = playlistName,
                    browseId = null,
                    bookmarkedAt = LocalDateTime.now(),
                    isEditable = true,
                )

                database.query {
                    insert(playlistEntity)
                }

                withContext(Dispatchers.Main) {
                    onPlaylistCreated?.invoke(playlistEntity.id)
                }
            }
        }
    )
}

