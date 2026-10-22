/*
 * Napster (C) 2026
 * Licensed under GPL-3.0
 */

package com.beatwave.music.playback.resolver.addon

import com.beatwave.music.constants.AudioQuality
import com.beatwave.music.playback.audio.OutputRouteState
import com.beatwave.music.utils.YTPlayerUtils
import com.music.innertube.models.response.PlayerResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import timber.log.Timber

@Serializable
data class AddonConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true,
    val downloadsAllowed: Boolean = true,
    val requiresLosslessOutput: Boolean = false,
)

sealed interface AddonHealth {
    data object Healthy : AddonHealth
    data class RateLimited(val secondsLeft: Long) : AddonHealth
    data class Unreachable(val message: String) : AddonHealth
    data class Error(val message: String) : AddonHealth
}

class AddonSource(
    val config: AddonConfig,
    val client: AddonClient = AddonClient(config.baseUrl),
) {
    private val _health = MutableStateFlow<AddonHealth>(AddonHealth.Healthy)
    val health: StateFlow<AddonHealth> = _health.asStateFlow()

    suspend fun resolve(
        target: TrackMatcher.Target,
        audioQuality: AudioQuality,
        allowLossless: Boolean,
        outputRouteState: OutputRouteState? = null,
    ): YTPlayerUtils.PlaybackData? {
        if (!config.enabled) return null

        // Check if lossless output is required by this add-on
        if (config.requiresLosslessOutput && outputRouteState != null && !outputRouteState.isCapableOfLosslessOrHighBitrate) {
            Timber.tag(TAG).d("Skipping add-on ${config.name}: requires lossless output sink")
            return null
        }

        val tier = when (audioQuality) {
            AudioQuality.LOSSLESS -> "LOSSLESS"
            AudioQuality.HIGH -> if (allowLossless) "LOSSLESS" else "HIGH"
            AudioQuality.LOW -> "LOW"
            AudioQuality.AUTO -> if (allowLossless) "LOSSLESS" else "HIGH"
        }

        val queries = TrackMatcher.queries(target)
        if (queries.isEmpty()) return null

        var bestMatch: Pair<AddonTrack, Int>? = null

        for (q in queries) {
            val searchRes = client.search(q, tier)
            if (searchRes.isFailure) {
                val ex = searchRes.exceptionOrNull()
                updateHealth(ex)
                continue
            }
            _health.value = AddonHealth.Healthy
            val tracks = searchRes.getOrNull().orEmpty()
            for (track in tracks) {
                val score = TrackMatcher.matchScore(
                    candidateTitle = track.title,
                    candidateArtist = track.artist,
                    candidateDurationSec = track.durationSec,
                    target = target,
                )
                if (score >= 0 && (bestMatch == null || score > bestMatch.second)) {
                    bestMatch = track to score
                }
            }
            if (bestMatch != null && bestMatch.second >= 120) {
                break // Excellent match found, stop querying
            }
        }

        val matchedTrack = bestMatch?.first ?: return null

        // If track provides direct streamURL, use it directly
        val directUrl = matchedTrack.streamURL
        val streamData: AddonStream = if (!directUrl.isNullOrBlank()) {
            AddonStream(
                url = directUrl,
                format = matchedTrack.format,
                audioQuality = matchedTrack.audioQuality,
            )
        } else {
            val streamRes = client.stream(matchedTrack.id, tier)
            if (streamRes.isFailure) {
                updateHealth(streamRes.exceptionOrNull())
                return null
            }
            _health.value = AddonHealth.Healthy
            streamRes.getOrNull() ?: return null
        }

        if (streamData.url.isBlank() || streamData.isEncrypted) {
            Timber.tag(TAG).d("Rejecting add-on stream: blank URL or encrypted DRM stream")
            return null
        }

        val mimeType = streamData.mimeType?.takeIf { it.isNotBlank() }
            ?: when {
                streamData.statedContainer == "flac" || streamData.format.contains("flac", ignoreCase = true) -> "audio/flac"
                streamData.statedContainer == "m4a" || streamData.statedContainer == "mp4" -> "audio/mp4"
                streamData.statedContainer == "webm" || streamData.statedContainer == "ogg" -> "audio/webm"
                else -> "audio/flac"
            }

        val isFlac = mimeType.contains("flac", ignoreCase = true) || streamData.statedCodec == "flac"
        val isLossless = isFlac || streamData.qualityText.contains("lossless", ignoreCase = true)

        val streamingFormat = PlayerResponse.StreamingData.Format(
            itag = if (isFlac) 999 else 140,
            url = streamData.url,
            mimeType = mimeType,
            bitrate = (streamData.kbps ?: if (isFlac) 1411 else 256) * 1000,
            width = null,
            height = null,
            contentLength = null,
            quality = if (isLossless) "LOSSLESS" else "HIGH",
            fps = null,
            qualityLabel = null,
            averageBitrate = null,
            audioQuality = streamData.quality.ifBlank { if (isLossless) "LOSSLESS" else "HIGH" },
            approxDurationMs = ((matchedTrack.durationSec ?: target.durationSec ?: 0) * 1000L).toString(),
            audioSampleRate = streamData.sampleRateHz ?: if (isFlac) 44100 else 48000,
            audioChannels = 2,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
            cipher = null,
            audioTrack = null,
        )

        return YTPlayerUtils.PlaybackData(
            audioConfig = null,
            videoDetails = null,
            playbackTracking = null,
            format = streamingFormat,
            streamUrl = streamData.url,
            streamExpiresInSeconds = 3600,
            isTidalStream = isLossless && streamData.qualityText.contains("tidal", ignoreCase = true),
            isSpineStream = false,
            isAddonStream = true,
            addonName = config.name,
        )
    }

    private fun updateHealth(throwable: Throwable?) {
        if (throwable == null) return
        val now = System.currentTimeMillis()
        val cooldown = client.quietUntilMs - now
        _health.value = when {
            cooldown > 0 -> AddonHealth.RateLimited(cooldown / 1000)
            throwable is AddonUnavailable -> AddonHealth.Unreachable(throwable.message ?: "Unavailable")
            else -> AddonHealth.Error(throwable.message ?: "Error")
        }
    }

    companion object {
        private const val TAG = "AddonSource"
    }
}
