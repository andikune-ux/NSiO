package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveDownloadInterceptorTest {
    @Test
    fun resolvesDriveConfirmationAnchor() {
        val html = """
            <html><body>
              <a id="uc-download-link" href="https://drive.google.com/uc?export=download&amp;confirm=abc123&amp;id=file_123">Download</a>
            </body></html>
        """.trimIndent()
        assertEquals(
            "https://drive.google.com/uc?export=download&confirm=abc123&id=file_123",
            resolveConfirmationUrl("https://drive.usercontent.google.com/download?id=file_123", html)
        )
    }

    @Test
    fun resolvesHiddenConfirmationFormFields() {
        val html = """
            <html><body>
              <form action="https://drive.usercontent.google.com/download" method="get">
                <input type="hidden" name="id" value="file_456">
                <input type="hidden" name="export" value="download">
                <input type="hidden" name="confirm" value="token456">
              </form>
            </body></html>
        """.trimIndent()
        assertEquals(
            "https://drive.usercontent.google.com/download?id=file_456&export=download&confirm=token456",
            resolveConfirmationUrl("https://drive.google.com/uc?id=file_456", html)
        )
    }

    @Test
    fun rejectsUnrecognizedDriveHtml() {
        assertNull(
            resolveConfirmationUrl(
                "https://drive.usercontent.google.com/download?id=file_789",
                "<html><body>Sign in to continue</body></html>"
            )
        )
    }
}
