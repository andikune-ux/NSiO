package com.example.player

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DriveDownloadInterceptorTest {
    @Test
    fun resolvesHiddenDriveConfirmationFormFields() {
        val html = """
            <html><body>
              <form action="https://drive.usercontent.google.com/download" method="get">
                <input type="hidden" name="id" value="file_123">
                <input type="hidden" name="export" value="download">
                <input type="hidden" name="confirm" value="confirm_456">
                <input type="hidden" name="uuid" value="uuid_789">
              </form>
            </body></html>
        """.trimIndent()

        val resolved = resolveConfirmationUrl(
            "https://drive.google.com/uc?export=download&id=file_123".toHttpUrl(),
            html
        )

        assertNotNull(resolved)
        assertEquals("file_123", resolved?.queryParameter("id"))
        assertEquals("download", resolved?.queryParameter("export"))
        assertEquals("confirm_456", resolved?.queryParameter("confirm"))
        assertEquals("uuid_789", resolved?.queryParameter("uuid"))
    }

    @Test
    fun extractsDriveWarningCookieAndUsesItsOneTimeToken() {
        val cookies = extractWarningCookies(
            listOf("download_warning_abc=token_123; Path=/; Secure; HttpOnly")
        )
        assertEquals(listOf("download_warning_abc" to "token_123"), cookies)

        val resolved = resolveConfirmationUrl(
            "https://drive.google.com/uc?export=download&id=file_123".toHttpUrl(),
            "<html><body>Download warning</body></html>",
            cookies
        )
        assertEquals("token_123", resolved?.queryParameter("confirm"))
    }

    @Test
    fun doesNotResolveConfirmationLinksOnUntrustedHosts() {
        val html = """
            <html><body>
              <form action="https://evil.example/download">
                <input name="confirm" value="bad">
              </form>
              <a href="https://evil.example/download?export=download&confirm=bad">download</a>
            </body></html>
        """.trimIndent()

        assertNull(
            resolveConfirmationUrl(
                "https://drive.google.com/uc?export=download&id=file_123".toHttpUrl(),
                html
            )
        )
    }
    @Test
    fun resolvesLegacyGoogleusercontentDownloadHost() {
        val html = """
            <html><body>
              <form action="https://drive.googleusercontent.com/uc" method="get">
                <input type="hidden" name="id" value="legacy_file">
                <input type="hidden" name="export" value="download">
                <input type="hidden" name="confirm" value="legacy_token">
              </form>
            </body></html>
        """.trimIndent()

        val resolved = resolveConfirmationUrl(
            "https://drive.googleusercontent.com/uc?id=legacy_file".toHttpUrl(),
            html
        )

        assertEquals("legacy_file", resolved?.queryParameter("id"))
        assertEquals("legacy_token", resolved?.queryParameter("confirm"))
    }

}
