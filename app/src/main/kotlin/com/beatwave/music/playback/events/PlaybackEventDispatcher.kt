/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback.events

import android.content.Context
import com.beatwave.music.constants.DataSaverEnabledKey
import com.beatwave.music.constants.EnableLastFMScrobblingKey
import com.beatwave.music.constants.LastFMUseNowPlaying
import com.beatwave.music.constants.ListenBrainzEnabledKey
import com.beatwave.music.constants.ListenBrainzTokenKey
import com.beatwave.music.constants.ScrobbleDelayPercentKey
import com.beatwave.music.constants.ScrobbleDelaySecondsKey
import com.beatwave.music.constants.ScrobbleMinSongDurationKey
import com.beatwave.music.integration.listenbrainz.ListenBrainzManager
import com.beatwave.music.integration.lastfm.ScrobbleManager
import com.beatwave.music.utils.dataStore
import com.beatwave.music.utils.get
import com.music.lastfm.LastFM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Small boundary for optional services that observe playback.
 * The playback service owns event timing; implementations own provider behavior.
 */
interface PlaybackEventDispatcher {
    fun start()
    fun onTrackChanged(
        mediaId: String?,
        metadata: com.beatwave.music.models.MediaMetadata?,
        durationMs: Long?,
        isReadyToPlay: Boolean,
    )
    fun onPlaybackStateChanged(
        isPlaying: Boolean,
        mediaId: String?,
        metadata: com.beatwave.music.models.MediaMetadata?,
        durationMs: Long?,
    )
    fun onPlaybackStopped()
    fun destroy()
}

class OptionalPlaybackEventDispatcher(
    private val context: Context,
    private val scope: CoroutineScope,
) : PlaybackEventDispatcher {
    private var scrobbleManager: ScrobbleManager? = null
    private var listenBrainzEnabled = false
    private var listenBrainzToken = ""
    private var listenBrainzCurrentMediaId: String? = null
    private var listenBrainzCurrentStartTs: Long = 0L

    override fun start() {
        val dataStore = context.dataStore

        dataStore.data
            .map { it[EnableLastFMScrobblingKey] ?: false }
            .debounce(300)
            .distinctUntilChanged()
            .collectInScope { enabled ->
                if (enabled && scrobbleManager == null) {
                    scrobbleManager = ScrobbleManager(
                        scope,
                        minSongDuration = dataStore.get(ScrobbleMinSongDurationKey, LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION),
                        scrobbleDelayPercent = dataStore.get(ScrobbleDelayPercentKey, LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT),
                        scrobbleDelaySeconds = dataStore.get(ScrobbleDelaySecondsKey, LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS),
                    ).also {
                        it.useNowPlaying = dataStore.get(LastFMUseNowPlaying, false)
                    }
                } else if (!enabled) {
                    scrobbleManager?.destroy()
                    scrobbleManager = null
                }
            }

        dataStore.data
            .map { it[LastFMUseNowPlaying] ?: false }
            .distinctUntilChanged()
            .collectInScope { scrobbleManager?.useNowPlaying = it }

        dataStore.data
            .map { prefs ->
                val enabled = prefs[ListenBrainzEnabledKey] ?: false
                if (prefs[DataSaverEnabledKey] == true) false else enabled
            }
            .distinctUntilChanged()
            .collectInScope { listenBrainzEnabled = it }

        dataStore.data
            .map { it[ListenBrainzTokenKey] ?: "" }
            .distinctUntilChanged()
            .collectInScope { listenBrainzToken = it }

        dataStore.data
            .map { prefs ->
                Triple(
                    prefs[ScrobbleDelayPercentKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT,
                    prefs[ScrobbleMinSongDurationKey] ?: LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION,
                    prefs[ScrobbleDelaySecondsKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS,
                )
            }
            .distinctUntilChanged()
            .collectInScope { (delayPercent, minDuration, delaySeconds) ->
                scrobbleManager?.apply {
                    scrobbleDelayPercent = delayPercent
                    minSongDuration = minDuration
                    scrobbleDelaySeconds = delaySeconds
                }
            }
    }

    override fun onTrackChanged(
        mediaId: String?,
        metadata: com.beatwave.music.models.MediaMetadata?,
        durationMs: Long?,
        isReadyToPlay: Boolean,
    ) {
        metadata?.let { lastMetadataByMediaId[mediaId.orEmpty()] = it }
        scrobbleManager?.onSongStop()
        submitListenBrainzFinished()

        if (isReadyToPlay && metadata != null) {
            scrobbleManager?.onSongStart(metadata, duration = durationMs)
        }

        if (isReadyToPlay && mediaId != null) {
            beginListenBrainzTrack(mediaId)
            submitListenBrainzPlayingNow(metadata, durationMs)
        }
    }

    override fun onPlaybackStateChanged(
        isPlaying: Boolean,
        mediaId: String?,
        metadata: com.beatwave.music.models.MediaMetadata?,
        durationMs: Long?,
    ) {
        metadata?.let { lastMetadataByMediaId[mediaId.orEmpty()] = it }
        scrobbleManager?.onPlayerStateChanged(isPlaying, metadata, duration = durationMs)

        if (isPlaying && mediaId != null) {
            if (listenBrainzCurrentMediaId != mediaId) {
                submitListenBrainzFinished()
                beginListenBrainzTrack(mediaId)
            }
            submitListenBrainzPlayingNow(metadata, durationMs)
        }
    }

    override fun onPlaybackStopped() {
        scrobbleManager?.onSongStop()
        submitListenBrainzFinished()
    }

    override fun destroy() {
        scrobbleManager?.destroy()
        scrobbleManager = null
        listenBrainzCurrentMediaId = null
        listenBrainzCurrentStartTs = 0L
        lastMetadataByMediaId.clear()
    }

    private fun beginListenBrainzTrack(mediaId: String) {
        if (listenBrainzCurrentMediaId != mediaId) {
            listenBrainzCurrentMediaId = mediaId
            listenBrainzCurrentStartTs = System.currentTimeMillis()
        }
    }

    private fun submitListenBrainzPlayingNow(
        metadata: com.beatwave.music.models.MediaMetadata?,
        durationMs: Long?,
    ) {
        val token = listenBrainzToken.trim()
        if (!listenBrainzEnabled || token.isBlank() || metadata == null) return
        scope.launch {
            try {
                ListenBrainzManager.submitPlayingNow(
                    context = context,
                    token = token,
                    title = metadata.title,
                    artistNames = metadata.artists.joinToString(" & ") { it.name },
                    releaseName = metadata.album?.title.orEmpty(),
                    durationMs = durationMs ?: metadata.duration.takeIf { it != -1 }?.times(1000L) ?: 0L,
                    positionMs = 0L,
                )
            } catch (error: Throwable) {
                Timber.w(error, "ListenBrainz playing-now event failed")
            }
        }
    }

    private fun submitListenBrainzFinished() {
        val mediaId = listenBrainzCurrentMediaId ?: return
        val startTs = listenBrainzCurrentStartTs
        val token = listenBrainzToken.trim()
        listenBrainzCurrentMediaId = null
        listenBrainzCurrentStartTs = 0L
        if (!listenBrainzEnabled || token.isBlank() || startTs <= 0L) return

        // Metadata is supplied by the playback callback for normal tracks. The
        // previous implementation also skipped submission when it could not
        // recover metadata, so an absent callback metadata remains harmless.
        val metadata = lastMetadataByMediaId.remove(mediaId) ?: return
        scope.launch {
            try {
                ListenBrainzManager.submitFinished(
                    context = context,
                    token = token,
                    title = metadata.title,
                    artistNames = metadata.artists.joinToString(" & ") { it.name },
                    releaseName = metadata.album?.title.orEmpty(),
                    durationMs = metadata.duration.takeIf { it != -1 }?.times(1000L) ?: 0L,
                    startMs = startTs,
                    endMs = System.currentTimeMillis(),
                )
            } catch (error: Throwable) {
                Timber.w(error, "ListenBrainz finished event failed")
            }
        }
    }

    private val lastMetadataByMediaId = mutableMapOf<String, com.beatwave.music.models.MediaMetadata>()

    private fun <T> kotlinx.coroutines.flow.Flow<T>.collectInScope(action: suspend (T) -> Unit): Job =
        scope.launch { collect { action(it) } }
}
