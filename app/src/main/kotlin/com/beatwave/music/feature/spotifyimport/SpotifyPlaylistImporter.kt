package com.beatwave.music.feature.spotifyimport

import com.beatwave.music.db.MusicDatabase
import com.beatwave.music.db.entities.ArtistEntity
import com.beatwave.music.db.entities.PlaylistEntity
import com.beatwave.music.db.entities.PlaylistSongMap
import com.beatwave.music.db.entities.Song
import com.beatwave.music.db.entities.SongEntity
import com.beatwave.music.models.MediaMetadata
import com.beatwave.music.models.toMediaMetadata
import com.beatwave.music.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.spotify.PublicSpotifyMetadataClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/** Non-UI workflow for importing a public Spotify playlist into the local library. */
class SpotifyPlaylistImporter(
    private val database: MusicDatabase,
) {
    suspend fun importPlaylistByLink(
        link: String,
        onProgress: suspend (playlistName: String, currentSongIndex: Int, totalSongs: Int) -> Unit,
    ): String {
        val playlistId = extractPlaylistId(link)
            ?: throw IllegalArgumentException("Invalid Spotify playlist link")

        val metadata = PublicSpotifyMetadataClient().getPlaylistMetadata(playlistId).getOrThrow()
        val songs = metadata.tracks.map { track ->
            Song(
                song = SongEntity(
                    id = track.id,
                    title = track.name,
                    duration = track.durationMs / 1000,
                    thumbnailUrl = track.album?.images?.firstOrNull()?.url ?: metadata.imageUrl,
                ),
                artists = track.artists.map { artist ->
                    ArtistEntity(id = artist.id ?: "", name = artist.name)
                },
            )
        }

        if (songs.isEmpty()) {
            throw IllegalStateException("No tracks found in the playlist. Make sure it's public.")
        }

        val totalSongs = songs.size
        onProgress(metadata.name, 0, totalSongs)
        val completedCount = AtomicInteger(0)
        val semaphore = Semaphore(4)

        val matchedMedia = coroutineScope {
            songs.map { song ->
                async {
                    semaphore.withPermit {
                        var resultMedia: MediaMetadata? = null
                        try {
                            resultMedia = resolveTrack(song)
                        } catch (error: Exception) {
                            reportException(error)
                        } finally {
                            onProgress(metadata.name, completedCount.incrementAndGet(), totalSongs)
                        }
                        resultMedia
                    }
                }
            }.awaitAll().filterNotNull()
        }

        persistPlaylist(
            playlistId = "SPOTIFY_PLAYLIST_LINK_$playlistId",
            title = metadata.name,
            thumbnailUrl = metadata.imageUrl,
            matchedMedia = matchedMedia,
        )
        return metadata.name
    }

    private suspend fun resolveTrack(song: Song): MediaMetadata? {
        val candidatesById = mutableMapOf<String, SongItem>()
        val engine = DeterministicTrackResolutionEngine(
            searchClient = object : YouTubeSearchClient<YouTubeCandidate> {
                override suspend fun search(query: String): List<YouTubeCandidate> {
                    val items = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                        .getOrNull()?.items
                        ?.filterIsInstance<SongItem>()
                        ?.distinctBy { it.id }
                        .orEmpty()
                    items.forEach { candidatesById[it.id] = it }
                    return items.map { item ->
                        YouTubeCandidate(
                            videoId = item.id,
                            title = item.title,
                            artist = item.artists.joinToString(" ") { it.name },
                            durationSec = item.duration,
                        )
                    }
                }
            },
        )
        val source = SourceTrackMetadata(
            title = song.title,
            artist = song.artists.joinToString(" ") { it.name },
            durationMs = song.song.duration * 1000,
        )
        return engine.resolve(source)?.let { candidatesById[it.videoId]?.toMediaMetadata() }
    }

    private suspend fun persistPlaylist(
        playlistId: String,
        title: String,
        thumbnailUrl: String?,
        matchedMedia: List<MediaMetadata>,
    ) {
        database.withTransaction {
            val existing = this@withTransaction.playlist(playlistId).first()
            val now = LocalDateTime.now()
            val entity = existing?.playlist?.copy(
                name = title,
                bookmarkedAt = existing.playlist.bookmarkedAt ?: now,
                lastUpdateTime = now,
                thumbnailUrl = thumbnailUrl,
                isEditable = true,
            ) ?: PlaylistEntity(
                id = playlistId,
                name = title,
                bookmarkedAt = now,
                lastUpdateTime = now,
                thumbnailUrl = thumbnailUrl,
                isEditable = true,
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

    private fun extractPlaylistId(link: String): String? {
        val trimmed = link.trim()
        if (trimmed.length == 22 && trimmed.all { it.isLetterOrDigit() }) return trimmed
        if (trimmed.startsWith("spotify:playlist:")) {
            val id = trimmed.substringAfter("spotify:playlist:").substringBefore("?").substringBefore(":")
            if (id.length == 22) return id
        }
        if (trimmed.contains("playlist/")) {
            val id = trimmed.substringAfter("playlist/").substringBefore("?").substringBefore("/")
            if (id.length == 22) return id
        }
        return null
    }
}
