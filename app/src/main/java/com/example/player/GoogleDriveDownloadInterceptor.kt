package com.example.player

import java.io.IOException
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

/**
 * Google Drive can return an HTML interstitial instead of media bytes.
 * Resolve its confirmation form/link with the same OkHttp client so cookies,
 * redirect handling and the original playback headers remain available.
 */
internal class GoogleDriveDownloadInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isGoogleDriveDownloadRequest(request)) return chain.proceed(request)

        val response = chain.proceed(request)
        if (!isHtmlResponse(response)) return response

        val html = try {
            response.body?.string().orEmpty()
        } finally {
            response.close()
        }
        val confirmationRequest = try {
            buildGoogleDriveConfirmationRequest(request, html)
        } catch (error: Exception) {
            val page = describeGoogleDriveHtmlPage(html)
            throw IOException(
                "Google Drive mengirim halaman HTML, tetapi tautan/form konfirmasi tidak dikenali. $page",
                error
            )
        }

        val confirmedResponse = chain.proceed(confirmationRequest)
        if (isHtmlResponse(confirmedResponse)) {
            val confirmedHtml = try {
                confirmedResponse.peekBody(16 * 1024).string()
            } catch (_: Exception) {
                ""
            }
            confirmedResponse.close()
            throw IOException(
                "Google Drive masih mengirim halaman web, bukan data video. " +
                    describeGoogleDriveHtmlPage(confirmedHtml)
            )
        }
        return confirmedResponse
    }
}

private fun isHtmlResponse(response: Response): Boolean {
    val contentType = response.body?.contentType()?.toString().orEmpty()
    if (contentType.contains("text/html", ignoreCase = true) ||
        contentType.contains("application/xhtml", ignoreCase = true)
    ) return true

    // Some Drive edge responses omit or mislabel Content-Type. Peek without
    // consuming the body so Media3 can still read the original media stream.
    val prefix = runCatching { response.peekBody(16 * 1024).string() }.getOrDefault("")
        .trimStart()
    return prefix.startsWith("<!doctype html", ignoreCase = true) ||
        prefix.startsWith("<html", ignoreCase = true)
}

private fun describeGoogleDriveHtmlPage(html: String): String {
    if (html.isBlank()) return "Respons HTML tidak berisi detail yang dapat dibaca."
    val document = Jsoup.parse(html)
    val title = document.title().replace(Regex("\\s+"), " ").trim().take(100)
    val detail = document.body()?.text()
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(180)
        .orEmpty()
    return buildString {
        if (title.isNotBlank()) append("Judul respons: ").append(title).append(". ")
        if (detail.isNotBlank()) append("Detail: ").append(detail)
    }.ifBlank { "Google Drive mengembalikan halaman HTML, bukan data video." }
}

internal fun isGoogleDriveDownloadRequest(request: Request): Boolean =
    isGoogleDriveHost(request.url.host)

private fun isGoogleDriveHost(host: String): Boolean {
    val normalized = host.lowercase()
    return normalized == "drive.google.com" ||
        normalized == "drive.usercontent.google.com"
}

internal fun googleDriveConfirmationRequestOrNull(request: Request, html: String): Request? {
    if (!isGoogleDriveDownloadRequest(request)) return null
    val document = Jsoup.parse(html, request.url.toString())

    val formRequest = document.select("form").firstNotNullOfOrNull { form ->
        val fields = buildList {
            form.select("input[name]").forEach { input ->
                val type = input.attr("type").lowercase()
                if (type in setOf("submit", "button", "image", "reset", "file")) return@forEach
                if (type in setOf("checkbox", "radio") && !input.hasAttr("checked")) return@forEach
                val name = input.attr("name").trim()
                if (name.isNotEmpty()) add(name to input.attr("value"))
            }
            form.select("select[name]").forEach { select ->
                val name = select.attr("name").trim()
                val option = select.selectFirst("option[selected]") ?: select.selectFirst("option")
                if (name.isNotEmpty() && option != null) add(name to option.attr("value"))
            }
            form.select("textarea[name]").forEach { area ->
                val name = area.attr("name").trim()
                if (name.isNotEmpty()) add(name to area.text())
            }
        }
        if (fields.isEmpty()) return@firstNotNullOfOrNull null

        val action = form.absUrl("action").takeIf { it.isNotBlank() } ?: request.url.toString()
        val actionUrl = runCatching { action.toHttpUrl() }.getOrNull()
            ?: return@firstNotNullOfOrNull null
        if (!isGoogleDriveHost(actionUrl.host)) return@firstNotNullOfOrNull null

        val fieldNames = fields.map { it.first.lowercase() }.toSet()
        val hasDriveDownloadSignals =
            actionUrl.encodedPath.contains("download", ignoreCase = true) ||
                actionUrl.encodedPath.equals("/uc", ignoreCase = true) ||
                form.id().contains("download", ignoreCase = true) ||
                "confirm" in fieldNames ||
                ("id" in fieldNames && "export" in fieldNames)
        if (!hasDriveDownloadSignals) return@firstNotNullOfOrNull null

        val builder = request.newBuilder().url(actionUrl)
        val method = form.attr("method").ifBlank { "get" }.uppercase()
        if (method == "POST") {
            val body = FormBody.Builder().apply {
                fields.forEach { (name, value) -> add(name, value) }
            }.build()
            builder.method("POST", body).build()
        } else {
            val target = actionUrl.newBuilder()
            fields.forEach { (name, value) ->
                target.removeAllQueryParameters(name).addQueryParameter(name, value)
            }
            builder.url(target.build()).get().build()
        }
    }
    if (formRequest != null) return formRequest

    // Some Drive responses expose a one-time confirmation URL as a link.
    val confirmationLink = document.select("a[href]").firstNotNullOfOrNull { link ->
        val href = link.absUrl("href")
        val target = runCatching { href.toHttpUrl() }.getOrNull() ?: return@firstNotNullOfOrNull null
        if (isGoogleDriveHost(target.host) && target.queryParameter("confirm") != null) href else null
    }
    if (!confirmationLink.isNullOrBlank()) {
        return request.newBuilder().url(confirmationLink).get().build()
    }

    // A few variants use a meta-refresh instead of a visible anchor.
    val refresh = document.select("meta[http-equiv=refresh]").firstNotNullOfOrNull { meta ->
        Regex("(?i)url\\s*=\\s*['\\\"]?([^'\\\";]+)")
            .find(meta.attr("content"))?.groupValues?.getOrNull(1)?.trim()
    }
    val refreshUrl = refresh?.let { runCatching { request.url.resolve(it) }.getOrNull() }
    if (refreshUrl != null && isGoogleDriveHost(refreshUrl.host)) {
        return request.newBuilder().url(refreshUrl).get().build()
    }
    return null
}

internal fun buildGoogleDriveConfirmationRequest(request: Request, html: String): Request =
    googleDriveConfirmationRequestOrNull(request, html)
        ?: throw IOException("Tidak ditemukan form atau tautan konfirmasi unduhan Google Drive yang valid")
