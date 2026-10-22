/*
 * Napster (C) 2026
 * Licensed under GPL-3.0
 */

package com.beatwave.music.playback.resolver.addon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

open class AddonException(message: String, cause: Throwable? = null) : Exception(message, cause)
class AddonNotFound(message: String = "Not found") : AddonException(message)
class AddonUnavailable(message: String) : AddonException(message)

/**
 * Communicates with an external HTTP streaming add-on server according to the open JSON protocol.
 */
class AddonClient(
    rawBaseUrl: String,
    private val client: OkHttpClient = defaultClient,
) {
    val baseUrl: String = normalizeBase(rawBaseUrl)

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val calls = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manifests = SharedCalls<AddonManifest>(MANIFEST_TTL_MS, calls) { Timber.tag(TAG).d(it) }
    private val searches = SharedCalls<AddonSearchResponse>(SEARCH_TTL_MS, calls) { Timber.tag(TAG).d(it) }
    private val streams = SharedCalls<AddonStream>(STREAM_TTL_MS, calls) { Timber.tag(TAG).d(it) }

    @Volatile
    var quietUntilMs = 0L
        private set

    suspend fun manifest(): Result<AddonManifest> = manifests.get(
        key = baseUrl,
        describe = { "▶ addon manifest(${redact(baseUrl)})" }
    ) {
        fetch<AddonManifest>(manifestUrl(baseUrl)).mapCatching { manifest ->
            if (manifest.id.isBlank()) {
                throw AddonException("The URL responded, but not with a valid add-on manifest")
            }
            if (!manifest.isPlayable) {
                val declared = manifest.resources.joinToString(", ")
                throw AddonException("This add-on declares $declared — Napster requires search")
            }
            manifest
        }.recoverCatching { failure ->
            if (failure is AddonNotFound) throw AddonException("No manifest found at that URL")
            throw failure
        }
    }

    suspend fun probeSearch(): Result<Int> =
        fetch<AddonSearchResponse>(endpoint(listOf("search"), mapOf("q" to PROBE_QUERY)))
            .map { it.tracks.size }

    suspend fun search(query: String, tier: String = "HIGH"): Result<List<AddonTrack>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        val params = settingsFor(tier)
        return searches.get(
            key = "${trimmed.length}:$trimmed|${params}",
            describe = { "▶ addon search(${redact(baseUrl)}) q=\"$trimmed\" tier=$tier" }
        ) {
            fetch<AddonSearchResponse>(endpoint(listOf("search"), params + ("q" to trimmed)))
        }.map { it.tracks }
    }

    suspend fun stream(trackId: String, tier: String = "HIGH"): Result<AddonStream> {
        val params = settingsFor(tier)
        return streams.get(
            key = "${trackId.length}:$trackId|${params}",
            describe = { "▶ addon stream(${redact(baseUrl)}) id=$trackId tier=$tier" }
        ) {
            fetch<AddonStream>(endpoint(listOf("stream", trackId), params))
        }
    }

    private suspend fun settingsFor(tier: String): Map<String, String> {
        val declared = manifest().getOrNull()?.settings.orEmpty()
        val params = LinkedHashMap<String, String>()
        declared.forEach { setting ->
            val key = setting.key.takeIf { it.isNotBlank() } ?: return@forEach
            setting.defaultValue?.let { params[key] = it }
        }
        if (tier.isNotBlank()) {
            val options = declared.firstOrNull { it.key == QUALITY_KEY }
                ?.options.orEmpty()
                .mapNotNull { it.stringValue }
            params[QUALITY_KEY] = matchTier(tier, options) ?: tier
        }
        return params
    }

    private fun matchTier(tier: String, options: List<String>): String? {
        if (options.isEmpty()) return null
        options.firstOrNull { it.equals(tier, ignoreCase = true) }?.let { return it }
        val wanted = when (tier.uppercase()) {
            "LOSSLESS" -> listOf("lossless", "flac", "hi-res", "hires", "max")
            "LOW" -> listOf("low", "data saver", "96", "128")
            else -> listOf("high", "standard", "hq", "320", "256")
        }
        return options.firstOrNull { opt -> wanted.any { it in opt.lowercase() } }
            ?: options.first()
    }

    private fun endpoint(segments: List<String>, params: Map<String, String>): String {
        val base = baseUrl.toHttpUrlOrNull()
            ?: throw AddonException("Malformed base URL")
        val builder = base.newBuilder()
        segments.forEach(builder::addPathSegment)
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    private suspend inline fun <reified T> fetch(url: String): Result<T> =
        runCatching { json.decodeFromString<T>(body(url)) }
            .onFailure { failure ->
                if (failure is CancellationException) throw failure
                if (failure !is AddonNotFound) {
                    Timber.tag(TAG).w("Addon call failed: ${redact(url)} - ${failure.message}")
                }
            }

    private suspend fun body(url: String): String = withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            val now = System.currentTimeMillis()
            val remainingCooldown = quietUntilMs - now
            if (remainingCooldown > 0) {
                delay(remainingCooldown.coerceAtMost(MAX_BACKOFF_WAIT_MS))
            }

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Napster-Android/1.0")
                .build()

            val waitTime = client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        return@withContext response.body?.string()?.takeIf { it.isNotBlank() }
                            ?: throw AddonException("Empty response from add-on")
                    }
                    response.code == 404 -> throw AddonNotFound("Not found on add-on")
                    response.code >= 500 -> throw AddonUnavailable("Server error: HTTP ${response.code}")
                    response.code == 429 -> {
                        val wait = retryAfterMs(response.header("Retry-After"), attempt)
                        quietUntilMs = System.currentTimeMillis() + wait
                        if (attempt >= MAX_RETRIES) {
                            throw AddonUnavailable("Add-on rate limit exceeded")
                        }
                        wait
                    }
                    else -> throw AddonException("HTTP ${response.code}")
                }
            }

            attempt++
        }
        @Suppress("UNREACHABLE_CODE")
        throw AddonException("unreachable")
    }

    private fun retryAfterMs(header: String?, attempt: Int): Long {
        val stated = header?.trim()?.toDoubleOrNull()?.times(1000)?.toLong()
        return (stated ?: (BACKOFF_BASE_MS shl attempt)).coerceIn(BACKOFF_BASE_MS, BACKOFF_CAP_MS)
    }

    fun clear() {
        manifests.clear()
        searches.clear()
        streams.clear()
        quietUntilMs = 0L
    }

    companion object {
        private const val TAG = "AddonClient"
        private const val PROBE_QUERY = "probe"
        private const val QUALITY_KEY = "quality"

        private const val MANIFEST_TTL_MS = 60 * 60 * 1000L // 1 hour
        private const val SEARCH_TTL_MS = 5 * 60 * 1000L   // 5 minutes
        private const val STREAM_TTL_MS = 10 * 60 * 1000L  // 10 minutes

        private const val MAX_RETRIES = 2
        private const val BACKOFF_BASE_MS = 1000L
        private const val BACKOFF_CAP_MS = 10_000L
        private const val MAX_BACKOFF_WAIT_MS = 5_000L

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun normalizeBase(url: String): String =
            url.trim()
                .removeSuffix("/")
                .removeSuffix("/manifest.json")
                .trimEnd('/')

        fun manifestUrl(base: String): String =
            "${normalizeBase(base)}/manifest.json"

        fun redact(url: String): String =
            runCatching {
                val parsed = url.toHttpUrlOrNull() ?: return@runCatching url
                parsed.newBuilder().query(null).build().toString()
            }.getOrDefault(url)
    }
}
