/*
 * Napster (C) 2026
 * Licensed under GPL-3.0
 */

package com.beatwave.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

/**
 * Architectural layer distinguishing provider claim, container/input format,
 * decoder output PCM, and active output route.
 */
data class AudioDiagnostics(
    val providerClaim: ProviderClaim = ProviderClaim(),
    val inputFormat: InputFormat = InputFormat(),
    val decoderPcm: DecoderPcm = DecoderPcm(),
    val outputRoute: OutputRouteState = OutputRouteState(),
) {
    /**
     * Whether what Media3 arrived at is measurably inferior to what the provider promised.
     * Only true when both claim and measurement are established.
     */
    val isDowngraded: Boolean
        get() {
            if (providerClaim.isLosslessClaim && !inputFormat.isLossless && inputFormat.mimeType != null) {
                return true
            }
            val claimedRate = providerClaim.claimedSampleRateHz
            val actualRate = inputFormat.sampleRateHz
            if (claimedRate != null && actualRate != null && actualRate < claimedRate) {
                return true
            }
            val claimedDepth = providerClaim.claimedBitDepth
            val actualDepth = inputFormat.bitDepth
            if (claimedDepth != null && actualDepth != null && actualDepth < claimedDepth) {
                return true
            }
            return false
        }

    val downgradeReason: String?
        get() {
            if (!isDowngraded) return null
            if (providerClaim.isLosslessClaim && !inputFormat.isLossless) {
                val got = inputFormat.codecLabel ?: inputFormat.mimeType ?: "Lossy stream"
                return "Claimed lossless source, but player observed $got"
            }
            val claimedRate = providerClaim.claimedSampleRateHz
            val actualRate = inputFormat.sampleRateHz
            if (claimedRate != null && actualRate != null && actualRate < claimedRate) {
                return "Sample rate reduced from ${claimedRate}Hz to ${actualRate}Hz"
            }
            val claimedDepth = providerClaim.claimedBitDepth
            val actualDepth = inputFormat.bitDepth
            if (claimedDepth != null && actualDepth != null && actualDepth < claimedDepth) {
                return "Bit depth reduced from ${claimedDepth}-bit to ${actualDepth}-bit"
            }
            return null
        }

    val badgeLabel: String
        get() {
            val codec = inputFormat.codecLabel ?: "AUTO"
            val depth = inputFormat.bitDepth?.let { "$it-bit" }
            val rate = inputFormat.sampleRateHz?.let {
                if (it >= 1000) "${it / 1000}.${(it % 1000) / 100}kHz" else "${it}Hz"
            }
            val kbps = inputFormat.bitrateKbps?.let { "${it}kbps" }

            return buildString {
                append(codec)
                if (depth != null) append(" · $depth")
                if (rate != null) append(" · $rate")
                else if (kbps != null) append(" · $kbps")
            }
        }
}

data class ProviderClaim(
    val sourceName: String? = null,
    val claimedFormat: String? = null,
    val claimedBitrateKbps: Int? = null,
    val claimedSampleRateHz: Int? = null,
    val claimedBitDepth: Int? = null,
    val isLosslessClaim: Boolean = false,
)

data class InputFormat(
    val mimeType: String? = null,
    val codecs: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val bitrateKbps: Int? = null,
    val container: String? = null,
) {
    val isLossless: Boolean
        get() = isLosslessMime(mimeType)

    val isHiRes: Boolean
        get() = isLossless && ((bitDepth ?: 0) > 16 || (sampleRateHz ?: 0) > 48000)

    val codecLabel: String?
        get() {
            val mime = mimeType?.lowercase() ?: ""
            val c = codecs?.lowercase() ?: ""
            return when {
                mime.contains("flac") || c.contains("flac") -> "FLAC"
                mime.contains("alac") || c.contains("alac") -> "ALAC"
                mime.contains("wav") || mime.contains("wave") -> "WAV"
                mime.contains("opus") || c.contains("opus") -> "Opus"
                mime.contains("mp4a") || mime.contains("aac") || c.contains("mp4a") || c.contains("aac") -> "AAC"
                mime.contains("mp3") || mime.contains("mpeg") || c.contains("mp3") || c.contains("mpeg") -> "MP3"
                mime.contains("vorbis") || c.contains("vorbis") -> "Vorbis"
                mime.contains("webm") || c.contains("webm") -> "WebM"
                else -> mime.substringAfterLast('/').uppercase().takeIf { it.isNotBlank() }
            }
        }

    companion object {
        fun isLosslessMime(mime: String?): Boolean {
            if (mime == null) return false
            val m = mime.lowercase()
            return m.contains("flac") || m.contains("alac") || m.contains("wav") ||
                    m == MimeTypes.AUDIO_RAW
        }
    }
}

data class DecoderPcm(
    val decoderName: String? = null,
    val pcmEncoding: Int? = null,
    val bitDepth: Int? = null,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
    val isSystemResampled: Boolean = false,
)

/**
 * Measures actual container format parameters, parsing FLAC STREAMINFO when available
 * and refusing to infer 32-bit audio from PCM_FLOAT.
 */
object AudioFormatMeasurer {

    private const val STREAM_INFO_BYTES = 34

    fun measure(format: Format, pcmEncoding: Int = C.ENCODING_INVALID): InputFormat {
        val streamInfo = parseFlacStreamInfo(format)

        val rendererDepth = if (pcmEncoding == C.ENCODING_PCM_FLOAT) {
            null // PCM_FLOAT is a carrier; never claim 32-bit master without source proof
        } else {
            bitDepthOf(pcmEncoding)
        }

        val effectiveSampleRate = format.sampleRate.takeIf { it > 0 } ?: streamInfo?.sampleRateHz
        val effectiveChannels = format.channelCount.takeIf { it > 0 } ?: streamInfo?.channels
        val effectiveBitDepth = rendererDepth ?: streamInfo?.bitDepth

        val bitrateKbps = format.bitrate.takeIf { it > 0 }?.let { it / 1000 }
            ?: format.averageBitrate.takeIf { it > 0 }?.let { it / 1000 }
            ?: if (format.sampleMimeType?.contains("flac", ignoreCase = true) == true &&
                effectiveSampleRate != null && effectiveBitDepth != null && effectiveChannels != null
            ) {
                // Approximate PCM decoded bitrate
                (effectiveSampleRate.toLong() * effectiveBitDepth * effectiveChannels / 1000).toInt()
            } else null

        return InputFormat(
            mimeType = format.sampleMimeType,
            codecs = format.codecs,
            sampleRateHz = effectiveSampleRate,
            bitDepth = effectiveBitDepth,
            channels = effectiveChannels,
            bitrateKbps = bitrateKbps,
            container = format.containerMimeType,
        )
    }

    fun bitDepthOf(pcmEncoding: Int): Int? = when (pcmEncoding) {
        C.ENCODING_PCM_16BIT -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT -> 32
        else -> null
    }

    private class FlacStreamInfo(val sampleRateHz: Int, val channels: Int, val bitDepth: Int)

    private fun parseFlacStreamInfo(format: Format): FlacStreamInfo? {
        if (format.sampleMimeType != MimeTypes.AUDIO_FLAC) return null
        val data = format.initializationData.firstOrNull() ?: return null
        fun byteAt(i: Int) = data[i].toInt() and 0xFF
        fun headerAt(i: Int) = data.size > i + 3 &&
                (byteAt(i) and 0x7F) == 0 &&
                byteAt(i + 1) == 0 && byteAt(i + 2) == 0 && byteAt(i + 3) == STREAM_INFO_BYTES

        val body = when {
            data.size >= 4 && String(data, 0, 4, Charsets.US_ASCII) == "fLaC" ->
                if (headerAt(4)) 8 else 4
            headerAt(0) -> 4
            else -> 0
        }
        if (data.size < body + STREAM_INFO_BYTES) return null
        fun at(i: Int) = byteAt(body + i)

        // STREAMINFO layout: 10 bytes min/max block/frame sizes, then 20-bit sample rate,
        // 3-bit channel count (minus 1), 5-bit sample depth (minus 1)
        val sampleRate = (at(10) shl 12) or (at(11) shl 4) or (at(12) shr 4)
        val channels = ((at(12) shr 1) and 0x07) + 1
        val bitDepth = (((at(12) and 0x01) shl 4) or (at(13) shr 4)) + 1
        return FlacStreamInfo(sampleRate, channels, bitDepth).takeIf { sampleRate > 0 }
    }
}
