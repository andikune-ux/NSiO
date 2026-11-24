package com.example.player

import java.io.IOException
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

/**
 * Google Drive sometimes returns a small HTML interstitial instead of media bytes.
 * Its download form carries a short-lived confirmation token and session cookie.
 * This interceptor submits that form using the same OkHttp client/cookie jar used
 * by playback, then lets Media3 consume the actual media response.
 */
internal class GoogleDriveDownloadInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isGoogleDriveDownloadRequest(request)) return chain.proceed(request)

        val response = chain.proceed(request)
        val contentType = response.body?.contentType()?.toString().orEmpty()
        val isHtml = contentType.contains("text/html", ignoreCase = true) ||
            contentType.contains("application/xhtml", ignoreCase = true)
        if (!isHtml) return response

        val html = try {
            response.body?.string().orEmpty()
        } finally {
            response.close()
        }
        val confirmationRequest = try {
            buildGoogleDriveConfirmationRequest(request, html)
        } catch (error: Exception) {
            throw IOException("Google Drive mengirim halaman konfirmasi yang tidak dapat diproses: ${error.message ?: "format form tidak valid"}. Pastikan file dapat diakses oleh siapa saja yang memiliki link.", error)
        }
        val confirmedResponse = chain.proceed(confirmationRequest)
        val confirmedType = confirmedResponse.body?.contentType()?.toString().orEmpty()
        if (confirmedType.contains("text/html", ignoreCase = true) ||
            confirmedType.contains("application/xhtml", ignoreCase = true)) {
            confirmedResponse.close()
            throw IOException("Google Drive masih mengirim halaman web, bukan data video. Periksa izin berbagi file, batas unduhan Google Drive, atau apakah file memerlukan login.")
        }
        return confirmedResponse
    }
}

internal fun isGoogleDriveDownloadRequest(request: Request): Boolean {
    val host = request.url.host.lowercase()
    return host == "drive.google.com" || host == "drive.usercontent.google.com"
}

internal fun googleDriveConfirmationRequestOrNull(request: Request, html: String): Request? {
    if (!isGoogleDriveDownloadRequest(request)) return null
    val document = Jsoup.parse(html, request.url.toString())
    val form = document.select("form").firstOrNull { candidate ->
        candidate.select("input[name]").isNotEmpty() &&
            (candidate.attr("action").contains("download", ignoreCase = true) ||
                candidate.id().contains("download", ignoreCase = true) ||
                candidate.select("input[name=confirm]").isNotEmpty())
    }

    if (form != null) {
        val action = form.absUrl("action").takeIf { it.isNotBlank() } ?: request.url.toString()
        val method = form.attr("method").ifBlank { "get" }.uppercase()
        val fields = form.select("input[name]").mapNotNull { input ->
            val name = input.attr("name")
            if (name.isBlank()) null else name to input.attr("value")
        }
        val builder = request.newBuilder().url(action)
        return if (method == "POST") {
            val body = FormBody.Builder().apply {
                fields.forEach { (name, value) -> add(name, value) }
            }.build()
            builder.method("POST", body).build()
        } else {
            val urlBuilder = request.url.newBuilder()
            val actionUrl = okhttp3.HttpUrl.get(action)
            val target = actionUrl.newBuilder()
            fields.forEach { (name, value) ->
                target.removeAllQueryParameters(name).addQueryParameter(name, value)
            }
            builder.url(target.build()).get().build()
        }
    }

    // Some Drive variants expose a direct confirmation URL instead of a form.
    val confirmationLink = document.select("a[href]").firstOrNull { link ->
        val href = link.absUrl("href")
        href.contains("confirm=", ignoreCase = true) &&
            (href.contains("download", ignoreCase = true) || href.contains("usercontent.google.com", ignoreCase = true))
    }?.absUrl("href")?.takeIf { it.isNotBlank() }
    return confirmationLink?.let { request.newBuilder().url(it).get().build() }
}

internal fun buildGoogleDriveConfirmationRequest(request: Request, html: String): Request =
    googleDriveConfirmationRequestOrNull(request, html)
        ?: throw IOException("Form konfirmasi unduhan Google Drive tidak ditemukan")
