package com.example.player

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.io.IOException

/**
 * Resolves Google Drive's virus-scan/download confirmation interstitials.
 * The original response is never passed to Media3 when it is an HTML page.
 */
internal class DriveDownloadException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

private class DriveConfirmationFollowUp

internal class DriveDownloadInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!isDriveDownloadRequest(request.url.host, request.url.encodedPath)) return response

        val contentType = response.header("Content-Type").orEmpty()
        val looksLikeHtml = contentType.contains("text/html", ignoreCase = true) ||
            response.peekBody(16 * 1024L).string().trimStart().let {
                it.startsWith("<!doctype html", true) ||
                    it.startsWith("<html", true) ||
                    it.startsWith("<head", true)
            }
        if (!looksLikeHtml) return response

        if (request.tag(DriveConfirmationFollowUp::class.java) != null) {
            response.close()
            throw DriveDownloadException(
                "Google Drive masih mengirim halaman konfirmasi, bukan data video. Periksa izin berbagi file atau coba lagi beberapa saat."
            )
        }

        val html = runCatching { response.peekBody(512 * 1024L).string() }.getOrDefault("")
        val confirmationUrl = runCatching {
            resolveConfirmationUrl(request.url.toString(), html, response.headers.values("Set-Cookie"))
        }.getOrNull()

        if (confirmationUrl.isNullOrBlank()) {
            response.close()
            throw DriveDownloadException(
                "Google Drive mengirim halaman web/konfirmasi, bukan data video. NSiO tidak dapat menemukan tautan unduhan yang valid dari respons tersebut."
            )
        }

        val followUp = request.newBuilder()
            .url(confirmationUrl)
            .tag(DriveConfirmationFollowUp::class.java, DriveConfirmationFollowUp())
            .build()
        response.close()
        val resolved = chain.proceed(followUp)
        val resolvedType = resolved.header("Content-Type").orEmpty()
        val stillHtml = resolvedType.contains("text/html", ignoreCase = true) ||
            resolved.peekBody(16 * 1024L).string().trimStart().let {
                it.startsWith("<!doctype html", true) ||
                    it.startsWith("<html", true) ||
                    it.startsWith("<head", true)
            }
        if (stillHtml) {
            resolved.close()
            throw DriveDownloadException(
                "Konfirmasi Google Drive belum berhasil. Respons akhirnya masih berupa HTML, bukan video."
            )
        }
        return resolved
    }

    private fun isDriveDownloadRequest(host: String, path: String): Boolean {
        val driveHost = host.equals("drive.google.com", true) ||
            host.equals("drive.usercontent.google.com", true)
        return driveHost && (
            path.equals("/uc", true) ||
                path.equals("/download", true) ||
                path.startsWith("/file/d/", true)
            )
    }
}

/** Pure resolver kept internal so confirmation-page variants can be unit tested. */
internal fun resolveConfirmationUrl(
    requestUrl: String,
    html: String,
    setCookieHeaders: List<String> = emptyList()
): String? {
    val base = requestUrl.toHttpUrlOrNull() ?: return null
    val document = Jsoup.parse(html, requestUrl)

    // Some Drive pages expose a direct confirmation anchor.
    document.select("a[href]").firstNotNullOfOrNull { anchor ->
        val href = anchor.attr("abs:href")
        href.takeIf {
            it.isNotBlank() &&
                (it.contains("export=download", true) || it.contains("confirm=", true)) &&
                (it.contains("drive.google.com", true) || it.contains("usercontent.google.com", true))
        }
    }?.let { return it }

    // Larger files often use a form with hidden id/export/confirm fields.
    document.select("form[action]").forEach { form ->
        val action = form.attr("abs:action").ifBlank { form.attr("action") }
        val actionUrl = action.toHttpUrlOrNull() ?: return@forEach
        if (!(actionUrl.host.equals("drive.google.com", true) ||
                actionUrl.host.equals("drive.usercontent.google.com", true)) ||
            !(actionUrl.encodedPath.contains("download", true) || actionUrl.encodedPath.equals("/uc", true))
        ) return@forEach
        val builder = actionUrl.newBuilder()
        form.select("input[name]").forEach { input ->
            val name = input.attr("name")
            if (name.isNotBlank()) builder.setQueryParameter(name, input.attr("value"))
        }
        return builder.build().toString()
    }

    // Drive may issue a one-time download_warning cookie instead of a form token.
    val warningToken = setCookieHeaders.firstNotNullOfOrNull { header ->
        Regex("(?:^|[,;]\\s*)download_warning(?:_[^=;]+)?=([^;]+)")
            .find(header)?.groupValues?.getOrNull(1)
    }?.takeIf { it.isNotBlank() }
    if (warningToken != null) {
        return base.newBuilder().setQueryParameter("confirm", warningToken).build().toString()
    }

    return null
}
