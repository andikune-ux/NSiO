package com.example.data.model

object WatchHistoryPolicy {
    fun recentlyWatched(items: List<MediaItem>): List<MediaItem> =
        items.asSequence()
            .filter { it.lastWatchedAt > 0L }
            .sortedByDescending { it.lastWatchedAt }
            .toList()
}
