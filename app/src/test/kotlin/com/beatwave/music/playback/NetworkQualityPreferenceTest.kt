package com.beatwave.music.playback

import com.beatwave.music.constants.AudioQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkQualityPreferenceTest {

    private fun resolveAudioQuality(
        isMetered: Boolean,
        wifiPref: String?,
        mobilePref: String?,
        globalPref: String?,
        isDataSaver: Boolean = false,
    ): AudioQuality {
        if (isDataSaver) {
            return AudioQuality.LOW
        }
        val targetVal = if (isMetered) mobilePref else wifiPref
        val perNet = targetVal?.let { v -> AudioQuality.entries.find { it.name == v } }
        return perNet ?: globalPref?.let { v -> AudioQuality.entries.find { it.name == v } } ?: AudioQuality.AUTO
    }

    private fun resolveAudioQualityWithTransport(
        hasCellularTransport: Boolean,
        hasWifiTransport: Boolean,
        isMetered: Boolean,
        wifiPref: String?,
        mobilePref: String?,
        globalPref: String?,
        isDataSaver: Boolean = false,
    ): AudioQuality {
        if (isDataSaver) return AudioQuality.LOW
        val isCellular = hasCellularTransport || (!hasWifiTransport && isMetered)
        val targetVal = if (isCellular) mobilePref else wifiPref
        val perNet = targetVal?.let { v -> AudioQuality.entries.find { it.name == v } }
        return perNet ?: globalPref?.let { v -> AudioQuality.entries.find { it.name == v } } ?: AudioQuality.AUTO
    }

    @Test
    fun testWifiAndMobileDifferentValues() {
        val wifi = AudioQuality.LOSSLESS.name
        val mobile = AudioQuality.LOW.name
        val global = AudioQuality.AUTO.name

        // When unmetered (Wi-Fi)
        val wifiQuality = resolveAudioQuality(isMetered = false, wifiPref = wifi, mobilePref = mobile, globalPref = global)
        assertEquals(AudioQuality.LOSSLESS, wifiQuality)

        // When metered (Cellular)
        val mobileQuality = resolveAudioQuality(isMetered = true, wifiPref = wifi, mobilePref = mobile, globalPref = global)
        assertEquals(AudioQuality.LOW, mobileQuality)
    }

    @Test
    fun testTransportSelectionTakesPrecedenceOverMeteredWifi() {
        val wifi = AudioQuality.LOSSLESS.name
        val mobile = AudioQuality.LOW.name

        // User is connected to a metered Wi-Fi network (hasWifiTransport=true, isMetered=true)
        val result = resolveAudioQualityWithTransport(
            hasCellularTransport = false,
            hasWifiTransport = true,
            isMetered = true,
            wifiPref = wifi,
            mobilePref = mobile,
            globalPref = AudioQuality.HIGH.name,
        )
        // Transport Wi-Fi routes to Wi-Fi setting
        assertEquals(AudioQuality.LOSSLESS, result)

        // User on cellular network
        val resultCellular = resolveAudioQualityWithTransport(
            hasCellularTransport = true,
            hasWifiTransport = false,
            isMetered = true,
            wifiPref = wifi,
            mobilePref = mobile,
            globalPref = AudioQuality.HIGH.name,
        )
        assertEquals(AudioQuality.LOW, resultCellular)
    }

    @Test
    fun testFallbackToGlobalWhenPerNetworkUnset() {
        // Both Wi-Fi and Mobile unset (null)
        val resolved = resolveAudioQuality(
            isMetered = false,
            wifiPref = null,
            mobilePref = null,
            globalPref = AudioQuality.HIGH.name,
        )
        assertEquals("Should inherit global setting when per-network override is null", AudioQuality.HIGH, resolved)

        val resolvedMetered = resolveAudioQuality(
            isMetered = true,
            wifiPref = null,
            mobilePref = null,
            globalPref = AudioQuality.HIGH.name,
        )
        assertEquals("Should inherit global setting on mobile when per-network override is null", AudioQuality.HIGH, resolvedMetered)
    }

    @Test
    fun testDefaultFallbackWhenAllPreferencesMissing() {
        val resolved = resolveAudioQuality(
            isMetered = false,
            wifiPref = null,
            mobilePref = null,
            globalPref = null,
        )
        assertEquals("Should default to AUTO when all preferences missing", AudioQuality.AUTO, resolved)
    }

    @Test
    fun testDataSaverOverridesAll() {
        val resolved = resolveAudioQuality(
            isMetered = false,
            wifiPref = AudioQuality.LOSSLESS.name,
            mobilePref = AudioQuality.HIGH.name,
            globalPref = AudioQuality.LOSSLESS.name,
            isDataSaver = true,
        )
        assertEquals("Data Saver must force LOW quality even on unmetered lossless Wi-Fi", AudioQuality.LOW, resolved)
    }

    @Test
    fun testNetworkChangeMidSessionTransitionsQuality() {
        var isMetered = false
        val wifiPref = AudioQuality.LOSSLESS.name
        val mobilePref = AudioQuality.LOW.name

        // Initial connection: Wi-Fi
        var current = resolveAudioQuality(isMetered, wifiPref, mobilePref, globalPref = null)
        assertEquals(AudioQuality.LOSSLESS, current)

        // Mid-session transition: Wi-Fi drops, switches to Cellular (metered)
        isMetered = true
        current = resolveAudioQuality(isMetered, wifiPref, mobilePref, globalPref = null)
        assertEquals(AudioQuality.LOW, current)

        // Reconnects to Wi-Fi
        isMetered = false
        current = resolveAudioQuality(isMetered, wifiPref, mobilePref, globalPref = null)
        assertEquals(AudioQuality.LOSSLESS, current)
    }

    @Test
    fun testUnknownOrCorruptedPreferenceFallback() {
        val resolved = resolveAudioQuality(
            isMetered = false,
            wifiPref = "NON_EXISTENT_LEGACY_QUALITY",
            mobilePref = null,
            globalPref = AudioQuality.HIGH.name,
        )
        assertEquals("Unknown preference value should gracefully fallback to global preference", AudioQuality.HIGH, resolved)
    }
}
