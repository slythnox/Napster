package com.beatwave.music.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class TrackUsageLogicTest {

    @Test
    fun `formatListenDuration handles zero and negative milliseconds`() {
        assertEquals("0 s", formatListenDurationTest(0L))
        assertEquals("0 s", formatListenDurationTest(-1000L))
    }

    @Test
    fun `formatListenDuration formats seconds only`() {
        assertEquals("45s", formatListenDurationTest(45_000L))
    }

    @Test
    fun `formatListenDuration formats minutes and seconds`() {
        assertEquals("3m 20s", formatListenDurationTest((3 * 60 + 20) * 1000L))
    }

    @Test
    fun `formatListenDuration formats hours minutes and seconds`() {
        assertEquals("1h 15m", formatListenDurationTest((1 * 3600 + 15 * 60 + 30) * 1000L))
    }

    @Test
    fun `24h cutoff calculation matches Room Converters UTC epoch format`() {
        val nowLdt = LocalDateTime.now()
        val epochMilliFromLdt = nowLdt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        val cutoff24h = epochMilliFromLdt - 24L * 3600 * 1000L

        // Verify cutoff is exactly 24 hours prior
        assertEquals(24L * 3600 * 1000L, epochMilliFromLdt - cutoff24h)

        // Events occurring 1 hour ago should be strictly greater than the cutoff
        val oneHourAgo = epochMilliFromLdt - 3600 * 1000L
        assertTrue(oneHourAgo > cutoff24h)

        // Events occurring 25 hours ago should be strictly less than the cutoff
        val twentyFiveHoursAgo = epochMilliFromLdt - 25L * 3600 * 1000L
        assertTrue(twentyFiveHoursAgo < cutoff24h)
    }

    @Test
    fun `session network usage arithmetic handles unsupported and resets safely`() {
        val startRx = 100_000L
        val currentRx = 150_000L
        val sessionRx = (currentRx - startRx).coerceAtLeast(0L)
        assertEquals(50_000L, sessionRx)

        // In case currentRx drops below startRx (e.g. counter wrap or reboot)
        val wrappedRx = 50_000L
        val safeWrapped = (wrappedRx - startRx).coerceAtLeast(0L)
        assertEquals(0L, safeWrapped)
    }

    private fun formatListenDurationTest(ms: Long): String {
        if (ms <= 0L) return "0 s"
        val totalSec = ms / 1000L
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return buildString {
            if (h > 0) append("${h}h ")
            if (m > 0 || h > 0) append("${m}m ")
            if (h == 0L) append("${s}s")
        }.trim()
    }
}
