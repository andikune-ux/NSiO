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
    @Test
    fun removedWatchedOrFavoriteItemsRemainAvailableAsHistory() {
        val removedWatched = MediaItem(
            "watched", "profile-a", "Watched", "https://example.com/removed.mp4",
            playlistId = "playlist-a", progressMs = 12_000L, lastWatchedAt = 100L
        )
        val removedFavorite = MediaItem(
            "favorite", "profile-a", "Favorite", "https://example.com/favorite.mp4",
            playlistId = "playlist-a", isFavorite = true
        )
        val refreshed = MediaItem(
            "new", "profile-a", "New", "https://example.com/new.mp4",
            playlistId = "playlist-a"
        )

        val result = MediaRefreshPolicy.mergePlaylist(
            existing = listOf(removedWatched, removedFavorite),
            incoming = listOf(refreshed)
        )

        assertEquals(setOf("new", "watched", "favorite"), result.map { it.id }.toSet())
    }

    @Test
    fun drivePhotoRenameKeepsStableRowIdentityAndAddsNewFolderMedia() {
        val existingPhoto = MediaItem(
            id = "photo-row-id",
            profileId = "profile-a",
            title = "Nama lama.jpg",
            url = "https://lh3.googleusercontent.com/d/drive-photo-id=w2000",
            thumbnailUrl = "https://lh3.googleusercontent.com/d/drive-photo-id=w800",
            type = "PHOTO",
            sourceType = "DRIVE",
            playlistId = "drive-folder",
            sourceUrl = "https://drive.google.com/drive/folders/folder-id",
            isFavorite = true,
            progressMs = 0L,
            createdAt = 100L
        )
        val renamedPhoto = existingPhoto.copy(
            id = "fresh-photo-random-id",
            title = "Nama baru.jpg",
            isFavorite = false,
            createdAt = 999L
        )
        val newVideo = MediaItem(
            id = "new-video-row",
            profileId = "profile-a",
            title = "Video baru.mp4",
            url = "https://drive.google.com/file/d/new-video-id/preview",
            type = "VIDEO",
            sourceType = "DRIVE",
            playlistId = "drive-folder",
            sourceUrl = "https://drive.google.com/drive/folders/folder-id"
        )

        val result = MediaRefreshPolicy.mergePlaylist(
            existing = listOf(existingPhoto),
            incoming = listOf(renamedPhoto, newVideo)
        )

        val savedPhoto = result.single { it.type == "PHOTO" }
        assertEquals("photo-row-id", savedPhoto.id)
        assertEquals("Nama baru.jpg", savedPhoto.title)
        assertEquals(true, savedPhoto.isFavorite)
        assertEquals(100L, savedPhoto.createdAt)
        assertEquals("new-video-row", result.single { it.type == "VIDEO" }.id)
    }

}
