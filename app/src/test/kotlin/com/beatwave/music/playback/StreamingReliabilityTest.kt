package com.beatwave.music.playback

import com.beatwave.music.utils.YTPlayerUtils
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17 tests: Streaming reliability and pipeline optimizations.
 */
class StreamingReliabilityTest {

    @Test
    fun `YTPlayerUtils shared httpClient is properly configured`() {
        val client = YTPlayerUtils.httpClient
        assertNotNull("httpClient should not be null", client)
        assertEquals("Connect timeout should be 15s", 15000L, client.connectTimeoutMillis.toLong())
        assertEquals("Read timeout should be 15s", 15000L, client.readTimeoutMillis.toLong())
        assertNotNull("Dns should be configured", client.dns)
        assertNotNull("Proxy selector should be configured", client.proxySelector)
    }

    @Test
    fun `allStreamClients includes main client and fallbacks in priority order`() {
        val clients = YTPlayerUtils.allStreamClients
        assertTrue("allStreamClients should have at least 10 clients", clients.size >= 10)
        // First fallback after MAIN_CLIENT should be VISIONOS, then IOS
        assertEquals("First fallback client should be VISIONOS", VISIONOS, clients[1])
        assertEquals("Second fallback client should be IOS", IOS, clients[2])
        assertEquals("Third fallback client should be ANDROID_VR_1_61_48", ANDROID_VR_1_61_48, clients[3])
    }

    @Test
    fun `distinctUntilChangedBy prevents redundant emissions for identical media ids`() = runBlocking {
        data class TestMetadata(val id: String?, val title: String)

        val flow = MutableStateFlow<TestMetadata?>(TestMetadata("id_1", "Title A"))
        val emittedIds = mutableListOf<String?>()

        val job = flow
            .distinctUntilChangedBy { it?.id }
            .onEach { emittedIds.add(it?.id) }
            .launchIn(this)

        delay(20)

        // Emitting metadata with the same ID but updated title (e.g. progress or tag change)
        flow.value = TestMetadata("id_1", "Title A (Remastered)")
        delay(20)
        flow.value = TestMetadata("id_1", "Title A (Live)")
        delay(20)
        // Emitting a new track ID
        flow.value = TestMetadata("id_2", "Title B")
        delay(20)
        // Emitting same new ID
        flow.value = TestMetadata("id_2", "Title B")
        delay(20)

        job.cancel()

        // Only id_1 (first time) and id_2 (first time) should be emitted
        assertEquals(listOf("id_1", "id_2"), emittedIds)
    }
}
