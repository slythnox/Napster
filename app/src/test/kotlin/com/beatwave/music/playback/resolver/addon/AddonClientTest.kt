package com.beatwave.music.playback.resolver.addon

import com.beatwave.music.constants.AudioQuality
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class AddonClientTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val searchRequestCount = AtomicInteger(0)
    private val streamRequestCount = AtomicInteger(0)

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val port = server.address.port
        baseUrl = "http://127.0.0.1:$port"

        server.createContext("/manifest.json", HttpHandler { exchange: HttpExchange ->
            val response = """
                {
                    "id": "test-addon",
                    "name": "Test Addon",
                    "version": "1.0.0",
                    "resources": ["search", "stream"],
                    "settings": [
                        {
                            "key": "quality",
                            "name": "Audio Quality",
                            "type": "select",
                            "defaultValue": "HIGH",
                            "options": [
                                {"value": "LOW", "name": "Low"},
                                {"value": "HIGH", "name": "High"},
                                {"value": "LOSSLESS", "name": "Lossless FLAC"}
                            ]
                        }
                    ]
                }
            """.trimIndent()
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.write(bytes)
            exchange.close()
        })

        server.createContext("/search", HttpHandler { exchange: HttpExchange ->
            searchRequestCount.incrementAndGet()
            val query = exchange.requestURI.query ?: ""

            if (query.contains("trigger_500=true")) {
                val err = "Internal Server Error".toByteArray()
                exchange.sendResponseHeaders(500, err.size.toLong())
                exchange.responseBody.write(err)
                exchange.close()
                return@HttpHandler
            }

            if (query.contains("trigger_429=true")) {
                exchange.responseHeaders.add("Retry-After", "2")
                val err = "Too Many Requests".toByteArray()
                exchange.sendResponseHeaders(429, err.size.toLong())
                exchange.responseBody.write(err)
                exchange.close()
                return@HttpHandler
            }

            val response = if (query.contains("direct=true")) {
                """
                {
                    "tracks": [
                        {
                            "id": "direct-track-1",
                            "title": "Bohemian Rhapsody",
                            "artist": "Queen",
                            "durationSec": 354,
                            "streamURL": "http://127.0.0.1:$port/audio/direct.flac",
                            "format": "flac",
                            "audioQuality": "LOSSLESS"
                        }
                    ]
                }
                """.trimIndent()
            } else {
                """
                {
                    "tracks": [
                        {
                            "id": "indirect-track-2",
                            "title": "Bohemian Rhapsody",
                            "artist": "Queen",
                            "durationSec": 354,
                            "streamURL": null,
                            "format": "flac",
                            "audioQuality": "LOSSLESS"
                        }
                    ]
                }
                """.trimIndent()
            }
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.write(bytes)
            exchange.close()
        })

        server.createContext("/stream/indirect-track-2", HttpHandler { exchange: HttpExchange ->
            streamRequestCount.incrementAndGet()
            val response = """
                {
                    "url": "http://127.0.0.1:$port/audio/streamed.flac",
                    "format": "flac",
                    "mimeType": "audio/flac",
                    "kbps": 1411,
                    "audioQuality": "LOSSLESS"
                }
            """.trimIndent()
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.write(bytes)
            exchange.close()
        })

        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun testManifestDiscoveryAndParsing() = runBlocking {
        val client = AddonClient(baseUrl)
        val manifestResult = client.manifest()
        assertTrue(manifestResult.isSuccess)
        val manifest = manifestResult.getOrThrow()
        assertEquals("test-addon", manifest.id)
        assertEquals("Test Addon", manifest.name)
        assertTrue(manifest.isPlayable)
        assertEquals(1, manifest.settings.size)
        assertEquals("quality", manifest.settings.first().key)
    }

    @Test
    fun testManifestNotFoundThrowsAddonException() = runBlocking {
        val deadServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        deadServer.start()
        val deadUrl = "http://127.0.0.1:${deadServer.address.port}"
        try {
            val client = AddonClient(deadUrl)
            val manifestResult = client.manifest()
            assertTrue(manifestResult.isFailure)
            val ex = manifestResult.exceptionOrNull()
            assertTrue(ex is AddonException)
            assertEquals("No manifest found at that URL", ex?.message)
        } finally {
            deadServer.stop(0)
        }
    }

    @Test
    fun testQueryParamsInSearch() = runBlocking {
        val client = AddonClient(baseUrl)
        val tracksResult = client.search("Queen Bohemian", tier = "LOSSLESS")
        assertTrue(tracksResult.isSuccess)
        val tracks = tracksResult.getOrThrow()
        assertEquals(1, tracks.size)
        assertEquals("Bohemian Rhapsody", tracks.first().title)
    }

    @Test
    fun testServerError500ThrowsAddonUnavailable() = runBlocking {
        val client = AddonClient(baseUrl)
        val searchResult = client.search("trigger_500=true")
        assertTrue(searchResult.isFailure)
        val ex = searchResult.exceptionOrNull()
        assertTrue(ex is AddonUnavailable)
        assertTrue(ex?.message?.contains("500") == true)
    }

    @Test
    fun testRateLimit429SetsBackoff() = runBlocking {
        val client = AddonClient(baseUrl)
        val searchResult = client.search("trigger_429=true")
        assertTrue(searchResult.isFailure)
        assertTrue(client.quietUntilMs > System.currentTimeMillis())
    }

    @Test
    fun testRequestDeduplicationInFlight() = runBlocking {
        val client = AddonClient(baseUrl)
        searchRequestCount.set(0)

        // Launch 5 concurrent searches for identical query
        val deferredList = (1..5).map {
            async { client.search("Bohemian Concurrent") }
        }
        val results = deferredList.awaitAll()
        for (res in results) {
            assertTrue(res.isSuccess)
            assertEquals(1, res.getOrThrow().size)
        }

        // SharedCalls should deduplicate: server must only receive 1 network call
        assertEquals(1, searchRequestCount.get())
    }

    @Test
    fun testDirectStreamUrlBypassesStreamEndpoint() = runBlocking {
        val config = AddonConfig(
            id = "test-addon",
            name = "Test Addon",
            baseUrl = baseUrl,
            enabled = true,
        )
        val source = AddonSource(config)
        streamRequestCount.set(0)

        val target = TrackMatcher.Target(
            title = "Bohemian Rhapsody",
            artist = "Queen",
            durationSec = 354,
        )

        // Query with direct=true returns streamURL in search response
        val playbackData = source.resolve(
            target = target.copy(title = "Bohemian Rhapsody direct=true"),
            audioQuality = AudioQuality.LOSSLESS,
            allowLossless = true,
        )

        assertNotNull(playbackData)
        assertEquals("http://127.0.0.1:${server.address.port}/audio/direct.flac", playbackData?.streamUrl)
        // Ensure /stream/{id} was NOT called because direct streamURL was present
        assertEquals(0, streamRequestCount.get())
    }

    @Test
    fun testIndirectStreamUrlCallsStreamEndpoint() = runBlocking {
        val config = AddonConfig(
            id = "test-addon",
            name = "Test Addon",
            baseUrl = baseUrl,
            enabled = true,
        )
        val source = AddonSource(config)
        streamRequestCount.set(0)

        val target = TrackMatcher.Target(
            title = "Bohemian Rhapsody",
            artist = "Queen",
            durationSec = 354,
        )

        val playbackData = source.resolve(
            target = target,
            audioQuality = AudioQuality.LOSSLESS,
            allowLossless = true,
        )

        assertNotNull(playbackData)
        assertEquals("http://127.0.0.1:${server.address.port}/audio/streamed.flac", playbackData?.streamUrl)
        // Ensure /stream/{id} was called
        assertEquals(1, streamRequestCount.get())
    }

    @Test
    fun testTrackMatcherRejectsLiveAndRemixMismatches() {
        val target = TrackMatcher.Target(
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationSec = 200,
        )

        // Live version must be rejected when target is studio
        val liveScore = TrackMatcher.matchScore(
            candidateTitle = "Blinding Lights (Live at SoFi)",
            candidateArtist = "The Weeknd",
            candidateDurationSec = 200,
            target = target,
        )
        assertEquals(-1, liveScore)

        // Remix must be rejected when target is original
        val remixScore = TrackMatcher.matchScore(
            candidateTitle = "Blinding Lights (Chromatics Remix)",
            candidateArtist = "The Weeknd",
            candidateDurationSec = 200,
            target = target,
        )
        assertEquals(-1, remixScore)

        // Acoustic must be rejected when target is original
        val acousticScore = TrackMatcher.matchScore(
            candidateTitle = "Blinding Lights (Acoustic Version)",
            candidateArtist = "The Weeknd",
            candidateDurationSec = 200,
            target = target,
        )
        assertEquals(-1, acousticScore)

        // Studio exact match must score high
        val exactScore = TrackMatcher.matchScore(
            candidateTitle = "Blinding Lights",
            candidateArtist = "The Weeknd",
            candidateDurationSec = 200,
            target = target,
        )
        assertTrue(exactScore >= 100)
    }
}
