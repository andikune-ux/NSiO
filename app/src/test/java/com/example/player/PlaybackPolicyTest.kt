package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPolicyTest {
    @Test
    fun normalizesDriveFilePreviewAndViewLinksWithoutInventingConfirmationToken() {
        val expected = "https://drive.google.com/uc?export=download&id=abc_123-xyz"
        assertEquals(expected, normalizePlaybackUrl("https://drive.google.com/file/d/abc_123-xyz/view?usp=sharing"))
        assertEquals(expected, normalizePlaybackUrl("https://drive.google.com/file/d/abc_123-xyz/preview"))
    }

    @Test
    fun normalizesDriveOpenAndUcQueryLinks() {
        val expected = "https://drive.google.com/uc?export=download&id=abc123"
        assertEquals(expected, normalizePlaybackUrl("https://drive.google.com/open?id=abc123"))
        assertEquals(expected, normalizePlaybackUrl("https://drive.google.com/uc?export=download&id=abc123"))
    }

    @Test
    fun leavesNonDriveAndLocalUrisUnchanged() {
        assertEquals("https://example.com/video.mp4", normalizePlaybackUrl("https://example.com/video.mp4"))
        assertEquals("content://media/external/video/42", normalizePlaybackUrl("content://media/external/video/42"))
        assertEquals("https://drive.google.com.evil.example/open?id=abc123", normalizePlaybackUrl("https://drive.google.com.evil.example/open?id=abc123"))
    }
}
