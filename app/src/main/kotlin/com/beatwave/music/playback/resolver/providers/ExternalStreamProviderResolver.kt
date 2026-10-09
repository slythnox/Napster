/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback.resolver.providers

import android.content.Context
import com.beatwave.music.constants.AudioQuality
import com.beatwave.music.constants.EnableSaavnStreamingKey
import com.beatwave.music.constants.ForceSelectedQualityKey
import com.beatwave.music.constants.SaavnFallbackToYouTubeKey
import com.beatwave.music.constants.EnableTidalStreamingKey
import com.beatwave.music.constants.EnabledModulesKey
import com.beatwave.music.constants.ModuleSourcesKey
import com.beatwave.music.constants.ModuleSettingsKey
import com.beatwave.music.constants.SaavnAudioQuality
import com.beatwave.music.constants.SaavnAudioQualityKey
import com.beatwave.music.constants.TidalInstanceUrlKey
import com.beatwave.music.constants.TidalQuality
import com.beatwave.music.constants.TidalQualityKey
import com.beatwave.music.integration.tidal.TidalService
import com.beatwave.music.integration.tidal.TidalTrack
import com.beatwave.music.utils.YTPlayerUtils
import com.beatwave.music.utils.dataStore
import com.beatwave.music.utils.get
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.response.PlayerResponse
import com.music.jiosaavn.SaavnService
import com.music.spine.ModuleManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import timber.log.Timber
import java.io.IOException

private data class ExternalYtMetadata(
    val currentSong: SongItem?,
    val meta: PlayerResponse?,
    val title: String,
    val artistNames: List<String>,
)

private fun tidalQualityRank(audioQuality: String?): Int = when (audioQuality?.uppercase(java.util.Locale.US)) {
    "HI_RES_LOSSLESS", "HI_RES" -> 3
    "LOSSLESS" -> 2
    "HIGH" -> 1
    else -> 0
}

/** Resolves optional external streams before the normal YouTube path. */
object ExternalStreamProviderResolver {
    private const val TAG = "ExternalStreamProviderResolver"
    private const val logTag = "ExternalStreamProviderResolver"

    suspend fun resolve(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        context: Context?,
        allowLossless: Boolean,
        forceStandardAudio: Boolean,
        metadataResolver: suspend (String, String?) -> Result<PlayerResponse?>,
    ): Result<YTPlayerUtils.PlaybackData>? {
        // â”€â”€ JioSaavn intercept â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // If the user has enabled JioSaavn streaming, try to resolve the stream
        // URL from JioSaavn first. We fall through to YouTube on ANY failure so
        // the user always hears audio.
        if (context != null) {
            // Circuit breaker: skips both the lossless-capable Spine modules and
            // Tidal below, so a user who picked a specific quality actually gets
            // it instead of a module/Tidal silently upgrading to FLAC.
            val forceSelectedQuality = context.dataStore.get(ForceSelectedQualityKey, false)

            var cachedYtMetadata: ExternalYtMetadata? = null

            suspend fun getYtMetadata(): ExternalYtMetadata {
                cachedYtMetadata?.let { return it }
                val (currentSong, meta) = coroutineScope {
                    val nextDeferred = async {
                        val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()
                        (nextResult?.items?.getOrNull(nextResult.currentIndex ?: 0)
                            ?: nextResult?.items?.firstOrNull()) as? SongItem
                    }
                    val metaDeferred = async { metadataResolver(videoId, playlistId).getOrNull() }
                    nextDeferred.await() to metaDeferred.await()
                }

                val title = currentSong?.title ?: meta?.videoDetails?.title.orEmpty()
                val artistNames: List<String> = if (currentSong?.artists?.isNotEmpty() == true) {
                    currentSong.artists.map { it.name }
                } else {
                    listOf(
                        meta?.videoDetails?.author.orEmpty()
                            .replace(Regex("(?i)\\s*-\\s*topic\\b"), "")
                            .replace(Regex("(?i)\\s*VEVO\\b"), "")
                            .trim()
                    ).filter { it.isNotBlank() }
                }
                return ExternalYtMetadata(currentSong, meta, title, artistNames).also {
                    cachedYtMetadata = it
                }
            }

            // â”€â”€ 8spine module intercept â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            // Try enabled 8spine modules for streaming before other sources.
            // Falls through to TIDAL/Saavn/YouTube on ANY failure.
            if (!forceStandardAudio) {
            Timber.tag(TAG).d("â•â•â• SPINE INTERCEPT START â•â•â• videoId=$videoId")
            val enabledModulesJson = context.dataStore.get(EnabledModulesKey, "[]")
            val moduleSourcesJson = context.dataStore.get(ModuleSourcesKey, "[]")
            val moduleSettingsJson = context.dataStore.get(ModuleSettingsKey, "{}")
            Timber.tag(TAG).d("  enabledModules: $enabledModulesJson")
            Timber.tag(TAG).d("  moduleSources: $moduleSourcesJson")
            Timber.tag(TAG).d("  moduleSettings: $moduleSettingsJson")
            runCatching {
                // Stored in the user's chosen priority order (drag-reorder in
                // ModuleSourceScreen), lowest index tried first.
                val enabledOrder = runCatching {
                    val arr = JSONArray(enabledModulesJson)
                    (0 until arr.length()).map { arr.getString(it) }
                }.getOrElse { emptyList<String>() }
                val enabledIds = enabledOrder.toSet()

                val sourceUrls = runCatching {
                    val arr = JSONArray(moduleSourcesJson)
                    (0 until arr.length()).map { arr.getString(it) }
                }.getOrElse { emptyList<String>() }

                val allModuleSettings = runCatching {
                    val obj = org.json.JSONObject(moduleSettingsJson)
                    obj.keys().asSequence().associateWith { key ->
                        val inner = obj.optJSONObject(key)
                        if (inner != null) {
                            inner.keys().asSequence().associateWith { k -> inner.optString(k, "") }
                        } else emptyMap()
                    }
                }.getOrElse { emptyMap<String, Map<String, String>>() }

                Timber.tag(TAG).d("  Parsed enabledIds: $enabledIds")
                Timber.tag(TAG).d("  Parsed sourceUrls: $sourceUrls")
                Timber.tag(TAG).d("  Parsed allModuleSettings: $allModuleSettings")

                if (enabledIds.isEmpty() || sourceUrls.isEmpty()) {
                    Timber.tag(TAG).d("  SPINE SKIP: enabledIds=${enabledIds.size} sourceUrls=${sourceUrls.size} â€” falling through")
                    Timber.tag(TAG).d("â•â•â• SPINE INTERCEPT END (skipped) â•â•â•")
                }

                if (enabledIds.isNotEmpty() && sourceUrls.isNotEmpty()) {
                    Timber.tag(TAG).d("  Resolving YouTube metadata for search query...")

                    val (currentSong, meta, title, artistNames) = getYtMetadata()
                    Timber.tag(TAG).d("  Resolved title=\"$title\" from ${if (currentSong != null) "YouTube.next" else "videoDetails"}")
                    if (title.isNotBlank()) {
                        var cleanTitle = title
                        for (artist in artistNames) {
                            cleanTitle = cleanTitle.replace(Regex("(?i)^\\s*${Regex.escape(artist)}\\s*[-â€“â€”]\\s*"), "")
                        }
                        // Strip YouTube noise that confuses search queries
                        cleanTitle = cleanTitle
                            .replace(Regex("(?i)\\(\\s*official\\s*(video|music\\s*video|audio)?\\s*\\)"), "")
                            .replace(Regex("(?i)\\(\\s*lyrics?\\s*\\)"), "")
                            .replace(Regex("(?i)\\[.*?\\]"), "")
                            .replace(Regex("[\u266A\u266B]"), "")
                            .replace(Regex("\\s+"), " ").trim()
                        val query = "$cleanTitle ${artistNames.joinToString(" ")}"
                            .replace("&", " ").replace(",", " ")
                            .replace(Regex("\\s+"), " ").trim()

                        Timber.tag(TAG).d("  Search query: \"$query\" (artists=$artistNames)")

                        val moduleManager = ModuleManager()
                        for ((sourceIdx, sourceUrl) in sourceUrls.withIndex()) {
                            Timber.tag(TAG).d("  â”€â”€ Source [${sourceIdx + 1}/${sourceUrls.size}]: $sourceUrl")
                            val modules = moduleManager.fetchIndex(sourceUrl).getOrElse { e ->
                                Timber.tag(TAG).e("  âœ— Failed to fetch index from $sourceUrl: ${e.message}")
                                continue
                            }
                            Timber.tag(TAG).d("  âœ“ Fetched ${modules.size} modules from $sourceUrl")
                            // Try in the user's priority order, not the index's own order â€”
                            // enabledOrder.indexOf is -1 (sorts first) for anything not enabled,
                            // but those are skipped by the `!in enabledIds` check below anyway.
                            val orderedModules = modules.sortedBy { enabledOrder.indexOf(it.id) }
                            for ((modIdx, module) in orderedModules.withIndex()) {
                                if (module.id !in enabledIds) {
                                    Timber.tag(TAG).d("    [${modIdx + 1}] SKIP ${module.id} (not enabled)")
                                    continue
                                }
                                Timber.tag(TAG).d("    [${modIdx + 1}/${modules.size}] TRY ${module.id} â€” isLossless=${module.isLossless} hasHiRes=${module.hasHiRes} isAtmos=${module.isDolbyAtmos}")
                                val spineResult = runCatching {
                                    val loadBaseUrl = sourceUrl.substringBeforeLast("/")
                                    Timber.tag(TAG).d("      Loading module JS from baseUrl=$loadBaseUrl")
                                    val loaded = moduleManager.loadModule(module) { loadBaseUrl }.getOrElse { e ->
                                        Timber.tag(TAG).e("      âœ— Failed to load module ${module.id}: ${e.message}")
                                        return@runCatching null
                                    }

                                    val moduleSettings = allModuleSettings[module.id] ?: emptyMap()
                                    Timber.tag(TAG).d("      Module settings: $moduleSettings")
                                    Timber.tag(TAG).d("      Calling searchTracks(\"$query\", 5, settings)...")
                                    var searchResult = moduleManager.searchTracks(loaded, query, 5, moduleSettings).getOrElse { e ->
                                        Timber.tag(TAG).e("      âœ— Search failed for module ${module.id}: ${e.message}")
                                        return@runCatching null
                                    }
                                    // Retry with artist-first query if no matches (helps GEOLIER2/Apple fallback)
                                    if (searchResult.tracks.isEmpty() && artistNames.isNotEmpty()) {
                                        val retryQuery = "${artistNames.first()} $cleanTitle"
                                            .replace("&", " ").replace(",", " ")
                                            .replace(Regex("\\s+"), " ").trim()
                                        Timber.tag(TAG).d("      No results, retrying with artist-first: \"$retryQuery\"")
                                        searchResult = moduleManager.searchTracks(loaded, retryQuery, 5, moduleSettings).getOrElse { e ->
                                            Timber.tag(TAG).e("      âœ— Retry search failed: ${e.message}")
                                            return@runCatching null
                                        }
                                    }
                                    // minByOrNull on its own always returns something â€” even a
                                    // track that matched nothing scores 0 and wins when it is the
                                    // only result. That is how a completely different song ends up
                                    // playing. Require a real title hit and a plausible runtime first.
                                    val matchedTrack = searchResult.tracks.filter { track ->
                                        val t = track.title.lowercase().trim()
                                        val q = cleanTitle.lowercase().trim()
                                        val songDur = currentSong?.duration
                                        val durationOk = if (songDur != null && track.duration != null) {
                                            Math.abs(track.duration - songDur) <= 10
                                        } else {
                                            // No runtime to check against â€” require an exact
                                            // title match instead of skipping the guard.
                                            t == q
                                        }
                                        (q in t || t in q) && durationOk
                                    }.minByOrNull { track ->
                                        val t = track.title.lowercase().trim()
                                        val a = track.artist.lowercase().trim()
                                        val q = cleanTitle.lowercase().trim()
                                        val artistsLower = artistNames.map { it.lowercase().trim() }
                                        var score = 0
                                        if (q in t || t in q) score -= 10
                                        if (artistsLower.any { it in a || a in it }) score -= 5
                                        val trackDur = track.duration
                                        val songDur = currentSong?.duration
                                        if (trackDur != null && songDur != null) {
                                            val diff = Math.abs(trackDur - songDur)
                                            if (diff < 5) score -= 3
                                        }
                                        score
                                    }
                                    if (matchedTrack == null) {
                                        Timber.tag(TAG).d("      âœ— No tracks found by module ${module.id}")
                                        return@runCatching null
                                    }

                                    Timber.tag(TAG).d("      âœ“ Match: \"${matchedTrack.title}\" by \"${matchedTrack.artist}\" id=${matchedTrack.id} quality=${matchedTrack.audioQuality} duration=${matchedTrack.duration}s")

                                    Timber.tag(TAG).d("      Calling getStreamUrl(\"${matchedTrack.id}\", settings)...")
                                    val streamResult = moduleManager.getStreamUrl(loaded, matchedTrack.id, moduleSettings).getOrElse { e ->
                                        Timber.tag(TAG).e("      âœ— Stream URL fetch failed for module ${module.id} track ${matchedTrack.id}: ${e.message}")
                                        return@runCatching null
                                    }
                                    val streamUrl = streamResult.streamUrl?.ifBlank { null }
                                    if (streamUrl == null) {
                                        Timber.tag(TAG).d("      âœ— Empty stream URL from module ${module.id}")
                                        Timber.tag(TAG).d("      Full streamResult: streamUrl=${streamResult.streamUrl} track=${streamResult.track}")
                                        return@runCatching null
                                    }

                                    Timber.tag(TAG).d("      âœ“ Stream URL: ${streamUrl.take(150)}...")
                                    Timber.tag(TAG).d("      Track audioQuality: ${streamResult.track?.audioQuality}")
                                    Timber.tag(TAG).d("      Track mimeType: ${streamResult.track?.mimeType}")
                                    Timber.tag(TAG).d("      Track audioModes: ${streamResult.track?.audioModes}")
                                    Timber.tag(TAG).d("      Track bitDepth: ${streamResult.track?.bitDepth}")
                                    Timber.tag(TAG).d("      Track sampleRate: ${streamResult.track?.sampleRate}")

                                    val isAtmosSupported = run {
                                        val list = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
                                        list.codecInfos.any { !it.isEncoder && it.supportedTypes.any { t -> t.contains("eac3", ignoreCase = true) } }
                                    }

                                    val isAtmos = !forceSelectedQuality && isAtmosSupported && (module.isDolbyAtmos ||
                                        streamResult.track?.audioQuality?.uppercase()?.contains("ATMOS") == true ||
                                        streamResult.track?.audioModes?.any { it.uppercase().contains("ATMOS") } == true ||
                                        streamResult.track?.mimeType?.uppercase()?.contains("EAC3") == true)

                                    if (!isAtmosSupported && (streamResult.track?.audioQuality?.uppercase()?.contains("ATMOS") == true)) {
                                        Timber.tag(TAG).w("      ! Device does not support EAC3/Atmos - will attempt fallback to FLAC/High")
                                    }

                                    val isLossless = !isAtmos && !forceSelectedQuality && (
                                        module.isLossless ||
                                        streamResult.track?.audioQuality?.uppercase()?.contains("LOSSLESS") == true ||
                                        streamResult.track?.audioQuality?.uppercase()?.contains("HIRES") == true ||
                                        streamResult.track?.audioQuality?.uppercase()?.contains("FLAC") == true
                                    )

                                    Timber.tag(TAG).d("      isAtmos=$isAtmos (module.isDolbyAtmos=${module.isDolbyAtmos} audioQuality=${streamResult.track?.audioQuality} audioModes=${streamResult.track?.audioModes} mimeType=${streamResult.track?.mimeType})")
                                    Timber.tag(TAG).d("      isLossless=$isLossless (module.isLossless=${module.isLossless})")

                                    val mimeType = when {
                                        isAtmos -> "audio/eac3-joc; codecs=ec-3"
                                        isLossless -> "audio/flac; codecs=\"flac\""
                                        else -> "audio/mpeg; codecs=mp3"
                                    }
                                    val bitrate = when {
                                        isAtmos -> 768_000
                                        isLossless -> 1_411_000
                                        else -> 320_000
                                    }

                                    Timber.tag(TAG).d("      âœ“ STREAMING from module ${module.id}: mimeType=$mimeType bitrate=$bitrate itag=${if (isAtmos) 9997 else if (isLossless) 9998 else 9996}")
                                    YTPlayerUtils.PlaybackData(
                                        audioConfig      = meta?.playerConfig?.audioConfig,
                                        videoDetails     = meta?.videoDetails,
                                        playbackTracking = meta?.playbackTracking,
                                        format           = PlayerResponse.StreamingData.Format(
                                            itag             = when {
                                                isAtmos -> 9997
                                                isLossless -> 9998
                                                else -> 9996
                                            },
                                            url              = streamUrl,
                                            mimeType         = mimeType,
                                            bitrate          = bitrate,
                                            width            = null,
                                            height           = null,
                                            contentLength    = null,
                                            quality          = when {
                                                isAtmos -> "DOLBY_ATMOS"
                                                isLossless -> "LOSSLESS"
                                                else -> "HIGH"
                                            },
                                            fps              = null,
                                            qualityLabel     = null,
                                            averageBitrate   = null,
                                            audioQuality     = when {
                                                isAtmos -> "DOLBY_ATMOS"
                                                isLossless -> "LOSSLESS"
                                                else -> "HIGH"
                                            },
                                            approxDurationMs = matchedTrack.duration?.let { (it * 1000L).toString() },
                                            audioSampleRate  = when {
                                                isAtmos -> 48000
                                                isLossless -> 44100
                                                else -> null
                                            },
                                            audioChannels    = when {
                                                isAtmos -> 6
                                                isLossless -> 2
                                                else -> null
                                            },
                                            loudnessDb       = null,
                                            lastModified     = null,
                                            signatureCipher  = null,
                                            cipher           = null,
                                            audioTrack       = null,
                                        ),
                                        streamUrl              = streamUrl,
                                        streamExpiresInSeconds = 3600,
                                        isSpineStream          = true,
                                    )
                                }.getOrNull()

                                if (spineResult != null) return Result.success(spineResult)
                            }
                        }
                    }
                }
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "â•â•â• SPINE INTERCEPT ERROR â•â•â•")
            }
            Timber.tag(TAG).d("â•â•â• SPINE INTERCEPT END â•â•â•")
            } // !forceStandardAudio
            // â”€â”€ End 8spine intercept â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

            // â”€â”€ Lossless (TIDAL) intercept â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            // Opt-in FLAC from a public hifi-api instance. Tried BEFORE JioSaavn so
            // lossless wins. Falls through to Saavn/YouTube on ANY failure.
            if (!forceStandardAudio && !forceSelectedQuality && allowLossless && context.dataStore.get(EnableTidalStreamingKey, false)) {
                Timber.tag(TAG).d("Lossless enabled â€” trying TIDAL for videoId=$videoId")
                val tidalResult = runCatching {
                    val (currentSong, meta, title, artistNames) = getYtMetadata()
                    if (title.isBlank()) return@runCatching null
                    val query = "$title ${artistNames.joinToString(" ")}"
                        .replace("&", " ").replace(",", " ")
                        .replace(Regex("\\s+"), " ").trim()

                    val wantedTitle = title.lowercase(java.util.Locale.US)
                    val wantedArtists = artistNames.map { it.lowercase(java.util.Locale.US) }
                    val wantedDuration = currentSong?.duration
                    val customUrl = context.dataStore.get(TidalInstanceUrlKey, "").ifBlank { null }

                    val candidates = TidalService.search(query, customUrl)
                    // Ranked, not just the first list-order match: Tidal's search
                    // order isn't quality order, and the previous first-match-wins
                    // pick could lock onto a lower-quality candidate (or one whose
                    // stream simply fails) ahead of a same-title/artist/duration
                    // candidate that actually serves lossless.
                    val matchingCandidates = candidates.filter { c ->
                        val ct = c.title.lowercase(java.util.Locale.US)
                        val ca = c.artistNames.map { it.lowercase(java.util.Locale.US) }
                        val titleOk = ct.contains(wantedTitle) || wantedTitle.contains(ct)
                        val artistOk = wantedArtists.isEmpty() || wantedArtists.any { w ->
                            ca.any { it.contains(w) || w.contains(it) }
                        }
                        // Substring matching alone lets a short title pull in an
                        // unrelated track; runtime is the cheap sanity check. When
                        // either duration is unknown there's nothing to check
                        // against â€” require an exact title match instead of
                        // silently skipping the guard, so a generic title can't
                        // slip a wrong track through with no check at all.
                        val durationOk = if (wantedDuration != null && c.duration != null) {
                            kotlin.math.abs(c.duration - wantedDuration) <= 10
                        } else {
                            ct == wantedTitle
                        }
                        titleOk && artistOk && durationOk
                    }.sortedByDescending { tidalQualityRank(it.audioQuality) }

                    if (matchingCandidates.isEmpty()) {
                        Timber.tag(TAG).d("TIDAL: no match for \"$query\" â€” falling back")
                        return@runCatching null
                    }

                    val quality = runCatching {
                        TidalQuality.valueOf(context.dataStore.get(TidalQualityKey, TidalQuality.LOSSLESS.name))
                    }.getOrDefault(TidalQuality.LOSSLESS)

                    // Try every match, best-quality-first, instead of aborting to
                    // Saavn/YouTube the instant the single first candidate's
                    // stream fetch fails.
                    var best: TidalTrack? = null
                    var streamUrl: String? = null
                    for (candidate in matchingCandidates) {
                        val url = TidalService.streamUrl(candidate.id, quality.toApiValue(), customUrl)
                        if (url != null) {
                            best = candidate
                            streamUrl = url
                            break
                        }
                    }
                    if (best == null || streamUrl == null) {
                        Timber.tag(TAG).d("TIDAL: no stream URL among ${matchingCandidates.size} match(es) for \"$query\" â€” falling back")
                        return@runCatching null
                    }

                    Timber.tag(TAG).i("Tidal: streaming FLAC \"${best.title}\" (id=${best.id}, ${quality.toApiValue()}) for videoId=$videoId")
                    YTPlayerUtils.PlaybackData(
                        audioConfig      = meta?.playerConfig?.audioConfig,
                        videoDetails     = meta?.videoDetails,
                        playbackTracking = meta?.playbackTracking,
                        format           = PlayerResponse.StreamingData.Format(
                            itag             = 9999,               // sentinel: lossless FLAC
                            url              = streamUrl,
                            // codecs= segment so the media-info sheet shows "flac", not the "mp3" default
                            mimeType         = "audio/flac; codecs=\"flac\"",
                            bitrate          = 1_411_000,          // nominal 16/44.1 stereo, for the badge
                            width            = null,
                            height           = null,
                            contentLength    = null,
                            quality          = quality.toApiValue(),
                            fps              = null,
                            qualityLabel     = null,
                            averageBitrate   = null,
                            audioQuality     = quality.toApiValue(),
                            approxDurationMs = best.duration?.let { (it * 1000L).toString() },
                            audioSampleRate  = 44100,
                            audioChannels    = 2,
                            loudnessDb       = null,
                            lastModified     = null,
                            signatureCipher  = null,
                            cipher           = null,
                            audioTrack       = null,
                        ),
                        streamUrl              = streamUrl,
                        streamExpiresInSeconds = 3600,
                        isTidalStream          = true,
                    )
                }.getOrNull()

                if (tidalResult != null) return Result.success(tidalResult)
                Timber.tag(TAG).d("TIDAL intercept failed or returned null â€” trying next source")
            }
            // â”€â”€ End TIDAL intercept â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

            val saavnEnabled = context.dataStore.get(EnableSaavnStreamingKey, false)
            if (saavnEnabled) {
                Timber.tag(TAG).d("JioSaavn streaming enabled â€” trying Saavn for videoId=$videoId")
                val saavnResult = runCatching {
                    // Step 1: fetch YouTube Music next items and player metadata (reused from cache if already resolved)
                    val (currentSong, meta, title, artistNames) = getYtMetadata()
                    val artist = artistNames.joinToString(", ")

                    if (title.isBlank()) return@runCatching null

                    Timber.tag(TAG).d("Saavn: resolved title=\"$title\" artists=$artistNames for videoId=$videoId")

                    val albumName = currentSong?.album?.name.orEmpty()
                    val wantedTitleLower = title.lowercase(java.util.Locale.US)
                    val wantedArtistsLower = artistNames.map { it.lowercase(java.util.Locale.US) }
                    val wantedDurationSec = currentSong?.duration

                    val primaryQuery = if (albumName.isNotBlank()) {
                        "$albumName $title $artist"
                    } else {
                        "$title $artist"
                    }
                    .replace("&", " ")
                    .replace(",", " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                    val fallbackQuery = "$title $artist"
                    .replace("&", " ")
                    .replace(",", " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                    suspend fun findMatch(searchQuery: String): com.music.jiosaavn.SaavnSong? {
                        if (searchQuery.isBlank()) return null
                        Timber.tag(TAG).d("Saavn: searching with query: \"$searchQuery\"")
                        val songs = SaavnService.searchSongs(searchQuery).getOrNull() ?: return null
                        return songs.firstOrNull { candidate ->
                            val candidateTitleLower = candidate.name.lowercase(java.util.Locale.US)
                            val candidateArtists = candidate.artists.primary.map { it.name.lowercase(java.util.Locale.US) }
                            
                            val titleMatches = candidateTitleLower.contains(wantedTitleLower) || wantedTitleLower.contains(candidateTitleLower)
                            val artistMatches = wantedArtistsLower.isEmpty() || wantedArtistsLower.any { wanted ->
                                candidateArtists.any { candidateArtist ->
                                    candidateArtist.contains(wanted) || wanted.contains(candidateArtist)
                                }
                            }
                            
                            // Title matching is substring-based in both directions, so a
                            // short title matches plenty of unrelated songs. Runtime is
                            // the cheap tiebreak that stops a different track being
                            // served as this one. When either duration is unknown
                            // there's nothing to tiebreak with â€” require an exact
                            // title match instead of skipping the guard entirely.
                            val candidateDuration = candidate.duration
                            val durationMatches = if (wantedDurationSec != null && candidateDuration != null) {
                                kotlin.math.abs(candidateDuration - wantedDurationSec) <= 10
                            } else {
                                candidateTitleLower == wantedTitleLower
                            }

                            val isMatch = titleMatches && artistMatches && durationMatches
                            if (isMatch) {
                                Timber.tag(TAG).d("Saavn: candidate matched: \"${candidate.name}\" on album \"${candidate.album?.name}\" by ${candidate.artists.primary.joinToString { it.name }}")
                            } else {
                                Timber.tag(TAG).d("Saavn: candidate rejected (name/artist/duration mismatch): \"${candidate.name}\" on album \"${candidate.album?.name}\" by ${candidate.artists.primary.joinToString { it.name }}")
                            }
                            isMatch
                        }
                    }

                    var bestSong = findMatch(primaryQuery)
                    if (bestSong == null && primaryQuery != fallbackQuery) {
                        Timber.tag(TAG).d("Saavn: no match found with primary query, trying fallback: \"$fallbackQuery\"")
                        bestSong = findMatch(fallbackQuery)
                    }

                    if (bestSong == null) {
                        Timber.tag(TAG).d("Saavn: no matching candidate found â€” falling back to YT")
                        return@runCatching null
                    }

                    Timber.tag(TAG).i("Saavn: matched \"${bestSong.name}\" (id=${bestSong.id}, album=\"${bestSong.album?.name}\")")

                    // Step 4: resolve stream URL at requested quality
                    val qualityKey = context.dataStore.get(SaavnAudioQualityKey, SaavnAudioQuality.QUALITY_320.name)
                    val quality = runCatching { SaavnAudioQuality.valueOf(qualityKey) }
                        .getOrDefault(SaavnAudioQuality.QUALITY_320)

                    // First try to resolve stream URL directly from the search result's downloadUrl list
                    // to avoid an extra details API call (saves 300ms-800ms).
                    var streamUrl = SaavnService.selectBestUrl(bestSong.downloadUrl, quality.toApiValue())
                    if (streamUrl.isNullOrBlank()) {
                        Timber.tag(TAG).d("Saavn: downloadUrl list empty in search results, fetching via getBestStreamUrl for songId=${bestSong.id}")
                        streamUrl = SaavnService.getBestStreamUrl(bestSong.id, quality.toApiValue())
                    } else {
                        Timber.tag(TAG).d("Saavn: resolved stream URL directly from search results: $streamUrl")
                    }

                    if (streamUrl.isNullOrBlank()) {
                        Timber.tag(TAG).d("Saavn: no stream URL for songId=${bestSong.id} â€” falling back to YT")
                        return@runCatching null
                    }

                    // Optimization: Skip synchronous HTTP HEAD request to get content length during playback.
                    // ExoPlayer parses the content length automatically from HTTP GET response headers on buffer.
                    val contentLength: Long? = null

                    Timber.tag(TAG).i("Saavn: streaming from JioSaavn (quality=${quality.toApiValue()}) for videoId=$videoId")
                    // Return a minimal YTPlayerUtils.PlaybackData using the Saavn URL.
                    // Reuse the YouTube metadata already fetched in Step 1 â€” no second
                    // network call needed. This keeps audioConfig/videoDetails/playbackTracking
                    // intact so history and normalization still work properly.
                    YTPlayerUtils.PlaybackData(
                        audioConfig      = meta?.playerConfig?.audioConfig,
                        videoDetails     = meta?.videoDetails,
                        playbackTracking = meta?.playbackTracking,
                        format           = PlayerResponse.StreamingData.Format(
                            itag             = when (quality) {
                                SaavnAudioQuality.QUALITY_320 -> 141
                                SaavnAudioQuality.QUALITY_160 -> 140
                                SaavnAudioQuality.QUALITY_96  -> 139
                            },
                            url              = streamUrl,
                            // JioSaavn delivers AAC-LC audio inside a regular MP4 container
                            // (e.g. https://aac.saavncdn.com/.../{id}_320.mp4)
                            mimeType         = "audio/mp4; codecs=\"mp4a.40.2\"",
                            bitrate          = when (quality) {
                                SaavnAudioQuality.QUALITY_320 -> 320_000
                                SaavnAudioQuality.QUALITY_160 -> 160_000
                                SaavnAudioQuality.QUALITY_96  -> 96_000
                            },
                            width            = null,
                            height           = null,
                            contentLength    = contentLength,
                            quality          = quality.toApiValue(),
                            fps              = null,
                            qualityLabel     = null,
                            averageBitrate   = null,
                            audioQuality     = quality.toApiValue(),
                            approxDurationMs = null,
                            audioSampleRate  = null,
                            audioChannels    = null,
                            loudnessDb       = null,
                            lastModified     = null,
                            signatureCipher  = null,
                            cipher           = null,
                            audioTrack       = null,
                        ),
                        streamUrl              = streamUrl,
                        streamExpiresInSeconds = 3600,
                        isSaavnStream          = true,   // â† mark as Saavn so downloads skip YT range trick
                    )
                }.getOrNull()

                if (saavnResult != null) {
                    return Result.success(saavnResult)
                }
                if (!context.dataStore.get(SaavnFallbackToYouTubeKey, true)) {
                    Timber.tag(TAG).d("Saavn intercept failed and YouTube fallback is off â€” failing playback")
                    return Result.failure(IOException("No matching track found on JioSaavn"))
                }
                // Any exception or null â†’ fall through to YouTube below
                Timber.tag(TAG).d("Saavn intercept failed or returned null â€” falling back to YouTube")
            }
        }
        // â”€â”€ End JioSaavn intercept â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        return null
    }
}


