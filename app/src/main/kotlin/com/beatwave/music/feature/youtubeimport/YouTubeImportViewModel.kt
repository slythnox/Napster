package com.beatwave.music.feature.youtubeimport

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatwave.music.db.MusicDatabase
import com.beatwave.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YouTubeImportUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class YouTubeImportProgress(
    val playlistName: String,
    val currentSongIndex: Int,
    val totalSongs: Int,
    val percent: Float,
    val isFinished: Boolean = false,
)

@HiltViewModel
class YouTubeImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    private val playlistImporter = YouTubePlaylistImporter(database)

    private val _uiState = MutableStateFlow(YouTubeImportUiState())
    val uiState: StateFlow<YouTubeImportUiState> = _uiState.asStateFlow()

    private val _importProgress = MutableStateFlow<YouTubeImportProgress?>(null)
    val importProgress: StateFlow<YouTubeImportProgress?> = _importProgress.asStateFlow()

    private var importJob: Job? = null

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _importProgress.update { null }
        _uiState.update { it.copy(isLoading = false) }
    }

    fun dismissImportProgress() {
        _importProgress.update { null }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun importPlaylistByLink(link: String) {
        importJob?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            _importProgress.update { null }
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                playlistImporter.importPlaylistByLink(link) { playlistName, currentSongIndex, totalSongs ->
                    if (currentSongIndex == 0) {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    _importProgress.update {
                        YouTubeImportProgress(
                            playlistName = playlistName,
                            currentSongIndex = currentSongIndex,
                            totalSongs = totalSongs,
                            percent = if (totalSongs > 0) currentSongIndex.toFloat() / totalSongs else 0f,
                        )
                    }
                }

                _importProgress.update { old ->
                    old?.copy(isFinished = true) ?: YouTubeImportProgress(
                        playlistName = "",
                        currentSongIndex = 0,
                        totalSongs = 0,
                        percent = 1.0f,
                        isFinished = true,
                    )
                }
            } catch (error: Exception) {
                if (error !is kotlinx.coroutines.CancellationException) {
                    reportException(error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to import playlist",
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
