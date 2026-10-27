/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback.queue

import androidx.media3.common.MediaItem
import com.beatwave.music.core.extensions.metadata
import com.beatwave.music.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true): Status {
            if (!enabled) return this
            var targetIndex = mediaItemIndex
            val filtered = items.filterIndexed { index, item ->
                val isExplicit = item.metadata?.explicit == true
                if (isExplicit && index < mediaItemIndex) {
                    targetIndex--
                }
                !isExplicit
            }
            return copy(
                items = filtered,
                mediaItemIndex = targetIndex.coerceIn(0, maxOf(0, filtered.size - 1)),
            )
        }

        fun filterVideoSongs(disableVideos: Boolean = false): Status {
            if (!disableVideos) return this
            var targetIndex = mediaItemIndex
            val filtered = items.filterIndexed { index, item ->
                val isVideo = item.metadata?.isVideoSong == true
                if (isVideo && index < mediaItemIndex) {
                    targetIndex--
                }
                !isVideo
            }
            return copy(
                items = filtered,
                mediaItemIndex = targetIndex.coerceIn(0, maxOf(0, filtered.size - 1)),
            )
        }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }


