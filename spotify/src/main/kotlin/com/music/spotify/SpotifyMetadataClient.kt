package com.music.spotify

import com.music.spotify.models.SpotifyTrack

/** Public, metadata-only boundary used by playlist import. */
interface SpotifyMetadataClient {
    suspend fun getPlaylistMetadata(playlistId: String): Result<SpotifyPlaylistMetadata>
    suspend fun getTrackMetadata(trackId: String): Result<SpotifyTrack>
}

data class SpotifyPlaylistMetadata(
    val id: String,
    val name: String,
    val tracks: List<SpotifyTrack>,
    val imageUrl: String? = null,
)

/** Adapter for the existing public Spotify embed parser. No account state is used. */
class PublicSpotifyMetadataClient(
    private val client: Spotify = Spotify,
) : SpotifyMetadataClient {
    override suspend fun getPlaylistMetadata(playlistId: String): Result<SpotifyPlaylistMetadata> =
        client.fetchPlaylistEmbedTracks(playlistId).map { tracks ->
            SpotifyPlaylistMetadata(
                id = playlistId,
                name = "Spotify playlist",
                tracks = tracks,
                imageUrl = tracks.firstNotNullOfOrNull { it.album?.images?.firstOrNull()?.url },
            )
        }

    override suspend fun getTrackMetadata(trackId: String): Result<SpotifyTrack> =
        Result.failure(UnsupportedOperationException(
            "Public track metadata lookup is not exposed by the current SPClient; use playlist metadata or add it to this adapter."
        ))
}
