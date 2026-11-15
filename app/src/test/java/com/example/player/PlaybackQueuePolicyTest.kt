package com.example.player

import com.example.data.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class PlaybackQueuePolicyTest {
    private fun item(id: String, type: String = "VIDEO") = MediaItem(
        id = id, profileId = "profile", title = id, url = "https://example.com/$id.mp4",
        type = type, sourceType = "PLAYLIST", category = "Test"
    )

    @Test fun queueSkipsPhotosAndRemovesDuplicateIds() {
        val queue = PlaybackQueuePolicy.build(listOf(item("a"), item("photo", "PHOTO"), item("b"), item("a")), "a", false)
        assertEquals(listOf("a", "b"), queue.map { it.id })
    }

    @Test fun shuffleKeepsCurrentItemFirstAndContainsEveryVideoOnce() {
        val queue = PlaybackQueuePolicy.build(listOf(item("a"), item("b"), item("c"), item("d")), "c", true, Random(42))
        assertEquals("c", queue.first().id)
        assertEquals(setOf("a", "b", "c", "d"), queue.map { it.id }.toSet())
        assertEquals(4, queue.map { it.id }.distinct().size)
    }

    @Test fun nextAndPreviousRespectRepeatAllBoundaries() {
        assertEquals(1, PlaybackQueuePolicy.adjacentIndex(3, 0, 1, false))
        assertEquals(2, PlaybackQueuePolicy.adjacentIndex(3, 0, -1, true))
        assertEquals(0, PlaybackQueuePolicy.adjacentIndex(3, 2, 1, true))
        assertNull(PlaybackQueuePolicy.adjacentIndex(3, 2, 1, false))
    }
}
