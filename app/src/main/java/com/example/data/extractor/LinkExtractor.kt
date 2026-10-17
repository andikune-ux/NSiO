package com.example.data.extractor

import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.StringReader
import java.net.URL
import java.net.URI
import java.util.UUID

sealed class ParseResult {
    data class Single(val item: MediaItem) : ParseResult()
    data class Multiple(val items: List<MediaItem>, val title: String) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

object LinkExtractor {

    private val directVideoExtensions = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".m4v")
    private val directPhotoExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

    /**
     * Parses a provided URL string to detect whether it is a single item,
     * an M3U playlist, CloudStream repository JSON, Google Drive link, or direct media stream.
     */
    suspend fun parseUrl(inputUrl: String, profileId: String): ParseResult = withContext(Dispatchers.IO) {
        val trimmed = inputUrl.trim()
        if (trimmed.isEmpty()) {
            return@withContext ParseResult.Error("URL tidak boleh kosong")
        }
        val parsedUri = try { URI(trimmed) } catch (_: Exception) { null }
        if (parsedUri?.scheme !in listOf("http", "https")) {
            return@withContext ParseResult.Error("URL harus diawali http:// atau https://")
        }

        try {
            // Case 1: Google Drive link
            if (trimmed.contains("drive.google.com")) {
                return@withContext parseGoogleDrive(trimmed, profileId)
            }

            // Case 2: Direct Video / Stream / Photo Extensions
            val lower = trimmed.lowercase()
            val hasVideoExt = directVideoExtensions.any { lower.contains(it) }
            val hasPhotoExt = directPhotoExtensions.any { lower.contains(it) }

            if (lower.contains(".m3u8") && !lower.contains(".m3u")) {
                // Direct HLS stream
                val item = MediaItem(
                    id = UUID.randomUUID().toString(),
                    profileId = profileId,
                    title = extractFilename(trimmed, "HLS Live Stream"),
                    url = trimmed,
                    thumbnailUrl = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=500",
                    type = "STREAM",
                    sourceType = "LINK",
                    category = "Live Stream"
                )
                return@withContext ParseResult.Single(item)
            }

            if (hasPhotoExt) {
                val item = MediaItem(
                    id = UUID.randomUUID().toString(),
                    profileId = profileId,
                    title = extractFilename(trimmed, "Foto"),
                    url = trimmed,
                    thumbnailUrl = trimmed,
                    type = "PHOTO",
                    sourceType = "LINK",
                    category = "Galeri Foto"
                )
                return@withContext ParseResult.Single(item)
            }

            if (hasVideoExt) {
                val item = MediaItem(
                    id = UUID.randomUUID().toString(),
                    profileId = profileId,
                    title = extractFilename(trimmed, "Video Langsung"),
                    url = trimmed,
                    thumbnailUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500",
                    type = "VIDEO",
                    sourceType = "LINK",
                    category = "Video Link"
                )
                return@withContext ParseResult.Single(item)
            }

            // Case 3: Fetch payload (could be M3U playlist, JSON CloudStream, or raw content)
            val content = try {
                URL(trimmed).readText()
            } catch (e: Exception) {
                // If fetch fails but looks like a direct link, treat as Single Stream
                val fallbackItem = MediaItem(
                    id = UUID.randomUUID().toString(),
                    profileId = profileId,
                    title = extractFilename(trimmed, "Media Stream"),
                    url = trimmed,
                    thumbnailUrl = "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=500",
                    type = "VIDEO",
                    sourceType = "LINK"
                )
                return@withContext ParseResult.Single(fallbackItem)
            }

            // Case 4: M3U playlist (contains #EXTM3U)
            if (content.contains("#EXTM3U") || trimmed.endsWith(".m3u", ignoreCase = true)) {
                val m3uItems = parseM3uContent(content, profileId)
                return@withContext if (m3uItems.size == 1) {
                    ParseResult.Single(m3uItems.first())
                } else if (m3uItems.isNotEmpty()) {
                    ParseResult.Multiple(m3uItems, "M3U Playlist (${m3uItems.size} Channel)")
                } else {
                    ParseResult.Error("M3U Playlist kosong atau tidak valid")
                }
            }

            // Case 5: CloudStream JSON Repo format or JSON Array/Object
            val trimmedContent = content.trim()
            if (trimmedContent.startsWith("{") || trimmedContent.startsWith("[")) {
                val jsonItems = parseJsonContent(trimmedContent, profileId)
                return@withContext if (jsonItems.size == 1) {
                    ParseResult.Single(jsonItems.first())
                } else if (jsonItems.isNotEmpty()) {
                    ParseResult.Multiple(jsonItems, "Koleksi Ekstensi JSON (${jsonItems.size} Item)")
                } else {
                    ParseResult.Error("JSON tidak berisi daftar media yang dapat diputar")
                }
            }

            // Default fallback: Single Video
            val fallback = MediaItem(
                id = UUID.randomUUID().toString(),
                profileId = profileId,
                title = extractFilename(trimmed, "Media Link"),
                url = trimmed,
                thumbnailUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=500",
                type = "VIDEO",
                sourceType = "LINK"
            )
            ParseResult.Single(fallback)
        } catch (e: Exception) {
            ParseResult.Error("Gagal mengurai link: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun parseGoogleDrive(url: String, profileId: String): ParseResult {
        // Check for file ID
            val fileId = when {
            url.contains("/file/d/") -> {
                url.substringAfter("/file/d/").substringBefore("/")
            }
            url.contains("id=") -> {
                url.substringAfter("id=").substringBefore("&")
            }
            url.contains("/folders/") -> {
                val folderId = url.substringAfter("/folders/").substringBefore("?")
                return ParseResult.Error("Folder Google Drive membutuhkan autentikasi API dan belum dapat diimpor tanpa izin pemilik (ID: $folderId)")
            }
            else -> ""
        }

        if (fileId.isNotEmpty()) {
            val streamUrl = "https://drive.google.com/uc?export=download&id=$fileId"
            val item = MediaItem(
                id = UUID.randomUUID().toString(),
                profileId = profileId,
                title = "Google Drive ($fileId)",
                url = streamUrl,
                thumbnailUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=500",
                type = "VIDEO",
                sourceType = "LINK",
                category = "Google Drive"
            )
            return ParseResult.Single(item)
        }

        return ParseResult.Error("URL Google Drive tidak dikenali")
    }

    private fun parseM3uContent(content: String, profileId: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val reader = BufferedReader(StringReader(content))
        var line: String?
        var currentTitle = "Saluran Live"
        var currentLogo = ""
        var currentGroup = "IPTV"

        while (reader.readLine().also { line = it } != null) {
            val l = line?.trim() ?: continue
            if (l.startsWith("#EXTINF:")) {
                // Parse tvg-name, tvg-logo, group-title, and display name
                if (l.contains("tvg-logo=\"")) {
                    currentLogo = l.substringAfter("tvg-logo=\"").substringBefore("\"")
                }
                if (l.contains("group-title=\"")) {
                    currentGroup = l.substringAfter("group-title=\"").substringBefore("\"")
                }
                currentTitle = l.substringAfterLast(",").trim()
                if (currentTitle.isEmpty()) currentTitle = "Saluran Live"
            } else if (l.startsWith("http://") || l.startsWith("https://")) {
                items.add(
                    MediaItem(
                        id = UUID.randomUUID().toString(),
                        profileId = profileId,
                        title = currentTitle,
                        url = l,
                        thumbnailUrl = currentLogo.ifEmpty { "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=500" },
                        type = if (l.contains(".m3u8")) "STREAM" else "VIDEO",
                        sourceType = "LINK",
                        category = currentGroup
                    )
                )
                // reset
                currentTitle = "Saluran Live"
                currentLogo = ""
            }
        }
        return items
    }

    private fun parseJsonContent(jsonStr: String, profileId: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        try {
            if (jsonStr.startsWith("[")) {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    collectJsonMedia(array.opt(i), profileId, items, 0)
                }
            } else {
                val obj = JSONObject(jsonStr)
                collectJsonMedia(obj, profileId, items, 0)
            }
        } catch (_: Exception) {}
        return items.distinctBy { it.url to it.title }
    }

    private fun collectJsonMedia(value: Any?, profileId: String, output: MutableList<MediaItem>, depth: Int) {
        if (value == null || depth > 5) return
        when (value) {
            is JSONObject -> {
                parseMediaFromJsonObj(value, profileId)?.let { output.add(it) }
                    ?: run {
                        val keys = value.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            collectJsonMedia(value.opt(key), profileId, output, depth + 1)
                        }
                    }
            }
            is JSONArray -> for (i in 0 until value.length()) {
                collectJsonMedia(value.opt(i), profileId, output, depth + 1)
            }
        }
    }

    private fun parseMediaFromJsonObj(obj: JSONObject, profileId: String): MediaItem? {
        val title = obj.optString("title", obj.optString("name", "")).trim()
        val url = obj.optString("url", obj.optString("videoUrl", obj.optString("link", ""))).trim()
        if (url.isEmpty()) return null
        val thumb = obj.optString("poster", obj.optString("thumbnail", obj.optString("icon", "")))
        val type = obj.optString("type", if (url.contains(".m3u8")) "STREAM" else "VIDEO")
        val category = extractCategory(obj)

        return MediaItem(
            id = UUID.randomUUID().toString(),
            profileId = profileId,
            title = if (title.isNotEmpty()) title else "Media Konten",
            url = url,
            thumbnailUrl = thumb.ifEmpty { "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=500" },
            type = type,
            sourceType = "LINK",
            category = category
        )
    }

    /**
     * Repository media tidak memiliki satu standar nama kategori. CloudStream,
     * IPTV dan katalog JSON umum memakai category/categories, genre/genres,
     * tags, group atau group-title. Semua bentuk tersebut dinormalisasi agar
     * dapat dipakai oleh filter kategori di beranda.
     */
    private fun extractCategory(obj: JSONObject): String {
        val values = mutableListOf<String>()
        val keys = listOf("category", "categories", "genre", "genres", "tags", "group", "group-title", "collection", "folder")
        keys.forEach { key ->
            val value = obj.opt(key) ?: return@forEach
            appendCategoryValue(value, values)
        }
        return values.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(" • ")
            .ifEmpty { "Tanpa Kategori" }
    }

    private fun appendCategoryValue(value: Any?, output: MutableList<String>) {
        when (value) {
            is JSONArray -> for (i in 0 until value.length()) appendCategoryValue(value.opt(i), output)
            is JSONObject -> {
                listOf("name", "title", "label", "value").firstNotNullOfOrNull { key ->
                    value.optString(key).takeIf { it.isNotBlank() }
                }?.let(output::add)
            }
            else -> value?.toString()?.split(",", "|", ";")?.forEach(output::add)
        }
    }

    private fun extractFilename(url: String, defaultName: String): String {
        return try {
            val name = url.substringAfterLast("/").substringBefore("?").replace("%20", " ")
            if (name.length > 2) name else defaultName
        } catch (_: Exception) {
            defaultName
        }
    }
}
