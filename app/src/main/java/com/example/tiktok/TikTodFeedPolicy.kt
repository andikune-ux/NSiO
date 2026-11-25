package com.example.tiktok

import com.example.data.model.MediaItem
import com.example.data.model.MediaSourceClassifier

/** Feed policy: Drive videos only, randomized without repetition inside one cycle. */
internal object TikTodFeedPolicy {
    fun eligible(items: List<MediaItem>, profileId: String, drivePlaylistIds: Set<String>): List<MediaItem> =
        items.asSequence()
            .filter { it.profileId == profileId }
            .filterNot { it.type.equals("PHOTO", true) }
            .filter { MediaSourceClassifier.isDriveMedia(it, drivePlaylistIds) }
            .distinctBy { it.id }
            .toList()

    fun newCycle(items: List<MediaItem>, previousFirstId: String? = null, random: kotlin.random.Random = kotlin.random.Random.Default): List<MediaItem> {
        if (items.isEmpty()) return emptyList()
        val shuffled = items.shuffled(random)
        if (shuffled.size > 1 && shuffled.first().id == previousFirstId) {
            return shuffled.drop(1) + shuffled.first()
        }
        return shuffled
    }

    /** New items are prioritized at session start, while the previous session's last item is avoided. */
    fun sessionStartOrder(
        items: List<MediaItem>,
        pendingNewIds: Set<String>,
        previousVideoId: String?,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): List<MediaItem> {
        if (items.isEmpty()) return emptyList()
        val fresh = items.filter { it.id in pendingNewIds }.shuffled(random).toMutableList()
        val regular = items.filterNot { it.id in pendingNewIds }.shuffled(random).toMutableList()
        val priority = if (fresh.isNotEmpty()) fresh else regular
        if (priority.size > 1 && priority.first().id == previousVideoId) {
            val alternative = priority.indexOfFirst { it.id != previousVideoId }
            if (alternative > 0) {
                val first = priority[0]
                priority[0] = priority[alternative]
                priority[alternative] = first
            }
        } else if (priority.size == 1 && priority.first().id == previousVideoId && regular.size > 1) {
            val alternative = regular.firstOrNull { it.id != previousVideoId }
            if (alternative != null) {
                return listOf(alternative) + priority + regular.filterNot { it.id == alternative.id }
            }
        }
        return fresh + regular
    }

    fun appendNewItems(current: List<MediaItem>, discovered: List<MediaItem>, playedIds: Set<String>): List<MediaItem> {
        val known = (current.map { it.id } + playedIds).toHashSet()
        return discovered.filter { it.id !in known } + current
    }
}
