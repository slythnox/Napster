package com.beatwave.music.feature.spotifyimport

/** Small deterministic boundary for resolving source metadata to a playable YouTube result. */
interface YouTubeSearchClient<Candidate> {
    suspend fun search(query: String): List<Candidate>
}

interface YouTubeStreamResolver<Stream> {
    suspend fun resolve(videoId: String): Stream
}

interface TrackResolutionEngine<Result> {
    suspend fun resolve(track: SourceTrackMetadata): Result?
}

data class SourceTrackMetadata(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Int? = null,
    val isrc: String? = null,
)

data class YouTubeCandidate(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSec: Int? = null,
    val isAudio: Boolean = true,
)

/** Candidate scoring is deliberately bounded and explainable for Phase 3. */
class DeterministicTrackResolutionEngine(
    private val searchClient: YouTubeSearchClient<YouTubeCandidate>,
) : TrackResolutionEngine<YouTubeCandidate> {
    override suspend fun resolve(track: SourceTrackMetadata): YouTubeCandidate? {
        val candidates = searchClient.search(buildQuery(track))
        if (candidates.isEmpty()) return null

        // Keep the importer from silently selecting an obviously unrelated result.
        // The threshold is intentionally conservative; callers can still decide how
        // to present a skipped track without changing the YouTube playback path.
        val ranked = candidates
            .distinctBy { it.videoId }
            .sortedByDescending { score(track, it) }
        return ranked.firstOrNull()?.takeIf { score(track, it) >= MIN_ACCEPTABLE_SCORE }
    }

    private fun buildQuery(track: SourceTrackMetadata): String =
        listOf(track.artist, track.title, track.album.orEmpty())
            .filter(String::isNotBlank)
            .joinToString(" ")

    private fun score(source: SourceTrackMetadata, candidate: YouTubeCandidate): Double {
        val title = titleSimilarity(source.title, candidate.title)
        val artist = similarity(source.artist, candidate.artist)
        val album = source.album?.takeIf { it.isNotBlank() }?.let {
            similarity(it, candidate.album.orEmpty())
        } ?: 0.5
        val duration = when {
            source.durationMs == null || candidate.durationSec == null -> 0.5
            kotlin.math.abs(source.durationMs / 1000 - candidate.durationSec) <= 3 -> 1.0
            kotlin.math.abs(source.durationMs / 1000 - candidate.durationSec) <= 10 -> 0.5
            else -> 0.0
        }
        val candidateText = normalize(candidate.title)
        val penalties = VERSION_PENALTIES.entries.fold(0.0) { total, entry ->
            total + if (containsVersionMarker(candidateText, entry.key)) entry.value else 0.0
        }
        val audioBonus = if (candidate.isAudio) 0.08 else -0.18
        return (title * 0.45 + artist * 0.30 + album * 0.10 + duration * 0.15 + audioBonus - penalties)
            .coerceIn(0.0, 1.0)
    }

    private fun titleSimilarity(source: String, candidate: String): Double {
        val fullSimilarity = similarity(source, candidate)
        val baseSimilarity = similarity(stripVersionSuffix(source), stripVersionSuffix(candidate))
        return maxOf(fullSimilarity, baseSimilarity)
    }

    private fun similarity(left: String, right: String): Double {
        val a = normalize(left)
        val b = normalize(right)
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        val aWords = a.split(' ').toSet()
        val bWords = b.split(' ').toSet()
        return (2.0 * aWords.intersect(bWords).size) / (aWords.size + bWords.size)
    }

    private fun stripVersionSuffix(value: String): String = value
        .replace(Regex("\\((live|remix|acoustic|karaoke|instrumental|remaster(ed)?|sped up|slowed|nightcore|8d)[^)]*\\)", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\[(live|remix|acoustic|karaoke|instrumental|remaster(ed)?|sped up|slowed|nightcore|8d)[^]]*]", RegexOption.IGNORE_CASE), " ")

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("\\b(feat|ft)\\.?\\b"), " ")
        .replace('&', ' ')
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsVersionMarker(text: String, marker: String): Boolean =
        Regex("(^|\\s)${Regex.escape(marker)}($|\\s)").containsMatchIn(text)

    private companion object {
        const val MIN_ACCEPTABLE_SCORE = 0.42
        val VERSION_PENALTIES = linkedMapOf(
            "live" to 0.18,
            "remix" to 0.16,
            "cover" to 0.20,
            "karaoke" to 0.25,
            "instrumental" to 0.18,
            "nightcore" to 0.22,
            "slowed" to 0.18,
            "sped up" to 0.18,
            "8d" to 0.20,
            "acoustic" to 0.12,
            "remastered" to 0.06,
            "remaster" to 0.06,
            "lyrics" to 0.10,
            "video" to 0.08,
        )
    }
}
