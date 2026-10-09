/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.utils

import android.net.ConnectivityManager

import androidx.media3.common.PlaybackException
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.beatwave.music.utils.BotDetectionMitigator
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import com.beatwave.music.constants.AudioQuality
import org.json.JSONArray
import com.beatwave.music.utils.cipher.CipherDeobfuscator
import com.beatwave.music.utils.YTPlayerUtils.MAIN_CLIENT
import com.beatwave.music.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import com.beatwave.music.utils.YTPlayerUtils.validateStatus
import com.beatwave.music.utils.potoken.PoTokenGenerator
import com.beatwave.music.utils.potoken.PoTokenResult
import com.beatwave.music.utils.sabr.EjsNTransformSolver
import com.beatwave.music.playback.resolver.providers.ExternalStreamProviderResolver
import com.beatwave.music.utils.PlaybackLogLevel
import com.beatwave.music.utils.PlaybackLogManager
import com.music.innertube.models.IpVersion
import com.music.innertube.models.WatchEndpoint
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Higher first. Ranks Tidal search-result candidates so the matcher below tries
 *  the best-quality candidate first instead of Tidal's own (unordered w.r.t.
 *  quality) search result order. */
object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                return when (YouTube.ipVersion) {
                    IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                    IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                    IpVersion.AUTO -> addresses
                }
            }
        })
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOfNotNull(YouTube.proxy ?: Proxy.NO_PROXY)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                Timber.tag(TAG).e(ioe, "Proxy connection failed for URI: $uri")
            }
        })
        .proxyAuthenticator { _, response ->
            YouTube.proxyAuth?.let { auth ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", auth)
                    .build()
            } ?: response.request
        }
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    /**
     * Client used for fast, low-latency stream resolution.
     * ANDROID_VR clients don't require PoToken and start instantly.
     * Note: ANDROID_VR has loginSupported=false, so metadata like audioConfig and
     * playbackTracking must be supplemented from an authenticated client (WEB_REMIX)
     * when the user is logged in.
     */
    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_1_43_32

    /** Used when a player response omits streamingData.expiresInSeconds — 6h, YouTube's usual value. */
    private const val DEFAULT_STREAM_EXPIRY_SECONDS = 21600

    /**
     * Client used to fetch metadata (audioConfig, playbackTracking) when the user is
     * logged in. This ensures remote YouTube history is correctly updated.
     */
    private val METADATA_CLIENT: YouTubeClient = WEB_REMIX

    /**
     * Tried in order when [MAIN_CLIENT] cannot produce a stream.
     *
     * IOS is first, and that ordering is measured rather than assumed. Probing the live
     * API (2026-08-19) against a track YouTube had bot-flagged, every one of ANDROID_VR
     * (both versions), ANDROID_MUSIC, IOS_MUSIC, TVHTML5 and WEB_REMIX came back
     * LOGIN_REQUIRED / "Sign in to confirm you're not a bot" or UNPLAYABLE -- and IOS
     * returned OK with direct URLs. Across five popular tracks IOS answered 5/5.
     *
     * It used to sit ninth. So the common failure -- one bot-flagged track -- cost eight
     * doomed round trips, several of them generating a PoToken first, before reaching the
     * client that works. That is the shape users describe as "playing very late", and when
     * one of those eight stalls it is the shape they describe as "song not playing".
     *
     * ANDROID_VR stays as MAIN_CLIENT: when it is not flagged it is fast and offers more
     * audio formats (4 vs IOS's 2), so it remains the better first choice. This array is
     * purely the recovery path.
     *
     * VISIONOS was added 2026-08-19 ahead of IOS: an unreleased/internal client YouTube
     * hasn't started bot-flagging yet, unlike IOS which the 2026-08-19 probe caught 5/5
     * but user reports since then show failing on bot-flagged tracks too. IOS kept second
     * since it still works when VISIONOS doesn't.
     *
     * IPADOS answered 0/5 in the same probe and is kept only for completeness.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        VISIONOS,
        IOS,
        ANDROID_VR_1_61_48,
        WEB_REMIX,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,  // Try embedded player first for age-restricted content
        TVHTML5,
        ANDROID_CREATOR,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        WEB,
        WEB_CREATOR
    )
    /**
     * Every client playback may try, in the order it tries them.
     *
     * Exposed for [YouTubeClientProbe] only. Deliberately derived from the same values the
     * player path uses, so a probe result always describes the chain that actually runs.
     */
    internal val allStreamClients: List<YouTubeClient>
        get() = listOf(MAIN_CLIENT) + STREAM_FALLBACK_CLIENTS

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        /** True when the stream is sourced from JioSaavn (not YouTube). */
        val isSaavnStream: Boolean = false,
        /** True when the stream is lossless FLAC sourced from TIDAL (hifi-api). */
        val isTidalStream: Boolean = false,
        /** True when the stream is sourced from an 8spine module. */
        val isSpineStream: Boolean = false,
    )
    /**
     * Custom player response intended to use for playback.
     * Stream URLs come from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS] for fast loading.
     * Metadata (audioConfig, playbackTracking) come from [METADATA_CLIENT] (WEB_REMIX)
     * when the user is logged in, to ensure remote history recording works correctly.
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context? = null,
        /** Downloads pass false so the offline copy stays YouTube; playback allows lossless. */
        allowLossless: Boolean = true,
        /** Set when a specific track has already failed lossless playback too many
         *  times (stall/parsing errors) — skips the Spine and Tidal intercepts
         *  entirely and resolves the plain YouTube stream for this call only. */
        forceStandardAudio: Boolean = false,
    ): Result<PlaybackData> {
        val externalResult = ExternalStreamProviderResolver.resolve(
            videoId = videoId,
            playlistId = playlistId,
            audioQuality = audioQuality,
            context = context,
            allowLossless = allowLossless,
            forceStandardAudio = forceStandardAudio,
            metadataResolver = ::playerResponseForMetadata,
        )
        if (externalResult != null) return externalResult

        val firstAttempt = resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager)

        // A geo-restricted or universally terminal video fails the same way on a retry — rotating a
        // guest session or re-attempting as-is can't fix a region block or deleted video, so
        // don't waste the retry on it either way.
        val firstErrorMsg = firstAttempt.exceptionOrNull()?.message
        val isTerminalOrGeo = BotDetectionMitigator.isUniversalTerminalError(firstErrorMsg)

        if (firstAttempt.isFailure && !isTerminalOrGeo) {
            // visitorData rides in every request's client context independent of
            // login — cookie/dataSyncId are separate fields — so a flagged
            // visitorData can trigger bot detection on a signed-in session just
            // as it does for a guest. A bare same-identity retry reproduces the
            // exact same rejection; rotating visitorData actually changes
            // something and is what gives a signed-in user a real chance too,
            // without touching their login cookie or dataSyncId.
            val label = if (YouTube.cookie == null) "guest" else "signed-in user"
            Timber.tag(TAG).w("Playback failed for $label. Rotating session and retrying...")
            PlaybackLogManager.log(PlaybackLogLevel.BOT, "Playback failed for $label", "Triggering bot detection mitigation (rotating guest session)")
            BotDetectionMitigator.rotateGuestSession()
            val retryResult = resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager)
            retryResult.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
            return retryResult
        }

        firstAttempt.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
        return firstAttempt
    }

    private suspend fun resolvePlaybackData(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        Timber.tag(logTag).d("Fetching player response for videoId: $videoId, playlistId: $playlistId")
        PlaybackLogManager.log(PlaybackLogLevel.INFO, "Resolving playback data", "Video: $videoId")
        
        // Debug: Log ALL playback attempts
        println("[PLAYBACK_DEBUG] playerResponseForPlayback called: videoId=$videoId, playlistId=$playlistId")
        // Check if this is an uploaded/privately owned track
        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true

        val isLoggedIn = YouTube.cookie != null
        Timber.tag(logTag).d("Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"}")

        // Signature timestamp, fetched ONLY if a client we actually try needs one, and at
        // most once per call.
        //
        // This used to run eagerly on every single playback. Getting it means downloading
        // and parsing YouTube's player JavaScript through NewPipe -- a blocking network
        // round trip plus a JS parse -- in front of every song. And MAIN_CLIENT is
        // ANDROID_VR, which declares useSignatureTimestamp = false: the value was fetched
        // and then handed to a client that does not use one, on the hot path, every time.
        //
        // Two failure modes came out of that. The cost shows up as songs taking seconds to
        // start; and when YouTube changes the player JS shape, the extraction fails on a
        // request that never needed it in the first place.
        var signatureTimestampValue: Int? = null
        var signatureTimestampFetched = false
        suspend fun signatureTimestampFor(client: YouTubeClient): Int? {
            if (!client.useSignatureTimestamp) return null
            if (!signatureTimestampFetched) {
                signatureTimestampValue = getSignatureTimestampOrNull(videoId).timestamp
                signatureTimestampFetched = true
                Timber.tag(logTag).d("Signature timestamp obtained lazily: $signatureTimestampValue")
            }
            return signatureTimestampValue
        }

        // Generate PoToken ONLY if MAIN_CLIENT uses it (which it now doesn't since we use ANDROID_VR)
        var poToken: PoTokenResult? = null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            Timber.tag(logTag).d("Generating PoToken for MAIN_CLIENT with sessionId")
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                if (poToken != null) {
                    Timber.tag(logTag).d("PoToken generated successfully")
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "PoToken generation failed: ${e.message}")
            }
        }

        // Try MAIN_CLIENT (ANDROID_VR) for fast stream resolution and METADATA_CLIENT (WEB_REMIX) for history tracking in parallel
        var (mainPlayerResponse, metadataResponse) = coroutineScope {
            val mainDeferred = async {
                Timber.tag(logTag).d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
                PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying ${MAIN_CLIENT.clientName} (Main)")
                YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestampFor(MAIN_CLIENT), poToken?.playerRequestPoToken).getOrThrow()
            }
            val metaDeferred = async {
                if (isLoggedIn) {
                    Timber.tag(logTag).d("Fetching metadata from METADATA_CLIENT (WEB_REMIX) for authenticated tracking")
                    try {
                        // Only generate PoToken for web client metadata fetch
                        var metaPoToken: PoTokenResult? = null
                        val metaSessionId = YouTube.dataSyncId
                        if (METADATA_CLIENT.useWebPoTokens && metaSessionId != null) {
                            try {
                                metaPoToken = poTokenGenerator.getWebClientPoToken(videoId, metaSessionId)
                            } catch (e: Exception) {
                                Timber.tag(logTag).e(e, "Metadata PoToken generation failed")
                            }
                        }
                        YouTube.player(
                            videoId, playlistId, METADATA_CLIENT,
                            signatureTimestampFor(METADATA_CLIENT), metaPoToken?.playerRequestPoToken
                        ).getOrNull().also { response ->
                            Timber.tag(logTag).d("Metadata response obtained: ${response?.playabilityStatus?.status}")
                        }
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "Failed to fetch metadata from METADATA_CLIENT")
                        null
                    }
                } else {
                    null
                }
            }
            mainDeferred.await() to kotlinx.coroutines.withTimeoutOrNull(400L) { metaDeferred.await() }
        }

        // Debug uploaded track response
        if (isUploadedTrack || playlistId?.contains("MLPT") == true) {
            println("[PLAYBACK_DEBUG] Main player response status: ${mainPlayerResponse.playabilityStatus.status}")
            println("[PLAYBACK_DEBUG] Playability reason: ${mainPlayerResponse.playabilityStatus.reason}")
            println("[PLAYBACK_DEBUG] Video details: title=${mainPlayerResponse.videoDetails?.title}, videoId=${mainPlayerResponse.videoDetails?.videoId}")
            println("[PLAYBACK_DEBUG] Streaming data null? ${mainPlayerResponse.streamingData == null}")
            println("[PLAYBACK_DEBUG] Adaptive formats count: ${mainPlayerResponse.streamingData?.adaptiveFormats?.size ?: 0}")
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val wasOriginallyAgeRestricted: Boolean

        // Check if MAIN_CLIENT response indicates age-restricted.
        // NOTE: Do NOT include LOGIN_REQUIRED here — ANDROID_VR returns LOGIN_REQUIRED as a
        // bot-detection / client-not-supported signal, NOT a content age gate. Treating it as
        // age-restricted incorrectly reroutes every bot-flagged request through WEB_CREATOR
        // and causes streaming failures for logged-in users.
        val mainStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestrictedFromResponse = mainStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        )
        wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {
            // Age-restricted: use WEB_CREATOR directly (no NewPipe needed from here)
            Timber.tag(logTag).d("Age-restricted detected, using WEB_CREATOR")
            Timber.tag(TAG).i("Age-restricted: using WEB_CREATOR for videoId=$videoId")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null).getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("WEB_CREATOR works for age-restricted content")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }

        // If we still don't have a valid response, throw
        if (mainPlayerResponse == null) {
            throw Exception("Failed to get player response")
        }

        // Fetch audioConfig and playbackTracking from the metadata client if available (authenticated)
        // Fall back to mainPlayerResponse values if metadata fetch failed or user is not logged in
        val audioConfig = metadataResponse?.playerConfig?.audioConfig ?: mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = metadataResponse?.videoDetails ?: mainPlayerResponse.videoDetails
        val playbackTracking = metadataResponse?.playbackTracking ?: mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        var retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null

        // Check current status
        val currentStatus = mainPlayerResponse.playabilityStatus.status
        var isAgeRestricted = currentStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        )

        if (isAgeRestricted) {
            Timber.tag(logTag).d("Content is still age-restricted (status: $currentStatus), will try fallback clients")
            Timber.tag(TAG).i("Age-restricted content detected: videoId=$videoId, status=$currentStatus")
        }

        // Check if this is a privately owned track (uploaded song)
        val isPrivateTrack = mainPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

        // Where to enter the fallback chain.
        //   private track   -> skip to TVHTML5, which is the one that serves uploads
        //   age-restricted  -> skip MAIN_CLIENT, start at the top of the fallbacks
        //   otherwise       -> -1, meaning try MAIN_CLIENT's own streams first
        //
        // Resolved by identity, not by a literal index. The comment here used to say
        // "index 1 = TVHTML5" while index 1 was actually WEB_REMIX -- the array had been
        // reordered and the magic number silently stopped meaning what it claimed. Any
        // future reordering now moves this with it.
        val startIndex = when {
            isPrivateTrack -> STREAM_FALLBACK_CLIENTS.indexOf(TVHTML5).coerceAtLeast(0)
            isAgeRestricted -> 0
            else -> -1
        }

        for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {
            // reset for each client
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            // decide which client to use for streams and load its player response
            val client: YouTubeClient
            if (clientIndex == -1) {
                // try with streams from main client first (use retry response if available)
                client = MAIN_CLIENT
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                Timber.tag(logTag).d("Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {
                // after main client use fallback clients
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Timber.tag(logTag).d("Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")
                PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying fallback [${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}]", client.clientName)

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    // skip client if it requires login but user is not logged in
                    Timber.tag(logTag).d("Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                // Lazily generate PoToken for fallback web clients if we haven't already
                if (client.useWebPoTokens && poToken == null && sessionId != null) {
                    Timber.tag(logTag).d("Lazily generating PoToken for fallback web client: ${client.clientName}")
                    try {
                        poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "Lazy PoToken generation failed")
                    }
                }

                Timber.tag(logTag).d("Fetching player response for fallback client: ${client.clientName}")
                // Only pass poToken for clients that support it
                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                // Skip signature timestamp for age-restricted (faster), use it for normal content
                val clientSigTimestamp =
                    if (wasOriginallyAgeRestricted) null else signatureTimestampFor(client)
                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken).getOrNull()
            }

            // process current client response
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("Player response status OK for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                PlaybackLogManager.log(PlaybackLogLevel.INFO, "Player response OK", if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName)

                // Check if formats have direct URLs (no signatureCipher needed)
                val hasDirectUrls = streamPlayerResponse.streamingData?.adaptiveFormats
                    ?.any { !it.url.isNullOrEmpty() } == true
                val hasSignatureCipher = streamPlayerResponse.streamingData?.adaptiveFormats
                    ?.any { !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() } == true

                Timber.tag(logTag).d("URL check: hasDirectUrls=$hasDirectUrls, hasSignatureCipher=$hasSignatureCipher")

                // Skip NewPipe - use direct URLs or custom cipher in findUrlOrNull
                val responseToUse = streamPlayerResponse

                format =
                    findFormat(
                        responseToUse,
                        audioQuality,
                        connectivityManager,
                    )

                if (format == null) {
                    Timber.tag(logTag).d("No suitable format found for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    continue
                }

                Timber.tag(logTag).d("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")

                streamUrl = findUrlOrNull(format, videoId, responseToUse, skipNewPipe = wasOriginallyAgeRestricted)
                if (streamUrl == null) {
                    Timber.tag(logTag).d("Stream URL not found for format")
                    continue
                }

                // Apply n-transform for throttle parameter handling
                val currentClient = if (clientIndex == -1) {
                    usedAgeRestrictedClient ?: MAIN_CLIENT
                } else {
                    STREAM_FALLBACK_CLIENTS[clientIndex]
                }

                // Check if this is a privately owned track
                val isPrivatelyOwnedTrack = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                // Apply n-transform FIRST for web clients (main branch order - critical!)
                if (currentClient.useWebPoTokens) {
                    try {
                        Timber.tag(logTag).d("Applying n-transform to stream URL for ${currentClient.clientName}")
                        val transformed = EjsNTransformSolver.transformNParamInUrl(streamUrl!!)
                        if (transformed != streamUrl) {
                            streamUrl = transformed
                            Timber.tag(logTag).d("N-transform applied successfully")
                        }
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "N-transform failed: ${e.message}")
                    }
                }

                // Apply PoToken SECOND (after n-transform - main branch order)
                // Note: pot token is base64 - do NOT Uri.encode it (breaks validation)
                if (currentClient.useWebPoTokens && poToken?.streamingDataPoToken != null) {
                    Timber.tag(logTag).d("Appending pot= parameter to stream URL")
                    val separator = if ("?" in streamUrl!!) "&" else "?"
                    streamUrl = "${streamUrl}${separator}pot=${poToken.streamingDataPoToken}"
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    // NOT a reason to discard a stream that resolved. `continue` here
                    // threw away a working format + URL over a missing housekeeping
                    // field, walked the rest of the fallback clients, and — when the
                    // field is absent from every response, which is what a YouTube-side
                    // shape change looks like — ended with "Missing stream expire time"
                    // and no playback at all.
                    Timber.tag(logTag).w("Stream expiration time not found — defaulting to ${DEFAULT_STREAM_EXPIRY_SECONDS}s")
                    streamExpiresInSeconds = DEFAULT_STREAM_EXPIRY_SECONDS
                }

                Timber.tag(logTag).d("Stream expires in: $streamExpiresInSeconds seconds")

                // Debug: Log URL host and pot token for debugging
                val urlHost = try { java.net.URL(streamUrl).host } catch (e: Exception) { "unknown" }
                Timber.tag(logTag).d("Stream URL host: $urlHost, pot length: ${poToken?.streamingDataPoToken?.length ?: 0}")

                // Check if this is a privately owned track (uploaded song)
                val isPrivatelyOwned = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1 || isPrivatelyOwned) {
                    /** skip [validateStatus] for last client or private tracks */
                    if (isPrivatelyOwned) {
                        Timber.tag(logTag).d("Skipping validation for privately owned track: ${currentClient.clientName}")
                        println("[PLAYBACK_DEBUG] Using stream without validation for PRIVATELY_OWNED_TRACK")
                    } else {
                        Timber.tag(logTag).d("Using last fallback client without validation: ${STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    }
                    Timber.tag(TAG).i("Playback: client=${currentClient.clientName}, videoId=$videoId, private=$isPrivatelyOwned")
                    break
                }

                if (validateStatus(streamUrl!!)) {
                    // working stream found
                    Timber.tag(logTag).d("Stream validated successfully with client: ${currentClient.clientName}")
                    PlaybackLogManager.log(PlaybackLogLevel.INFO, "Stream validated", currentClient.clientName)
                    // Log for release builds
                    Timber.tag(TAG).i("Playback: client=${currentClient.clientName}, videoId=$videoId")
                    break
                } else {
                    Timber.tag(logTag).d("Stream validation failed for client: ${currentClient.clientName}")

                    // For web clients: try alternate n-transform and re-validate (Zemer approach)
                    if (currentClient.useWebPoTokens) {
                        var nTransformWorked = false

                        // Try CipherDeobfuscator n-transform
                        try {
                            val nTransformed = CipherDeobfuscator.transformNParamInUrl(streamUrl!!)
                            if (nTransformed != streamUrl) {
                                Timber.tag(logTag).d("CipherDeobfuscator n-transform applied, re-validating...")
                                if (validateStatus(nTransformed)) {
                                    Timber.tag(logTag).d("N-transformed URL VALIDATED OK!")
                                    streamUrl = nTransformed
                                    nTransformWorked = true
                                    Timber.tag(TAG).i("Playback: client=${currentClient.clientName}, videoId=$videoId (cipher n-transform)")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag(logTag).e(e, "CipherDeobfuscator n-transform error")
                        }

                        if (nTransformWorked) break
                    }
                }
            } else {
                val status = streamPlayerResponse?.playabilityStatus?.status ?: "Unknown"
                val reason = streamPlayerResponse?.playabilityStatus?.reason ?: "No reason"
                Timber.tag(logTag).d("Player response status not OK: $status, reason: $reason")
                PlaybackLogManager.log(PlaybackLogLevel.WARNING, "Client failed: ${client.clientName}", "$status: $reason")
                
                if (BotDetectionMitigator.isUniversalTerminalError(reason)) {
                    Timber.tag(logTag).w("Universal terminal error detected ($status: $reason), aborting fallback client loop.")
                    break
                }
            }
        }

        if (streamPlayerResponse == null) {
            Timber.tag(logTag).e("Bad stream player response - all clients failed")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: All clients failed for uploaded track videoId=$videoId")
            }
            throw Exception("Bad stream player response")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Timber.tag(logTag).e("Playability status not OK: $errorReason")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: Playability not OK for uploaded track - status=${streamPlayerResponse.playabilityStatus.status}, reason=$errorReason")
            }
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        // Checked before streamExpiresInSeconds: format/streamUrl not being set is
        // WHY streamExpiresInSeconds never got set either (all three are reset to
        // null at the top of every loop iteration, and expiresInSeconds is only
        // assigned after format+streamUrl already succeeded for that client) — the
        // old order reported "Missing stream expire time" for every failure, even
        // ones where the real cause was no usable audio format or a dead stream
        // URL on the last-tried client. Surfacing the real reason here.
        if (format == null) {
            Timber.tag(logTag).e("Could not find format")
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            Timber.tag(logTag).e("Could not find stream url")
            throw Exception("Could not find stream url")
        }

        // format + streamUrl came from a response that DID succeed, so a missing
        // expiry here is the genuinely rare case (a client omitting the field) —
        // default rather than throw away an otherwise-playable stream.
        val expiresInSeconds = streamExpiresInSeconds ?: DEFAULT_STREAM_EXPIRY_SECONDS

        Timber.tag(logTag).d("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        if (isUploadedTrack) {
            println("[PLAYBACK_DEBUG] SUCCESS: Got playback data for uploaded track - format=${format.mimeType}, streamUrl=${streamUrl?.take(100)}...")
        }
        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            expiresInSeconds,
        )
    }.onFailure { e ->
        Timber.tag(logTag).e(e, "Playback resolution failed")
        PlaybackLogManager.log(PlaybackLogLevel.ERROR, "Playback failed", "${e::class.simpleName}: ${e.message}")
        
        // Restore original println for Logcat
        println("[PLAYBACK_DEBUG] EXCEPTION during playback for videoId=$videoId: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
    }
    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).d("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX) // ANDROID_VR does not work with history
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        Timber.tag(logTag).d("Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")

        val audioFormats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio }
            .orEmpty()
        // Some videos (auto-dubs, certain regional content) only serve non-original
        // audio tracks — requiring isOriginal made those permanently unplayable
        // (format=null -> thrown as a generic Exception -> ExoPlayer buckets it as
        // ERROR_CODE_IO_UNSPECIFIED and retries the same doomed resolve forever).
        val original = audioFormats.filter { it.isOriginal }
        val candidates = original.ifEmpty { audioFormats }

        val format = candidates
            .maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus stream
            }

        if (format != null) {
            Timber.tag(logTag).d("Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        } else {
            Timber.tag(logTag).d("No suitable audio format found")
        }

        return format
    }
    /**
     * Checks if the stream url returns a successful status.
     * If this returns true the url is likely to work.
     * If this returns false the url might cause an error during playback.
     */
    private fun validateStatus(url: String): Boolean {
        Timber.tag(logTag).d("Validating stream URL status")
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(url)
                .header("User-Agent", YouTubeClient.USER_AGENT_WEB)

            // Do NOT add Cookie header — googlevideo.com CDN rejects account cookies with 403.
            // Stream URLs are already authenticated via signed URL parameters.

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val isSuccessful = response.isSuccessful
            Timber.tag(logTag).d("Stream URL validation result: ${if (isSuccessful) "Success" else "Failed"} (${response.code})")
            return isSuccessful
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return false
    }
    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                Timber.tag(logTag).d("Signature timestamp obtained: $timestamp")
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                if (isAgeRestricted) {
                    Timber.tag(logTag).d("Age-restricted content detected from NewPipe")
                    Timber.tag(TAG).i("Age-restricted detected early via NewPipe: videoId=$videoId")
                } else {
                    Timber.tag(logTag).e(error, "Failed to get signature timestamp")
                    reportException(error)
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        Timber.tag(logTag).d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe")

        // First check if format already has a URL
        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return format.url
        }

        // Try custom cipher deobfuscation for signatureCipher formats
        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Timber.tag(logTag).d("Format has signatureCipher, using custom deobfuscation")
            val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customDeobfuscatedUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained via custom cipher deobfuscation")
                return customDeobfuscatedUrl
            }
            Timber.tag(logTag).d("Custom cipher deobfuscation failed")
        }

        // Skip NewPipe for age-restricted content
        if (skipNewPipe) {
            Timber.tag(logTag).d("Skipping NewPipe methods for age-restricted content")
            return null
        }

        // Try to get URL using NewPipeExtractor signature deobfuscation
        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            Timber.tag(logTag).d("Stream URL obtained via NewPipe deobfuscation")
            return deobfuscatedUrl
        }

        // Fallback: try to get URL from StreamInfo
        Timber.tag(logTag).d("Trying StreamInfo fallback for URL")
        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained from StreamInfo")
                return streamUrl
            }

            // If exact itag not found, try to find any audio stream
            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second

            if (audioStream != null) {
                Timber.tag(logTag).d("Audio stream URL obtained from StreamInfo (different itag)")
                return audioStream
            }
        }

        Timber.tag(logTag).e("Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }
}
