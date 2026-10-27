/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback.queue

import androidx.media3.common.MediaItem
import com.beatwave.music.core.extensions.metadata
import com.beatwave.music.models.MediaMetadata

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
    override val preloadItem: MediaMetadata? = null,
) : Queue {

    override suspend fun getInitialStatus() = Queue.Status(
        title = title,
        items = items,
        mediaItemIndex = startIndex.coerceIn(0, maxOf(0, items.size - 1)),
        position = position
    )

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()
}
