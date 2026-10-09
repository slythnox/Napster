package com.beatwave.music.feature.search

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchDebounceTest {

    @Test
    fun `empty query clears immediately with zero delay`() = runBlocking {
        val query = MutableStateFlow("initial")
        val emissions = mutableListOf<String>()

        val job = query
            .debounce { if (it.isEmpty()) 0L else 300L }
            .onEach { emissions.add(it) }
            .launchIn(this)

        // Clear query immediately
        query.value = ""
        delay(50) // Small delay to let collector run, well under 300ms

        assertEquals(listOf(""), emissions)
        job.cancel()
    }

    @Test
    fun `rapid typing coalesces into single emission after debounce period`() = runBlocking {
        val query = MutableStateFlow("")
        val emissions = mutableListOf<String>()

        val job = query
            .debounce { if (it.isEmpty()) 0L else 300L }
            .onEach { emissions.add(it) }
            .launchIn(this)

        delay(50) // initial empty string emitted
        emissions.clear()

        // Rapid typing simulation
        query.value = "c"
        delay(50)
        query.value = "co"
        delay(50)
        query.value = "col"
        delay(50)
        query.value = "cold"
        delay(50)

        // No emissions yet because typing interval is < 300ms
        assertEquals(emptyList<String>(), emissions)

        // Wait for debounce period (300ms) to expire
        delay(350)

        assertEquals(listOf("cold"), emissions)
        job.cancel()
    }
}
