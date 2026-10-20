/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import com.beatwave.music.constants.MediaSessionConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for Android Auto queue conversion, playback advancement,
 * repeat-mode policies, next/previous transitions, and empty-state fallbacks.
 */
class AndroidAutoPlaybackQueueTest {

    // Simulates multi-track container to queue conversion
    data class MockMediaItem(val mediaId: String, val title: String, val artist: String)

    data class MockQueueState(
        val items: List<MockMediaItem>,
        var currentIndex: Int,
        var currentPositionMs: Long = 0L,
        var repeatMode: Int = Player.REPEAT_MODE_OFF,
        var shuffleModeEnabled: Boolean = false,
    )

    private fun resolveMediaItemsWithStartPosition(
        pathParts: List<String>,
        availableSongs: List<MockMediaItem>,
        defaultIndex: Int = 0,
        defaultPos: Long = C.TIME_UNSET,
    ): Pair<List<MockMediaItem>, Int> {
        if (pathParts.isEmpty()) return Pair(emptyList(), defaultIndex)

        val root = pathParts[0]
        return when (root) {
            MusicService.SONG -> {
                val songId = pathParts.getOrNull(1) ?: return Pair(emptyList(), defaultIndex)
                val targetIndex = availableSongs.indexOfFirst { it.mediaId == songId }.takeIf { it != -1 } ?: 0
                Pair(availableSongs, targetIndex)
            }
            MusicService.ALBUM, MusicService.PLAYLIST, MusicService.ARTIST -> {
                val songId = pathParts.getOrNull(2)
                val targetIndex = if (songId != null) {
                    availableSongs.indexOfFirst { it.mediaId == songId }.takeIf { it != -1 } ?: 0
                } else {
                    0 // Tapping the container itself starts from the beginning
                }
                Pair(availableSongs, targetIndex)
            }
            else -> {
                // Fallback / raw media IDs
                val rawId = pathParts.lastOrNull()
                val match = availableSongs.find { it.mediaId == rawId }
                if (match != null) {
                    Pair(listOf(match), 0)
                } else {
                    Pair(emptyList(), defaultIndex)
                }
            }
        }
    }

    private fun simulateTrackTransition(
        state: MockQueueState,
        reason: Int,
        targetIndex: Int,
    ) {
        val previousIndex = state.currentIndex
        state.currentIndex = targetIndex

        // Replicates fixed onMediaItemTransition logic:
        // Only if player repeat mode is REPEAT_MODE_ONE does it enforce repeat-one
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            if (state.repeatMode == Player.REPEAT_MODE_ONE &&
                previousIndex != C.INDEX_UNSET &&
                previousIndex != state.currentIndex
            ) {
                // Seek back to previous index
                state.currentIndex = previousIndex
                state.currentPositionMs = 0L
            }
        }
    }

    private fun simulateNext(state: MockQueueState): Boolean {
        if (state.items.isEmpty()) return false
        val nextIndex = state.currentIndex + 1
        return if (nextIndex < state.items.size) {
            state.currentIndex = nextIndex
            state.currentPositionMs = 0L
            true
        } else if (state.repeatMode == Player.REPEAT_MODE_ALL) {
            state.currentIndex = 0
            state.currentPositionMs = 0L
            true
        } else {
            false
        }
    }

    private fun simulatePrevious(state: MockQueueState, rewindThresholdMs: Long = 3000L): Boolean {
        if (state.items.isEmpty()) return false
        if (state.currentPositionMs > rewindThresholdMs) {
            state.currentPositionMs = 0L
            return true
        }
        val prevIndex = state.currentIndex - 1
        return if (prevIndex >= 0) {
            state.currentIndex = prevIndex
            state.currentPositionMs = 0L
            true
        } else if (state.repeatMode == Player.REPEAT_MODE_ALL) {
            state.currentIndex = state.items.lastIndex
            state.currentPositionMs = 0L
            true
        } else {
            state.currentPositionMs = 0L
            true
        }
    }

    private val sampleSongs = listOf(
        MockMediaItem("song_1", "First Song", "Artist A"),
        MockMediaItem("song_2", "Second Song", "Artist A"),
        MockMediaItem("song_3", "Third Song", "Artist B"),
    )

    @Test
    fun `playlist to queue conversion preserves all items and resolves target index`() {
        val (items, startIndex) = resolveMediaItemsWithStartPosition(
            listOf(MusicService.PLAYLIST, "playlist_123", "song_2"),
            sampleSongs
        )
        assertEquals(3, items.size)
        assertEquals(1, startIndex)
        assertEquals("song_2", items[startIndex].mediaId)
    }

    @Test
    fun `tapping container card without songId starts playback from index 0`() {
        // Android Auto sends path ["playlist", "playlist_123"] when clicking playlist row directly
        val (items, startIndex) = resolveMediaItemsWithStartPosition(
            listOf(MusicService.PLAYLIST, "playlist_123"),
            sampleSongs
        )
        assertEquals(3, items.size)
        assertEquals(0, startIndex)
        assertEquals("song_1", items[startIndex].mediaId)
    }

    @Test
    fun `tapping album card without songId starts playback from index 0`() {
        val (items, startIndex) = resolveMediaItemsWithStartPosition(
            listOf(MusicService.ALBUM, "album_999"),
            sampleSongs
        )
        assertEquals(3, items.size)
        assertEquals(0, startIndex)
    }

    @Test
    fun `raw mediaId gracefully resolves single song item`() {
        val (items, startIndex) = resolveMediaItemsWithStartPosition(
            listOf("song_3"),
            sampleSongs
        )
        assertEquals(1, items.size)
        assertEquals(0, startIndex)
        assertEquals("song_3", items[0].mediaId)
    }

    @Test
    fun `empty library or unavailable items return empty queue without crashing`() {
        val (items, _) = resolveMediaItemsWithStartPosition(
            listOf(MusicService.PLAYLIST, "empty_playlist"),
            emptyList()
        )
        assertTrue(items.isEmpty())

        val (unknownItems, _) = resolveMediaItemsWithStartPosition(
            listOf("non_existent_id"),
            sampleSongs
        )
        assertTrue(unknownItems.isEmpty())
    }

    @Test
    fun `multi-track auto-advance progresses to next track when repeat is off`() {
        val state = MockQueueState(
            items = sampleSongs,
            currentIndex = 0,
            repeatMode = Player.REPEAT_MODE_OFF
        )

        // Song 0 ends naturally; ExoPlayer advances to song 1
        simulateTrackTransition(state, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, targetIndex = 1)
        assertEquals("Track should advance to index 1", 1, state.currentIndex)

        // Song 1 ends naturally; ExoPlayer advances to song 2
        simulateTrackTransition(state, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, targetIndex = 2)
        assertEquals("Track should advance to index 2", 2, state.currentIndex)
    }

    @Test
    fun `repeat-one forces seek to same track on auto-advance`() {
        val state = MockQueueState(
            items = sampleSongs,
            currentIndex = 1,
            repeatMode = Player.REPEAT_MODE_ONE
        )

        // Player auto-advanced to 2 while repeat mode is REPEAT_MODE_ONE
        simulateTrackTransition(state, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, targetIndex = 2)
        assertEquals("Repeat-one should remain on index 1", 1, state.currentIndex)
        assertEquals(0L, state.currentPositionMs)
    }

    @Test
    fun `repeat-all does not trap track on same index during auto-advance`() {
        val state = MockQueueState(
            items = sampleSongs,
            currentIndex = 0,
            repeatMode = Player.REPEAT_MODE_ALL
        )

        // Song 0 ends naturally in repeat-all mode
        simulateTrackTransition(state, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, targetIndex = 1)
        assertEquals("Repeat-all must advance to index 1", 1, state.currentIndex)
    }

    @Test
    fun `next and previous navigation works across boundary conditions`() {
        val state = MockQueueState(
            items = sampleSongs,
            currentIndex = 0,
            repeatMode = Player.REPEAT_MODE_OFF
        )

        // Next from 0 -> 1
        assertTrue(simulateNext(state))
        assertEquals(1, state.currentIndex)

        // Next from 1 -> 2
        assertTrue(simulateNext(state))
        assertEquals(2, state.currentIndex)

        // Next from 2 (end of queue, repeat-off) -> returns false
        assertFalse(simulateNext(state))
        assertEquals(2, state.currentIndex)

        // Previous from 2 when progress > threshold rewinds current track
        state.currentPositionMs = 5000L
        assertTrue(simulatePrevious(state))
        assertEquals("Should rewind to 0 on track 2", 2, state.currentIndex)
        assertEquals(0L, state.currentPositionMs)

        // Previous from 2 when progress <= threshold jumps to track 1
        assertTrue(simulatePrevious(state))
        assertEquals(1, state.currentIndex)

        // Previous from 1 jumps to track 0
        assertTrue(simulatePrevious(state))
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `repeat-all wraps next and previous around queue ends`() {
        val state = MockQueueState(
            items = sampleSongs,
            currentIndex = 2,
            repeatMode = Player.REPEAT_MODE_ALL
        )

        // Next from last item wraps to 0
        assertTrue(simulateNext(state))
        assertEquals(0, state.currentIndex)

        // Previous from first item (progress = 0) wraps to last item
        assertTrue(simulatePrevious(state))
        assertEquals(2, state.currentIndex)
    }

    @Test
    fun `custom session command identifiers are well-formed`() {
        assertEquals("TOGGLE_LIKE", MediaSessionConstants.ACTION_TOGGLE_LIKE)
        assertEquals("TOGGLE_START_RADIO", MediaSessionConstants.ACTION_TOGGLE_START_RADIO)
        assertEquals("TOGGLE_LIBRARY", MediaSessionConstants.ACTION_TOGGLE_LIBRARY)
        assertEquals("TOGGLE_SHUFFLE", MediaSessionConstants.ACTION_TOGGLE_SHUFFLE)
        assertEquals("TOGGLE_REPEAT_MODE", MediaSessionConstants.ACTION_TOGGLE_REPEAT_MODE)
    }
}
