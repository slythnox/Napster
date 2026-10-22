package com.beatwave.music.playback.resolver.addon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {

    @Test
    fun testTitleCleaningRemovesVideoAndTopicNoise() {
        val target = TrackMatcher.Target(
            title = "Starboy (Official Music Video) - Topic",
            artist = "The Weeknd",
            durationSec = 230,
        )
        val queries = TrackMatcher.queries(target)
        assertTrue(queries.isNotEmpty())
        assertTrue("Queries should not contain 'Official Music Video'", queries.none { it.contains("Official Music Video") })
        assertTrue("Queries should not contain '- Topic'", queries.none { it.contains("- Topic") })
    }

    @Test
    fun testMatchScoringPrefersExactMatch() {
        val target = TrackMatcher.Target(
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationSec = 200,
        )

        val exactScore = TrackMatcher.matchScore(
            candidateTitle = "Blinding Lights",
            candidateArtist = "The Weeknd",
            candidateDurationSec = 200,
            target = target,
        )

        val remixScore = TrackMatcher.matchScore(
            candidateTitle = "Blinding Lights (Remix)",
            candidateArtist = "The Weeknd",
            candidateDurationSec = 240,
            target = target,
        )

        assertTrue("Exact match score ($exactScore) must exceed remix score ($remixScore)", exactScore > remixScore)
        assertTrue("Exact match should pass quality threshold", exactScore >= 100)
    }

    @Test
    fun testDurationMismatchPenalized() {
        val target = TrackMatcher.Target(
            title = "Shape of You",
            artist = "Ed Sheeran",
            durationSec = 233,
        )

        val closeDurationScore = TrackMatcher.matchScore(
            candidateTitle = "Shape of You",
            candidateArtist = "Ed Sheeran",
            candidateDurationSec = 235,
            target = target,
        )

        val farDurationScore = TrackMatcher.matchScore(
            candidateTitle = "Shape of You",
            candidateArtist = "Ed Sheeran",
            candidateDurationSec = 350,
            target = target,
        )

        assertTrue(closeDurationScore > farDurationScore)
    }
}
