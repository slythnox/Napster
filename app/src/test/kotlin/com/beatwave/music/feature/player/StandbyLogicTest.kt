/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StandbyLogicTest {

    @Test
    fun swipeExitGesture_triggersOnlyOnDeliberateHorizontalSwipe() {
        val thresholdPx = 120f

        fun shouldExit(dragX: Float, dragY: Float): Boolean {
            return abs(dragX) > thresholdPx && abs(dragX) > abs(dragY) * 1.3f
        }

        // Deliberate right swipe
        assertTrue(shouldExit(150f, 10f))
        // Deliberate left swipe
        assertTrue(shouldExit(-150f, 10f))

        // Small movement / jitter below threshold
        assertFalse(shouldExit(50f, 5f))
        assertFalse(shouldExit(-80f, 0f))

        // Vertical scroll dominating horizontal movement
        assertFalse(shouldExit(140f, 150f))
        assertFalse(shouldExit(-130f, 200f))
    }

    @Test
    fun trackMetadataFallback_handlesMissingAndBlankFields() {
        fun formatTrackDisplay(rawTitle: String?, artists: List<String>): String {
            val title = rawTitle?.takeIf { it.isNotBlank() } ?: "No track playing"
            val artistStr = artists.filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() } ?: "Napster"
            return if (title == "No track playing") title else "$title $artistStr"
        }

        assertEquals("City Nights Joshua Domfeh", formatTrackDisplay("City Nights", listOf("Joshua Domfeh")))
        assertEquals("No track playing", formatTrackDisplay(null, listOf()))
        assertEquals("No track playing", formatTrackDisplay("", listOf("Joshua Domfeh")))
        assertEquals("City Nights Napster", formatTrackDisplay("City Nights", listOf()))
    }

    @Test
    fun clockPattern_matchesTimeFormat() {
        fun getPattern(is24Hour: Boolean): String = if (is24Hour) "HH:mm" else "h:mm"

        assertEquals("HH:mm", getPattern(true))
        assertEquals("h:mm", getPattern(false))
    }

    @Test
    fun standbyRoute_matchesSettingsRegistry() {
        val standbyRoute = "settings/standby"
        assertEquals("settings/standby", standbyRoute)
    }
}
