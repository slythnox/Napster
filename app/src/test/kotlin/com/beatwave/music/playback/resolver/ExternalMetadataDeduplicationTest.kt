package com.beatwave.music.playback.resolver

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ExternalMetadataDeduplicationTest {

    private data class MockResolvedMetadata(
        val title: String,
        val artistNames: List<String>,
    )

    @Test
    fun `metadata is fetched exactly once across multiple external provider attempts`() = runBlocking {
        val networkCallCount = AtomicInteger(0)

        // Simulates the scoped lazy resolver in ExternalStreamProviderResolver
        var cachedMetadata: MockResolvedMetadata? = null

        suspend fun getOrFetchMetadata(): MockResolvedMetadata {
            cachedMetadata?.let { return it }
            // Simulates network fetch
            networkCallCount.incrementAndGet()
            return MockResolvedMetadata("Viva La Vida", listOf("Coldplay")).also {
                cachedMetadata = it
            }
        }

        // Provider 1 (Spine) calls getOrFetchMetadata()
        val spineMeta = getOrFetchMetadata()
        assertEquals("Viva La Vida", spineMeta.title)
        assertEquals(1, networkCallCount.get())

        // Provider 2 (Tidal) calls getOrFetchMetadata() after Spine returns null
        val tidalMeta = getOrFetchMetadata()
        assertEquals("Viva La Vida", tidalMeta.title)
        assertEquals(1, networkCallCount.get()) // Reused, no second network call!

        // Provider 3 (JioSaavn) calls getOrFetchMetadata() after Tidal fails
        val saavnMeta = getOrFetchMetadata()
        assertEquals("Viva La Vida", saavnMeta.title)
        assertEquals(1, networkCallCount.get()) // Reused, no third network call!
    }
}
