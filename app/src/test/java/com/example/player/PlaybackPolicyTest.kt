package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPolicyTest {
    @Test
    fun restoresLegacyDriveDownloadEndpointForPreviewAndViewLinks() {
        val expected = "https://drive.usercontent.google.com/download?id=abc_123-xyz&export=download&confirm=t"
        assertEquals(expected, normalizePlaybackUrl("https://drive.google.com/file/d/abc_123-xyz/view?usp=sharing"))
        assertEquals(expected, normalizePlaybackUrl("https://drive.google.com/file/d/abc_123-xyz/preview"))
    }

    @Test
    fun normalizesDriveOpenAndUcQueryLinks() {
        val expected = "https://drive.usercontent.google.com/download?id=abc123&export=download&confirm=t"
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
            .url("https://drive.usercontent.google.com/download?id=abc123&export=download&confirm=t")
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
    fun preservesGoogleDriveInterceptorFailureInsteadOfCallingItInternetDisconnect() {
        val message = describeHttpPlaybackFailure(
            listOf(java.io.IOException("Google Drive mengirim halaman konfirmasi yang tidak dapat diproses: form tidak valid"))
        )
        org.junit.Assert.assertTrue(message.contains("Google Drive"))
        org.junit.Assert.assertFalse(message.contains("Periksa koneksi internet"))
    }

    @Test
    fun distinguishesSocketTimeoutFromDeviceInternetDisconnect() {
        val message = describeHttpPlaybackFailure(listOf(java.net.SocketTimeoutException("timeout")))
        org.junit.Assert.assertTrue(message.contains("tidak merespons"))
        org.junit.Assert.assertTrue(message.contains("belum tentu berarti koneksi internet perangkat terputus"))
    }

    @Test
    fun doesNotTreatNonDriveRequestsAsConfirmationCandidates() {
        val original = okhttp3.Request.Builder().url("https://example.com/video.mp4").build()
        assertEquals(null, googleDriveConfirmationRequestOrNull(original, "<html></html>"))
    }

    @Test
    fun acceptsDriveConfirmationFormWhenActionOmitsDownloadAndConfirmField() {
        val original = okhttp3.Request.Builder()
            .url("https://drive.usercontent.google.com/download?id=abc123&export=download")
            .header("Range", "bytes=0-")
            .build()
        val html = """
            <html><body>
              <form action="https://drive.usercontent.google.com/open" method="get">
                <input type="hidden" name="id" value="abc123">
                <input type="hidden" name="export" value="download">
                <input type="hidden" name="uuid" value="session-456">
              </form>
            </body></html>
        """.trimIndent()

        val resolved = buildGoogleDriveConfirmationRequest(original, html)
        assertEquals("drive.usercontent.google.com", resolved.url.host)
        assertEquals("abc123", resolved.url.queryParameter("id"))
        assertEquals("download", resolved.url.queryParameter("export"))
        assertEquals("session-456", resolved.url.queryParameter("uuid"))
        assertEquals("bytes=0-", resolved.header("Range"))
    }

    @Test
    fun acceptsGoogleDriveConfirmationLinkWithoutForm() {
        val original = okhttp3.Request.Builder()
            .url("https://drive.usercontent.google.com/download?id=abc123&export=download")
            .build()
        val html = """
            <html><body>
              <a href="https://drive.usercontent.google.com/download?id=abc123&export=download&confirm=token-789">
                Download
              </a>
            </body></html>
        """.trimIndent()

        val resolved = buildGoogleDriveConfirmationRequest(original, html)
        assertEquals("token-789", resolved.url.queryParameter("confirm"))
    }

    @Test
    fun rejectsConfirmationFormThatPostsToAnUnrelatedHost() {
        val original = okhttp3.Request.Builder()
            .url("https://drive.usercontent.google.com/download?id=abc123&export=download")
            .build()
        val html = """
            <html><body>
              <form action="https://example.com/download" method="get">
                <input type="hidden" name="id" value="abc123">
                <input type="hidden" name="export" value="download">
                <input type="hidden" name="confirm" value="token-789">
              </form>
            </body></html>
        """.trimIndent()

        assertEquals(null, googleDriveConfirmationRequestOrNull(original, html))
    }

    @Test
    fun resolvesGoogleDriveMetaRefreshConfirmationUrl() {
        val original = okhttp3.Request.Builder()
            .url("https://drive.usercontent.google.com/download?id=abc123&export=download")
            .build()
        val html = """
            <html><head>
              <meta http-equiv="refresh" content="0; url=/download?id=abc123&confirm=token-789">
            </head></html>
        """.trimIndent()

        val resolved = buildGoogleDriveConfirmationRequest(original, html)
        assertEquals("drive.usercontent.google.com", resolved.url.host)
        assertEquals("token-789", resolved.url.queryParameter("confirm"))
    }
}
