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
    fun mauMuUsesOnlyTheSelectedAvatarIncludingPageTwoAvatars() {
        val items = listOf(item("page-one"), item("jj-one"), item("jj-two"))
        val assignments = mapOf(
            "page-one" to "p1:balqyz",
            "jj-one" to "p2:balqyz",
            "jj-two" to "p2:ayu"
        )
        assertEquals(listOf("jj-one"), TikTodFeedPolicy.forAvatar(items, assignments, "p2:balqyz").map { it.id })
        assertEquals(listOf("page-one"), TikTodFeedPolicy.forAvatar(items, assignments, "p1:balqyz").map { it.id })
    }

    @Test
    fun avatarVideoCountsIncludeEmptyAvatarsAndCountSharedDriveFileOnce() {
        val first = item("same-drive-file")
        val duplicateEntry = first.copy(id = "second-entry")
        val other = item("other-drive-file")
        val assignments = mapOf(
            "drive:same-drive-file" to "p1:balqyz",
            "drive:other-drive-file" to "p2:rawina"
        )

        assertEquals(
            mapOf("p1:balqyz" to 1, "p1:ayu" to 0, "p2:rawina" to 1),
            TikTodFeedPolicy.avatarVideoCounts(
                listOf(first, duplicateEntry, other),
                assignments,
                listOf("p1:balqyz", "p1:ayu", "p2:rawina")
            )
        )
    }

    @Test
    fun jedagJedugCombinesAllPageTwoAvatarsAndExcludesPageOne() {
        val items = listOf(item("page-one"), item("jj-one"), item("jj-two"))
        val assignments = mapOf(
            "page-one" to "p1:balqyz",
            "jj-one" to "p2:balqyz",
            "jj-two" to "p2:ayu"
        )
        assertEquals(listOf("jj-one", "jj-two"), TikTodFeedPolicy.jedagJedug(items, assignments).map { it.id })
    }

    @Test
    fun unassignedVideosAreNotAddedToAvatarFeeds() {
        val items = listOf(item("assigned"), item("unassigned"))
        val assignments = mapOf("assigned" to "p1:balqyz")
        assertEquals(listOf("assigned"), TikTodFeedPolicy.forAvatar(items, assignments, "p1:balqyz").map { it.id })
        assertTrue(TikTodFeedPolicy.jedagJedug(items, assignments).isEmpty())
    }

    @Test
    fun cycleContainsEveryVideoOnce() {
        val source = listOf(item("a"), item("b"), item("c"))
        val result = TikTodFeedPolicy.newCycle(source)
        assertEquals(source.map { it.id }.toSet(), result.map { it.id }.toSet())
        assertEquals(result.size, result.map { it.id }.distinct().size)
    }

    @Test
    fun refreshedCycleDoesNotStartWithCurrentVideoWhenAlternativesExist() {
        val items = listOf(item("current"), item("other-a"), item("other-b"))
        repeat(20) { seed ->
            val result = TikTodFeedPolicy.newCycle(
                items = items,
                previousFirstId = "current",
                random = kotlin.random.Random(seed)
            )
            assertTrue(result.first().id != "current")
            assertEquals(items.map { it.id }.toSet(), result.map { it.id }.toSet())
            assertEquals(result.size, result.map { it.id }.distinct().size)
        }
    }

    @Test
    fun sessionStartPrioritizesNewItemsAndAvoidsPreviousVideo() {
        val items = listOf(item("old-a"), item("new-a"), item("new-b"))
        val result = TikTodFeedPolicy.sessionStartOrder(
            items = items,
            pendingNewIds = setOf("new-a", "new-b"),
            previousVideoId = "new-a",
            random = kotlin.random.Random(7)
        )
        assertTrue(result.first().id != "new-a")
        assertTrue(result.take(2).all { it.id.startsWith("new-") })
        assertEquals(items.map { it.id }.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun sessionStartAvoidsPreviousVideoEvenWhenOnlyOneFreshItemRemains() {
        val items = listOf(item("previous"), item("regular"))
        val result = TikTodFeedPolicy.sessionStartOrder(
            items = items,
            pendingNewIds = setOf("previous"),
            previousVideoId = "previous",
            random = kotlin.random.Random(2)
        )
        assertEquals("regular", result.first().id)
        assertEquals(items.map { it.id }.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun sessionStartFallsBackToAnotherVideoWhenOnlyRegularQueueHasPreviousFirst() {
        val items = listOf(item("old-a"), item("old-b"))
        val result = TikTodFeedPolicy.sessionStartOrder(
            items = items,
            pendingNewIds = emptySet(),
            previousVideoId = resultIdForTest(items),
            random = kotlin.random.Random(3)
        )
        assertTrue(result.first().id != resultIdForTest(items))
    }

    private fun resultIdForTest(items: List<MediaItem>): String = items.first().id

    @Test
    fun newlyDiscoveredItemsAreReturnedAheadOfExistingQueue() {
        val current = listOf(item("current"), item("later"))
        val added = TikTodFeedPolicy.appendNewItems(current, listOf(item("new")), setOf("already-played"))
        assertEquals(listOf("new", "current", "later"), added.map { it.id })
        assertFalse(added.any { it.id == "already-played" })
        assertTrue(added.first().id == "new")
    }
}
