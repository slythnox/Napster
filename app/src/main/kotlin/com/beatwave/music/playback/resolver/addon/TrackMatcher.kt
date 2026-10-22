package com.beatwave.music.playback.resolver.addon

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Normalizes, strips YouTube noise, and scores candidate tracks from streaming add-ons
 * to verify they match the currently playing song before dispatching audio streams.
 */
object TrackMatcher {

    data class Target(
        val title: String,
        val artist: String = "",
        val durationSec: Int? = null,
        val album: String? = null,
        val isExplicit: Boolean? = null,
        val isVideo: Boolean = false,
    )

    private val ARTIST_SEPARATORS = Regex("""\s*(?:,|&|feat\.?|ft\.?|vs\.?|x|\+|/|;|with)\s*""", RegexOption.IGNORE_CASE)
    private val NOISE_PATTERNS = listOf(
        Regex("""(?i)\b(?:official\s*(?:audio|video|music\s*video|lyric\s*video)?)\b"""),
        Regex("""(?i)\b(?:hd|4k|hq|audio|video|lyrics?)\b"""),
        Regex("""(?i)\b(?:full\s*(?:audio|video|track|song))\b"""),
        Regex("""(?i)\b(?:visualizer|clip\s*officiel)\b"""),
        Regex("""(?i)\s*[-–—]\s*topic\b"""),
    )
    private val VERSION_KEYWORDS = listOf(
        "remix", "mix", "live", "acoustic", "instrumental", "karaoke", "edit",
        "extended", "radio edit", "slowed", "reverb", "sped up", "cover", "orchestral"
    )

    /** Builds clean queries for the search endpoint, stripped of YouTube packaging noise. */
    fun queries(target: Target): List<String> {
        val cleanTitle = cleanTitle(target.title, target.artist)
        if (cleanTitle.isBlank()) return emptyList()
        val artist = primaryArtist(target.artist)
        return if (artist.isBlank()) {
            listOf(cleanTitle)
        } else {
            listOf("$cleanTitle $artist", cleanTitle).distinct()
        }
    }

    fun cleanTitle(title: String, artist: String = ""): String {
        var t = normalize(title)
        if (artist.isNotBlank()) {
            val normArtist = normalize(artist)
            t = t.replace(Regex("(?i)^\\s*${Regex.escape(normArtist)}\\s*[-–—:]\\s*"), "")
        }
        for (pattern in NOISE_PATTERNS) {
            t = t.replace(pattern, "")
        }
        // Remove trailing bracketed noise e.g. (Official Audio)
        t = t.replace(Regex("""\(\s*\)|\[\s*\]"""), "")
        return t.replace(Regex("""\s+"""), " ").trim()
    }

    fun primaryArtist(artist: String): String =
        normalize(artist).split(ARTIST_SEPARATORS).firstOrNull()?.trim().orEmpty()

    /**
     * Scores a candidate track against the target.
     * Returns a score >= 0 if it is a valid match (higher is better), or -1 if rejected.
     */
    fun matchScore(
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationSec: Int?,
        target: Target,
    ): Int {
        val candTitleClean = cleanTitle(candidateTitle, candidateArtist)
        val targetTitleClean = cleanTitle(target.title, target.artist)

        // 1. Version keyword mismatch guard (e.g. remix vs original)
        for (keyword in VERSION_KEYWORDS) {
            val candHas = candTitleClean.contains(keyword, ignoreCase = true)
            val targetHas = targetTitleClean.contains(keyword, ignoreCase = true)
            if (candHas != targetHas) {
                return -1 // Rejected: one is a remix/live/acoustic and the other is not
            }
        }

        // 2. Duration check (reject if delta exceeds 15 seconds)
        if (candidateDurationSec != null && target.durationSec != null && target.durationSec > 0) {
            val delta = abs(candidateDurationSec - target.durationSec)
            if (delta > 15) {
                return -1 // Rejected: duration mismatch exceeds tolerance
            }
        }

        var score = 100

        // 3. Exact title bonus
        if (candTitleClean.equals(targetTitleClean, ignoreCase = true)) {
            score += 50
        } else if (candTitleClean.contains(targetTitleClean, ignoreCase = true) ||
            targetTitleClean.contains(candTitleClean, ignoreCase = true)
        ) {
            score += 25
        }

        // 4. Artist match
        val primaryTargetArtist = primaryArtist(target.artist)
        val primaryCandArtist = primaryArtist(candidateArtist)
        if (primaryTargetArtist.isNotBlank() && primaryCandArtist.isNotBlank()) {
            if (primaryCandArtist.equals(primaryTargetArtist, ignoreCase = true)) {
                score += 50
            } else if (normalize(candidateArtist).contains(primaryTargetArtist, ignoreCase = true) ||
                normalize(target.artist).contains(primaryCandArtist, ignoreCase = true)
            ) {
                score += 25
            }
        }

        // 5. Duration closeness bonus
        if (candidateDurationSec != null && target.durationSec != null && target.durationSec > 0) {
            val delta = abs(candidateDurationSec - target.durationSec)
            score += (15 - delta).coerceAtLeast(0) * 2
        }

        return score
    }

    private fun normalize(str: String): String {
        val decomposed = Normalizer.normalize(str, Normalizer.Form.NFD)
        return decomposed.replace(Regex("""\p{M}"""), "")
            .replace('–', '-')
            .replace('—', '-')
            .lowercase(Locale.ROOT)
            .trim()
    }
}
