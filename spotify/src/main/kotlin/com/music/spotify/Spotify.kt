package com.music.spotify

import com.music.spotify.models.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.*

object Spotify {
    private fun randomUserAgent(): String {
        val osOptions = arrayOf(
            "Windows NT 10.0; Win64; x64",
            "Macintosh; Intel Mac OS X 10_15_7",
            "X11; Linux x86_64",
        )
        val chromeBase = 140
        val chromeMajor = chromeBase - (0..4).random()
        val chromePatch = (0..499).random()
        val os = osOptions.random()
        return "Mozilla/5.0 ($os) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$chromeMajor.0.$chromePatch.0 Safari/537.36"
    }

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            defaultRequest {
                header("User-Agent", randomUserAgent())
                header("app-platform", "WebPlayer")
                header("Origin", "https://open.spotify.com")
                header("Referer", "https://open.spotify.com/")
                header("Accept", "application/json")
            }
            expectSuccess = false
        }
    }

    class SpotifyException(
        val statusCode: Int,
        override val message: String,
        val retryAfterSec: Long = 0,
    ) : Exception(message)

    // JSON parsing helpers
    private fun JsonObject.obj(key: String): JsonObject? =
        try { this[key]?.takeIf { it !is JsonNull }?.jsonObject } catch (_: Exception) { null }

    private fun JsonObject.str(key: String): String? =
        try { this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull } catch (_: Exception) { null }

    private fun JsonObject.int(key: String): Int? =
        try { this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull } catch (_: Exception) { null }

    private fun JsonObject.arr(key: String): JsonArray? =
        try { this[key]?.takeIf { it !is JsonNull }?.jsonArray } catch (_: Exception) { null }

    val CURATED_SPOTIFY_PLAYLISTS = listOf(
        "37i9dQZF1DWXtlo6ENS92N", // Bollywood Central
        "37i9dQZF1DX5q67ZpWyRrZ", // Indie India
        "37i9dQZF1DX5cZuAHLNjGz", // Punjabi 101
        "37i9dQZF1DX0b1hHYQtJjp", // Just Good Music
        "37i9dQZF1DXcBWIGoYBM5M", // Today's Top Hits
        "37i9dQZEVXbMDoHDwVN2tF", // Top 50 - Global
        "37i9dQZF1DWUa8ZRTfalHk", // Pop Rising
        "37i9dQZF1DX4WYpdgoIcn6", // Chill Hits
        "37i9dQZF1DX0XUsuxWHRQd", // RapCaviar
        "37i9dQZF1DX4dyzvuaRJ0n", // Dance Hits
        "37i9dQZF1DX2Nc3B70tvx0", // Ultimate Indie
        "37i9dQZF1DWXRqgorJj26U", // Rock Classics
        "37i9dQZF1DWWQRwui0ExPn", // Lofi Beats
        "37i9dQZF1DX4sWSpwq3LiO", // Peaceful Piano
        "37i9dQZF1DX10zKzsJ2jva"  // Viva Latino
    )

    /**
     * Preserved for caller compatibility in recommendation seeding.
     * Account search is not enabled; recommendations rely on curated embed playlists.
     */
    suspend fun searchTrack(query: String): Result<String?> = Result.success(null)

    /**
     * Fetches metadata for tracks in a public Spotify playlist via the open embed page.
     * No credentials or account tokens required.
     */
    suspend fun fetchPlaylistEmbedTracks(playlistId: String): Result<List<SpotifyTrack>> = runCatching {
        val response = httpClient.get("https://open.spotify.com/embed/playlist/$playlistId")
        val html = response.bodyAsText()
        val regex = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""")
        val matchResult = regex.find(html) ?: throw SpotifyException(500, "Could not parse Spotify embed data")
        val jsonStr = matchResult.groupValues[1]

        val jsonElement = json.parseToJsonElement(jsonStr)
        val entity = jsonElement.jsonObject.obj("props")
            ?.obj("pageProps")
            ?.obj("state")
            ?.obj("data")
            ?.obj("entity") ?: throw SpotifyException(500, "Invalid Spotify embed entity")

        val trackList = entity.arr("trackList") ?: emptyList()
        val coverUrl = entity.obj("coverArt")?.arr("sources")?.firstOrNull()?.jsonObject?.str("url") ?: ""

        trackList.mapNotNull { trackElement ->
            val trackObj = trackElement.jsonObject
            val title = trackObj.str("title") ?: return@mapNotNull null
            val subtitle = trackObj.str("subtitle") ?: "Unknown Artist"
            val durationMs = trackObj.int("duration") ?: 0
            val uri = trackObj.str("uri") ?: ""
            val trackId = uri.substringAfterLast(":")

            val artists = subtitle.split(",").map { name ->
                SpotifySimpleArtist(id = "", name = name.trim())
            }

            SpotifyTrack(
                id = trackId.ifBlank { title.hashCode().toString() },
                name = title,
                artists = artists,
                album = SpotifySimpleAlbum(
                    id = "",
                    name = title,
                    images = listOf(SpotifyImage(url = coverUrl))
                ),
                durationMs = durationMs,
                uri = uri.ifEmpty { null }
            )
        }
    }

    /**
     * Provides algorithmic/curated recommendations by aggregating fresh tracks from
     * editorial playlists.
     */
    suspend fun getRecommendations(
        seedTracks: List<String> = emptyList(),
        limit: Int = 35
    ): Result<List<SpotifyTrack>> = runCatching {
        val randomPlaylists = CURATED_SPOTIFY_PLAYLISTS.shuffled().take(5)
        val allTracks = mutableListOf<SpotifyTrack>()

        for (pId in randomPlaylists) {
            fetchPlaylistEmbedTracks(pId).onSuccess { tracks ->
                allTracks.addAll(tracks)
            }
        }

        if (allTracks.isNotEmpty()) {
            allTracks.distinctBy { "${it.name.lowercase()}_${it.artists.firstOrNull()?.name?.lowercase()}" }
                .shuffled()
                .take(limit)
        } else {
            emptyList()
        }
    }
}
