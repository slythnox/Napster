package com.beatwave.music.feature.youtubeimport

import com.beatwave.music.db.MusicDatabase
import com.beatwave.music.db.entities.PlaylistEntity
import com.beatwave.music.db.entities.PlaylistSongMap
import com.beatwave.music.models.MediaMetadata
import com.beatwave.music.models.toMediaMetadata
import com.beatwave.music.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.utils.completed
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/** Non-UI workflow for importing a public or unlisted YouTube Music playlist into the local library. */
class YouTubePlaylistImporter(
    private val database: MusicDatabase? = null,
) {
    suspend fun importPlaylistByLink(
        link: String,
        onProgress: suspend (playlistName: String, currentSongIndex: Int, totalSongs: Int) -> Unit,
    ): String {
        val playlistId = extractPlaylistId(link)
            ?: throw IllegalArgumentException("Invalid YouTube Music playlist link or ID")

        val playlistPage = YouTube.playlist(playlistId).completed().getOrNull()
            ?: throw IllegalStateException("Failed to load playlist. Ensure the playlist is public or unlisted.")

        val playlistTitle = playlistPage.playlist.title.ifBlank { "Imported Playlist" }
        val songs = playlistPage.songs
        if (songs.isEmpty()) {
            throw IllegalStateException("No tracks found in the playlist. Ensure the playlist is public or unlisted.")
        }

        val totalSongs = songs.size
        onProgress(playlistTitle, 0, totalSongs)

        val matchedMedia = mutableListOf<MediaMetadata>()
        for ((index, songItem) in songs.withIndex()) {
            try {
                matchedMedia.add(songItem.toMediaMetadata())
            } catch (error: Exception) {
                reportException(error)
            } finally {
                onProgress(playlistTitle, index + 1, totalSongs)
            }
        }

        persistPlaylist(
            playlistId = "YTM_PLAYLIST_$playlistId",
            rawBrowseId = playlistId,
            title = playlistTitle,
            thumbnailUrl = playlistPage.playlist.thumbnail,
            matchedMedia = matchedMedia,
        )
        return playlistTitle
    }

    private suspend fun persistPlaylist(
        playlistId: String,
        rawBrowseId: String,
        title: String,
        thumbnailUrl: String?,
        matchedMedia: List<MediaMetadata>,
    ) {
        val db = database ?: error("Database not initialized")
        db.withTransaction {
            val existing = this@withTransaction.playlist(playlistId).first()
            val now = LocalDateTime.now()
            val entity = existing?.playlist?.copy(
                name = title,
                browseId = rawBrowseId,
                bookmarkedAt = existing.playlist.bookmarkedAt ?: now,
                lastUpdateTime = now,
                thumbnailUrl = thumbnailUrl ?: existing.playlist.thumbnailUrl,
                isEditable = true,
                remoteSongCount = matchedMedia.size,
            ) ?: PlaylistEntity(
                id = playlistId,
                name = title,
                browseId = rawBrowseId,
                bookmarkedAt = now,
                lastUpdateTime = now,
                thumbnailUrl = thumbnailUrl,
                isEditable = true,
                remoteSongCount = matchedMedia.size,
            )

            if (existing == null) insert(entity) else update(entity)
            matchedMedia.forEach(::insert)
            clearPlaylist(playlistId)
            matchedMedia.forEachIndexed { index, metadata ->
                insert(
                    PlaylistSongMap(
                        playlistId = playlistId,
                        songId = metadata.id,
                        position = index,
                        setVideoId = metadata.setVideoId,
                    ),
                )
            }
            update(entity.copy(lastUpdateTime = now))
        }
    }

    fun extractPlaylistId(link: String): String? {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) return null

        // Handle URL containing 'list=' parameter (e.g. music.youtube.com/playlist?list=PL...)
        if (trimmed.contains("list=")) {
            val listParam = trimmed
                .substringAfter("list=")
                .substringBefore("&")
                .substringBefore(" ")
                .substringBefore("#")
                .trim()
            if (listParam.isNotBlank()) {
                return listParam.removePrefix("VL")
            }
        }

        // Direct playlist ID format (PL, VL, RDCLAK5uy_, OLAK5uy_, etc.)
        if (trimmed.matches(Regex("^(PL|VL|RD|OLAK5uy_)[a-zA-Z0-9_-]+$"))) {
            return trimmed.removePrefix("VL")
        }

        // General alphanumeric/hyphen/underscore token of valid length
        if (trimmed.length in 12..64 && trimmed.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            return trimmed.removePrefix("VL")
        }

        return null
    }
}
