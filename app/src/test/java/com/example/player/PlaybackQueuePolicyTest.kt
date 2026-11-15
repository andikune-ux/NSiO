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

    @Test fun queueOnlyContainsItemsFromCurrentSource() {
        val driveA = item("drive-a").copy(sourceType = "DRIVE", playlistId = "drive-list")
        val driveB = item("drive-b").copy(sourceType = "DRIVE", playlistId = "drive-list")
        val otherDrive = item("drive-other").copy(sourceType = "DRIVE", playlistId = "other-list")
        val cloudStream = item("cloud").copy(sourceType = "CLOUDSTREAM", pluginId = "plugin-a")
        val queue = PlaybackQueuePolicy.filterForCurrentSource(
            listOf(driveA, driveB, otherDrive, cloudStream),
            driveA
        )
        assertEquals(listOf("drive-a", "drive-b"), queue.map { it.id })
    }

    @Test fun cloudStreamQueueStaysInsideTheSamePlugin() {
        val first = item("cs-a").copy(sourceType = "CLOUDSTREAM", pluginId = "plugin-a")
        val second = item("cs-b").copy(sourceType = "CLOUDSTREAM", pluginId = "plugin-a")
        val anotherPlugin = item("cs-c").copy(sourceType = "CLOUDSTREAM", pluginId = "plugin-b")
        val drive = item("drive").copy(sourceType = "DRIVE")
        val queue = PlaybackQueuePolicy.filterForCurrentSource(
            listOf(first, second, anotherPlugin, drive),
            first
        )
        assertEquals(listOf("cs-a", "cs-b"), queue.map { it.id })
    }

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
