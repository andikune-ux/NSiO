package com.example.data.model

object WatchHistoryPolicy {
    fun recentlyWatched(items: List<MediaItem>): List<MediaItem> =
        items.asSequence()
            .filter { it.lastWatchedAt > 0L }
            .sortedByDescending { it.lastWatchedAt }
            .toList()

    fun continueWatching(items: List<MediaItem>): List<MediaItem> =
        items.asSequence()
            .filter { it.progressMs > 0L && (it.durationMs <= 0L || it.progressMs < it.durationMs) }
            .sortedWith(compareByDescending<MediaItem> { it.lastWatchedAt }
                .thenByDescending { it.progressMs })
            .toList()
}
