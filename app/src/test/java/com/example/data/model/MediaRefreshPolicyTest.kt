package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaRefreshPolicyTest {
    @Test
    fun playlistRefreshPreservesIdentityPlaybackAndFavoriteState() {
        val old = MediaItem(
            id = "old-id",
            profileId = "profile-a",
            title = "Old title",
            url = "https://example.com/movie.mp4",
            type = "VIDEO",
            playlistId = "playlist-a",
            sourceUrl = "https://example.com/list.m3u",
            durationMs = 90_000L,
            progressMs = 35_000L,
            lastWatchedAt = 1234L,
            isFavorite = true,
            createdAt = 567L
        )
        val refreshed = old.copy(
            id = "fresh-random-id",
            title = "Updated title",
            thumbnailUrl = "https://example.com/poster.jpg",
            durationMs = 0L,
            progressMs = 0L,
            lastWatchedAt = 0L,
            isFavorite = false,
            createdAt = 999L
        )

        val result = MediaRefreshPolicy.mergePlaylist(listOf(old), listOf(refreshed)).single()

        assertEquals("old-id", result.id)
        assertEquals("Updated title", result.title)
        assertEquals(90_000L, result.durationMs)
        assertEquals(35_000L, result.progressMs)
        assertEquals(1234L, result.lastWatchedAt)
        assertEquals(true, result.isFavorite)
        assertEquals(567L, result.createdAt)
        assertEquals("https://example.com/poster.jpg", result.thumbnailUrl)
    }

    @Test
    fun cloudStreamIdentityIsDifferentAcrossProfiles() {
        val first = MediaRefreshPolicy.cloudStreamId("profile-a", "plugin", "provider", "https://example.com/title")
        val second = MediaRefreshPolicy.cloudStreamId("profile-b", "plugin", "provider", "https://example.com/title")

        assertNotEquals(first, second)
    }

    @Test
    fun cloudStreamRefreshMigratesLegacyIdAndPreservesLocalState() {
        val legacy = MediaItem(
            id = "legacy-global-id",
            profileId = "profile-a",
            title = "Old title",
            url = "https://example.com/title",
            type = "CLOUDSTREAM",
            sourceType = "CLOUDSTREAM",
            pluginId = "plugin",
            extraInfo = "provider",
            progressMs = 42_000L,
            durationMs = 100_000L,
            lastWatchedAt = 900L,
            isFavorite = true,
            createdAt = 321L
        )
        val fresh = legacy.copy(
            id = "old-shared-id",
            title = "New title",
            progressMs = 0L,
            durationMs = 0L,
            lastWatchedAt = 0L,
            isFavorite = false,
            createdAt = 999L
        )

        val result = MediaRefreshPolicy.mergeCloudStream(
            profileId = "profile-a",
            pluginId = "plugin",
            incoming = listOf(fresh),
            existing = listOf(legacy)
        ).single()

        assertNotEquals("legacy-global-id", result.id)
        assertEquals(MediaRefreshPolicy.cloudStreamId("profile-a", "plugin", "provider", fresh.url), result.id)
        assertEquals(42_000L, result.progressMs)
        assertEquals(100_000L, result.durationMs)
        assertEquals(900L, result.lastWatchedAt)
        assertEquals(true, result.isFavorite)
        assertEquals(321L, result.createdAt)
    }
}
