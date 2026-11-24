package com.example.tiktok

import com.example.data.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTodFeedPolicyTest {
    private fun item(id: String, source: String = "DRIVE", type: String = "VIDEO") = MediaItem(
        id = id,
        profileId = "profile-a",
        title = id,
        url = if (source == "DRIVE") "https://drive.google.com/file/d/$id/view" else "https://example.test/$id.mp4",
        type = type,
        sourceType = source
    )

    @Test
    fun feedContainsOnlyDriveVideosFromActiveProfile() {
        val items = listOf(
            item("drive-a"),
            item("link-a", "LINK"),
            item("photo-a", "DRIVE", "PHOTO"),
            item("other-profile").copy(profileId = "profile-b")
        )
        val result = TikTodFeedPolicy.eligible(items, "profile-a", emptySet())
        assertEquals(listOf("drive-a"), result.map { it.id })
    }

    @Test
    fun cycleContainsEveryVideoOnce() {
        val source = listOf(item("a"), item("b"), item("c"))
        val result = TikTodFeedPolicy.newCycle(source)
        assertEquals(source.map { it.id }.toSet(), result.map { it.id }.toSet())
        assertEquals(result.size, result.map { it.id }.distinct().size)
    }

    @Test
    fun newlyDiscoveredItemsAreReturnedAheadOfExistingQueue() {
        val current = listOf(item("current"), item("later"))
        val added = TikTodFeedPolicy.appendNewItems(current, listOf(item("new")), setOf("already-played"))
        assertEquals(listOf("new", "current", "later"), added.map { it.id })
        assertFalse(added.any { it.id == "already-played" })
        assertTrue(added.first().id == "new")
    }
}
