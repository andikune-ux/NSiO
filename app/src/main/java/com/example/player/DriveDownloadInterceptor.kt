package com.example.player

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.io.IOException

internal class DriveDownloadException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

private class DriveConfirmationFollowUp

/**
 * Handles Drive's virus-scan/large-file interstitial before Media3 sees the body.
 * It is deliberately limited to Drive download endpoints; other providers are untouched.
 */
internal class DriveDownloadInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!isDriveDownloadRequest(request.url.host, request.url.encodedPath)) return response
        if (!looksLikeHtml(response)) return response

        if (request.tag(DriveConfirmationFollowUp::class.java) != null) {
            response.close()
            throw DriveDownloadException(
                "Google Drive masih mengirim halaman konfirmasi, bukan data video. Periksa izin berbagi file atau coba lagi."
            )
        }

        val html = runCatching { response.peekBody(512 * 1024L).string() }.getOrDefault("")
        val finalUrl = response.request.url
        val cookies = extractWarningCookies(response.headers.values("Set-Cookie"))
        val followUpUrl = runCatching {
            resolveConfirmationUrl(finalUrl, html, cookies)
        }.getOrNull()

        if (followUpUrl == null) {
            response.close()
            throw DriveDownloadException(
                "Google Drive mengirim halaman web/konfirmasi, bukan data video. Tautan konfirmasi unduhan tidak ditemukan."
            )
        }

        val followUp = request.newBuilder()
            .url(followUpUrl)
            .tag(DriveConfirmationFollowUp::class.java, DriveConfirmationFollowUp())
            .apply {
                if (cookies.isNotEmpty()) header("Cookie", cookies.joinToString("; ") { "${it.first}=${it.second}" })
            }
            .build()
        response.close()

        val resolved = chain.proceed(followUp)
        if (looksLikeHtml(resolved)) {
            resolved.close()
            throw DriveDownloadException(
                "Konfirmasi Google Drive belum berhasil. Server masih mengirim halaman HTML, bukan data video."
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

    private fun looksLikeHtml(response: Response): Boolean {
        val contentType = response.header("Content-Type").orEmpty()
        if (contentType.contains("text/html", ignoreCase = true)) return true
        return response.peekBody(16 * 1024L).string().trimStart().let {
            it.startsWith("<!doctype html", true) ||
                it.startsWith("<html", true) ||
                it.startsWith("<head", true)
        }
    }
}

/** Pure helpers are internal so Drive confirmation variants can be unit tested. */
internal fun extractWarningCookies(setCookieHeaders: List<String>): List<Pair<String, String>> {
    val pattern = Regex("(?:^|;\\s*)(download_warning(?:_[^=;]+)?)=([^;]+)", RegexOption.IGNORE_CASE)
    return setCookieHeaders.mapNotNull { header ->
        pattern.find(header)?.let { match ->
            match.groupValues[1] to match.groupValues[2]
        }
    }.distinctBy { it.first }
}

internal fun resolveConfirmationUrl(
    requestUrl: HttpUrl,
    html: String,
    warningCookies: List<Pair<String, String>> = emptyList()
): HttpUrl? {
    val document = Jsoup.parse(html, requestUrl.toString())

    document.select("form[action]").forEach { form ->
        val action = form.attr("abs:action").ifBlank { requestUrl.toString() }
        val actionUrl = action.toHttpUrlOrNull() ?: return@forEach
        if (!isDriveHost(actionUrl.host) ||
            !(actionUrl.encodedPath.contains("download", true) || actionUrl.encodedPath.equals("/uc", true))
        ) return@forEach

        val builder = actionUrl.newBuilder()
        form.select("input[name]").forEach { input ->
            val name = input.attr("name")
            if (name.isNotBlank()) builder.setQueryParameter(name, input.attr("value"))
        }
        if (warningCookies.isNotEmpty() && builder.build().queryParameter("confirm").isNullOrBlank()) {
            builder.setQueryParameter("confirm", warningCookies.first().second)
        }
        return builder.build()
    }

    document.select("a[href]").forEach { anchor ->
        val href = anchor.attr("abs:href").toHttpUrlOrNull() ?: return@forEach
        if (isDriveHost(href.host) &&
            (href.queryParameter("export") == "download" || !href.queryParameter("confirm").isNullOrBlank())
        ) return href
    }

    if (warningCookies.isNotEmpty() && isDriveHost(requestUrl.host)) {
        return requestUrl.newBuilder()
            .setQueryParameter("confirm", warningCookies.first().second)
            .build()
    }
    return null
}

private fun isDriveHost(host: String): Boolean =
    host.equals("drive.google.com", true) ||
        host.equals("drive.usercontent.google.com", true)
