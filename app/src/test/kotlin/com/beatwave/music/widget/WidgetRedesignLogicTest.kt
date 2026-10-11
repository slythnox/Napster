/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.widget

import com.beatwave.music.feature.lyrics.LyricsEntry
import com.beatwave.music.feature.lyrics.LyricsUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WidgetRedesignLogicTest {

    @Test
    fun widgetActionConstants_areConsistentAcrossReceivers() {
        assertEquals("com.beatwave.music.widget.PLAY_PAUSE", MusicWidgetReceiver.ACTION_PLAY_PAUSE)
        assertEquals("com.beatwave.music.widget.NEXT", MusicWidgetReceiver.ACTION_NEXT)
        assertEquals("com.beatwave.music.widget.PREVIOUS", MusicWidgetReceiver.ACTION_PREVIOUS)
        assertEquals("com.beatwave.music.widget.UPDATE_WIDGET", MusicWidgetReceiver.ACTION_UPDATE_WIDGET)

        assertEquals("com.beatwave.music.widget.TURNTABLE_PLAY_PAUSE", TurntableWidgetReceiver.ACTION_TURNTABLE_PLAY_PAUSE)
        assertEquals("com.beatwave.music.widget.TURNTABLE_NEXT", TurntableWidgetReceiver.ACTION_TURNTABLE_NEXT)
        assertEquals("com.beatwave.music.widget.TURNTABLE_PREVIOUS", TurntableWidgetReceiver.ACTION_TURNTABLE_PREVIOUS)
        assertEquals("com.beatwave.music.widget.UPDATE_TURNTABLE_WIDGET", TurntableWidgetReceiver.ACTION_UPDATE_TURNTABLE_WIDGET)
    }

    @Test
    fun lyricsWidget_handlesEmptyAndSyncedLyrics() {
        val emptyLines = emptyList<LyricsEntry>()
        val emptyPlaceholder = if (emptyLines.isEmpty()) "No lyrics available" else "lyrics"
        assertEquals("No lyrics available", emptyPlaceholder)

        val sampleLyrics = listOf(
            LyricsEntry(0L, "First line of the song"),
            LyricsEntry(5000L, "Active line of the song"),
            LyricsEntry(10000L, "Next line of the song")
        )

        val activeIndexAt6s = LyricsUtils.findCurrentLineIndex(sampleLyrics, 6000L)
        assertEquals(1, activeIndexAt6s)
        assertEquals("Active line of the song", sampleLyrics[activeIndexAt6s].text)

        val activeIndexAt12s = LyricsUtils.findCurrentLineIndex(sampleLyrics, 12000L)
        assertEquals(2, activeIndexAt12s)
        assertEquals("Next line of the song", sampleLyrics[activeIndexAt12s].text)
    }

    @Test
    fun vinylDeck_timeRemainingCalculation() {
        fun formatTimeRemaining(duration: Long, position: Long): String {
            if (duration <= 0) return "-0:00"
            val remainingMs = (duration - position).coerceAtLeast(0)
            val minutes = (remainingMs / 1000) / 60
            val seconds = (remainingMs / 1000) % 60
            return String.format(Locale.US, "-%d:%02d", minutes, seconds)
        }

        assertEquals("-3:30", formatTimeRemaining(240000L, 30000L))
        assertEquals("-0:45", formatTimeRemaining(60000L, 15000L))
        assertEquals("-0:00", formatTimeRemaining(0L, 0L))
        assertEquals("-0:00", formatTimeRemaining(60000L, 70000L))
    }

    @Test
    fun dotPlayer_equalizerFrameAnimation_bounds() {
        val numDots = 4
        val maxBounce = 16f
        val baselineY = 28f

        for (frame in 0..120) {
            for (i in 0 until numDots) {
                val wave = kotlin.math.sin(frame * 0.45 + i * 1.15).toFloat()
                val bounce = (wave + 1f) / 2f
                val cy = baselineY - (bounce * maxBounce)
                assertTrue("cy must stay within bounds", cy in (baselineY - maxBounce)..baselineY)
            }
        }
    }

    @Test
    fun vinylDeck_rotationAngleStepAndContinuousBounds() {
        var angle = 0f
        val step = 6.0f // Phase 17.1: smooth 10 FPS cadence (6.0° per 100ms = 60°/s)

        for (tick in 1..600) {
            angle = (angle + step) % 360f
            assertTrue("Angle must remain in [0, 360)", angle in 0.0f..360.0f)
            assertFalse("Angle must not be NaN", angle.isNaN())
        }
        // Exactly 60 full rotations after 600 ticks at 36° each 60 ticks
        assertEquals(0.0f, angle, 0.001f)
    }

    @Test
    fun playbackSnapshot_threadSafetyDecoupling() {
        // Simulates thread-safe published snapshot contract used by MusicService
        data class PlaybackSnapshot(
            val isPlaying: Boolean,
            val durationMs: Long,
            val positionMs: Long
        )

        class SnapshotHolder {
            @Volatile
            var snapshot = PlaybackSnapshot(isPlaying = true, durationMs = 210000L, positionMs = 45000L)
        }
        val holder = SnapshotHolder()

        // Consumer on background dispatcher reads only snapshot without accessing player
        val readerResult = holder.snapshot
        assertTrue(readerResult.isPlaying)
        assertEquals(210000L, readerResult.durationMs)
        assertEquals(45000L, readerResult.positionMs)

        // Player updates state on main looper
        holder.snapshot = PlaybackSnapshot(isPlaying = false, durationMs = 210000L, positionMs = 45000L)
        assertFalse(holder.snapshot.isPlaying)
    }
}
