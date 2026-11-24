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

    @Test
    fun buildsGoogleDriveConfirmationRequestFromHiddenFormFields() {
        val original = okhttp3.Request.Builder()
            .url("https://drive.google.com/uc?export=download&id=abc123")
            .header("Range", "bytes=0-")
            .build()
        val html = """
            <html><body>
              <form id="download-form" action="https://drive.usercontent.google.com/download" method="get">
                <input type="hidden" name="id" value="abc123">
                <input type="hidden" name="export" value="download">
                <input type="hidden" name="confirm" value="t-987">
                <input type="hidden" name="uuid" value="session-456">
              </form>
            </body></html>
        """.trimIndent()

        val resolved = buildGoogleDriveConfirmationRequest(original, html)
        assertEquals("drive.usercontent.google.com", resolved.url.host)
        assertEquals("abc123", resolved.url.queryParameter("id"))
        assertEquals("download", resolved.url.queryParameter("export"))
        assertEquals("t-987", resolved.url.queryParameter("confirm"))
        assertEquals("session-456", resolved.url.queryParameter("uuid"))
        assertEquals("bytes=0-", resolved.header("Range"))
    }

    @Test
    fun doesNotTreatNonDriveRequestsAsConfirmationCandidates() {
        val original = okhttp3.Request.Builder().url("https://example.com/video.mp4").build()
        assertEquals(null, googleDriveConfirmationRequestOrNull(original, "<html></html>"))
    }
}
