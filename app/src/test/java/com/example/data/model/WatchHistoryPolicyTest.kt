package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchHistoryPolicyTest {
    @Test
    fun returnsOnlyWatchedItemsInMostRecentOrder() {
        val older = MediaItem("older", "p", "Older", "https://example.com/older.mp4", lastWatchedAt = 100L)
        val neverWatched = MediaItem("new", "p", "New", "https://example.com/new.mp4")
        val newer = MediaItem("newer", "p", "Newer", "https://example.com/newer.mp4", lastWatchedAt = 300L)

        val result = WatchHistoryPolicy.recentlyWatched(listOf(older, neverWatched, newer))

        assertEquals(listOf("newer", "older"), result.map { it.id })
    }
    @Test
    fun continueWatchingUsesLastWatchedTimeAndExcludesCompletedItems() {
        val moreProgressButOlder = MediaItem(
            "older", "p", "Older", "https://example.com/older.mp4",
            durationMs = 10_000L, progressMs = 8_000L, lastWatchedAt = 100L
        )
        val recentlyWatched = MediaItem(
            "recent", "p", "Recent", "https://example.com/recent.mp4",
            durationMs = 10_000L, progressMs = 2_000L, lastWatchedAt = 300L
        )
        val completed = MediaItem(
            "done", "p", "Done", "https://example.com/done.mp4",
            durationMs = 10_000L, progressMs = 10_000L, lastWatchedAt = 500L
        )

        assertEquals(
            listOf("recent", "older"),
            WatchHistoryPolicy.continueWatching(listOf(moreProgressButOlder, completed, recentlyWatched)).map { it.id }
        )
    }

}
