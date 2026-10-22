package com.beatwave.music.playback.audio

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDiagnosticsTest {

    @Test
    fun testNotDowngradedWhenClaimMatchesMeasurement() {
        val diag = AudioDiagnostics(
            providerClaim = ProviderClaim(
                sourceName = "Spine",
                claimedFormat = "audio/flac",
                claimedSampleRateHz = 44100,
                claimedBitDepth = 16,
                isLosslessClaim = true,
            ),
            inputFormat = InputFormat(
                mimeType = "audio/flac",
                sampleRateHz = 44100,
                bitDepth = 16,
            )
        )
        assertFalse(diag.isDowngraded)
        assertNull(diag.downgradeReason)
    }

    @Test
    fun testDowngradedWhenClaimLosslessButStreamLossy() {
        val diag = AudioDiagnostics(
            providerClaim = ProviderClaim(
                sourceName = "Tidal",
                isLosslessClaim = true,
            ),
            inputFormat = InputFormat(
                mimeType = "audio/mp4a-latm",
                codecs = "mp4a.40.2",
                sampleRateHz = 44100,
            )
        )
        assertTrue(diag.isDowngraded)
        assertTrue(diag.downgradeReason?.contains("lossless", ignoreCase = true) == true)
    }

    @Test
    fun testDowngradedWhenSampleRateReduced() {
        val diag = AudioDiagnostics(
            providerClaim = ProviderClaim(
                claimedSampleRateHz = 96000,
            ),
            inputFormat = InputFormat(
                mimeType = "audio/flac",
                sampleRateHz = 48000,
            )
        )
        assertTrue(diag.isDowngraded)
        assertTrue(diag.downgradeReason?.contains("96000Hz to 48000Hz") == true)
    }

    @Test
    fun testPcmFloatCarrierDoesNotAssume32Bit() {
        val depth = AudioFormatMeasurer.bitDepthOf(C.ENCODING_PCM_FLOAT)
        assertNull("PCM_FLOAT must never be assumed to be 32-bit audio master", depth)
        assertEquals(16, AudioFormatMeasurer.bitDepthOf(C.ENCODING_PCM_16BIT))
        assertEquals(24, AudioFormatMeasurer.bitDepthOf(C.ENCODING_PCM_24BIT))
        assertEquals(32, AudioFormatMeasurer.bitDepthOf(C.ENCODING_PCM_32BIT))
    }

    @Test
    fun testBadgeLabelFormatting() {
        val input = InputFormat(
            mimeType = "audio/flac",
            bitDepth = 24,
            sampleRateHz = 96000,
            bitrateKbps = 1500,
        )
        val diag = AudioDiagnostics(inputFormat = input)
        assertTrue(diag.badgeLabel.contains("FLAC"))
        assertTrue(diag.badgeLabel.contains("24-bit"))
        assertTrue(diag.badgeLabel.contains("96.0kHz"))
    }
}
