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
}
