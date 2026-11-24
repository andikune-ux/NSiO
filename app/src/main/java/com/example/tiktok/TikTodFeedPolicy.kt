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

    fun appendNewItems(current: List<MediaItem>, discovered: List<MediaItem>, playedIds: Set<String>): List<MediaItem> {
        val known = (current.map { it.id } + playedIds).toHashSet()
        return discovered.filter { it.id !in known } + current
    }
}
