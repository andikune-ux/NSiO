package com.example.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSourceClassifierTest {
    @Test
    fun recognizesOnlyExactGoogleDriveHosts() {
        assertTrue(MediaSourceClassifier.isGoogleDriveUrl("https://drive.google.com/file/d/abc/view"))
        assertTrue(MediaSourceClassifier.isGoogleDriveUrl("https://docs.google.com/document/d/abc/edit"))
        assertTrue(MediaSourceClassifier.isGoogleDriveUrl("https://drive.usercontent.google.com/download?id=abc"))
        assertTrue(MediaSourceClassifier.isGoogleDriveUrl("https://drive.googleusercontent.com/uc?id=abc"))
        assertFalse(MediaSourceClassifier.isGoogleDriveUrl("https://drive.google.com.evil.example/file"))
        assertFalse(MediaSourceClassifier.isGoogleDriveUrl("https://example.com/drive.google.com"))
        assertFalse(MediaSourceClassifier.isGoogleDriveUrl("not a url"))
    }

    @Test
    fun classifiesDriveCatalogItemsByExplicitIdentityOrPlaylist() {
        val explicit = MediaItem(
            id = "drive-1",
            profileId = "profile",
            title = "Drive item",
            url = "https://cdn.example/video.mp4",
            sourceType = "DRIVE"
        )
        val playlist = MediaItem(
            id = "drive-2",
            profileId = "profile",
            title = "Playlist item",
            url = "https://cdn.example/video.mp4",
            playlistId = "drive-playlist"
        )
        assertTrue(MediaSourceClassifier.isDriveMedia(explicit))
        assertTrue(MediaSourceClassifier.isDriveMedia(playlist, setOf("drive-playlist")))
        assertFalse(MediaSourceClassifier.isDriveMedia(playlist, setOf("other-playlist")))
    }
}
