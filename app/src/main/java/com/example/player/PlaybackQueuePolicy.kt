package com.example.player

import com.example.data.model.MediaItem
import kotlin.random.Random

/** Queue order policy for video playback; the active item remains first on shuffle activation. */
internal object PlaybackQueuePolicy {
    fun build(items: List<MediaItem>, currentId: String, shuffle: Boolean, random: Random = Random.Default): List<MediaItem> {
        val videos = items.asSequence()
            .filterNot { it.type.equals("PHOTO", ignoreCase = true) }
            .distinctBy { it.id }
            .toList()
        val current = videos.firstOrNull { it.id == currentId } ?: return videos
        return if (shuffle) listOf(current) + videos.filterNot { it.id == currentId }.shuffled(random) else videos
    }

    fun adjacentIndex(itemCount: Int, currentIndex: Int, direction: Int, repeatAll: Boolean): Int? {
        if (itemCount <= 0 || currentIndex !in 0 until itemCount || direction == 0) return null
        val target = currentIndex + if (direction > 0) 1 else -1
        if (target in 0 until itemCount) return target
        if (!repeatAll) return null
        return if (direction > 0) 0 else itemCount - 1
    }
}
