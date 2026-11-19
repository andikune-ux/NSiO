package com.example.embedded.modules.ddtv.server

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale

/**
 * DDTV payloads have changed field names between server revisions. Keep
 * catalog/detail parsing tolerant without guessing playback URLs.
 */
object DdtvContentFields {
    private val imageKeys = setOf(
        "cover_url", "cover", "cover_img", "cover_image", "cover_pic",
        "thumbnail", "thumbnail_url", "thumb", "thumb_url", "thumb_img",
        "pic", "pic_url", "pic_thumb", "vod_pic", "vod_pic_thumb",
        "image", "image_url", "img", "img_url", "poster", "poster_url",
        "photo", "photo_url", "banner", "banner_url", "preview",
        "preview_url", "picture", "picture_url", "snapshot", "snapshot_url"
    )
    private val imageContainerKeys = setOf("data", "result", "info", "vod", "video", "item", "images", "covers")
    private val imageBaseKeys = listOf("image_domain", "img_domain", "pic_domain", "cdn", "domain", "host", "base_url")

    fun id(video: JSONObject): Long {
        return sequenceOf("id", "video_id", "vod_id", "videoId", "vodId")
            .mapNotNull { key -> video.opt(key)?.toString()?.toLongOrNull() }
            .firstOrNull { it > 0L } ?: 0L
    }

    fun title(video: JSONObject, fallbackId: Long = id(video)): String {
        return sequenceOf("title", "name", "video_name", "vod_name", "vod_title", "movie_name", "resource_name")
            .map { video.optString(it, "").trim() }
            .firstOrNull { it.isNotBlank() && it != "null" }
            ?: "DDTV • Video $fallbackId"
    }

    fun imageUrl(vararg sources: JSONObject?): String {
        for (source in sources.filterNotNull()) {
            val raw = findImage(source, 0) ?: continue
            normalizeImageUrl(raw, source)?.let { return it }
        }
        return ""
    }

    fun metadataValue(video: JSONObject, detail: JSONObject?, vararg keys: String): String {
        for (source in listOfNotNull(detail, video)) {
            for (key in keys) {
                val value = source.opt(key) ?: continue
                if (value == JSONObject.NULL) continue
                val text = when (value) {
                    is JSONArray -> (0 until value.length()).mapNotNull { value.opt(it)?.toString() }
                        .filter { it.isNotBlank() && it != "null" }.joinToString(" • ")
                    is JSONObject -> sequenceOf("name", "title", "value", "label")
                        .map { value.optString(it, "").trim() }
                        .firstOrNull { it.isNotBlank() }.orEmpty()
                    else -> value.toString().trim()
                }
                if (text.isNotBlank() && text != "null") return text
            }
        }
        return ""
    }

    private fun findImage(value: Any?, depth: Int): String? {
        if (depth > 6) return null
        return when (value) {
            is JSONObject -> {
                val keys = value.keys()
                val entries = mutableListOf<Pair<String, Any?>>()
                while (keys.hasNext()) {
                    val key = keys.next()
                    entries += key.lowercase(Locale.ROOT) to value.opt(key)
                }
                entries.firstNotNullOfOrNull { (key, child) ->
                    if (key in imageKeys) candidateString(child, depth + 1)
                    else null
                } ?: entries.firstNotNullOfOrNull { (key, child) ->
                    if (child is JSONObject || child is JSONArray || key in imageContainerKeys) {
                        findImage(child, depth + 1)
                    } else null
                }
            }
            is JSONArray -> (0 until minOf(value.length(), 32))
                .firstNotNullOfOrNull { findImage(value.opt(it), depth + 1) }
            else -> null
        }
    }

    private fun candidateString(value: Any?, depth: Int): String? {
        if (depth > 6) return null
        return when (value) {
            is String -> value.trim().takeIf { it.isNotBlank() && it != "null" }
            is JSONObject -> {
                val keys = value.keys()
                val preferred = listOf("url", "src", "path", "image", "pic", "img", "value")
                preferred.firstNotNullOfOrNull { key ->
                    if (value.has(key)) candidateString(value.opt(key), depth + 1) else null
                } ?: run {
                    var found: String? = null
                    while (keys.hasNext() && found == null) found = candidateString(value.opt(keys.next()), depth + 1)
                    found
                }
            }
            is JSONArray -> (0 until minOf(value.length(), 16))
                .firstNotNullOfOrNull { candidateString(value.opt(it), depth + 1) }
            else -> null
        }
    }

    private fun normalizeImageUrl(raw: String, source: JSONObject): String? {
        val value = raw.replace("&amp;", "&").trim()
        if (value.length > 8_192 || value.contains('\\n') || value.contains('\\r')) return null
        val absolute = when {
            value.startsWith("https://", true) -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://", true) -> return null
            else -> {
                val base = imageBase(source) ?: return null
                runCatching { URI(base).resolve(value).toString() }.getOrNull() ?: return null
            }
        }
        return absolute.takeIf(DdtvServerUrlPolicy::isPublicHttps)
    }

    private fun imageBase(source: JSONObject): String? {
        for (key in imageBaseKeys) {
            val raw = source.optString(key, "").trim()
            if (raw.isBlank()) continue
            val candidate = if (raw.startsWith("https://", true)) raw else "https://$raw"
            runCatching { DdtvServerUrlPolicy.origin(candidate) }.getOrNull()?.let { return it }
        }
        val domains = source.optJSONArray("domains")
        if (domains != null) {
            for (index in 0 until minOf(domains.length(), 16)) {
                val raw = domains.optString(index, "").trim()
                val candidate = if (raw.startsWith("https://", true)) raw else "https://$raw"
                runCatching { DdtvServerUrlPolicy.origin(candidate) }.getOrNull()?.let { return it }
            }
        }
        return null
    }
}
