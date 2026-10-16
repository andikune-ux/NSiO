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
                    val obj = array.optJSONObject(i) ?: continue
                    parseMediaFromJsonObj(obj, profileId)?.let { items.add(it) }
                }
            } else {
                val obj = JSONObject(jsonStr)
                // CloudStream Repo could have "pluginLists" or "items" or "data"
                val listKeys = listOf("items", "videos", "media", "data", "plugins", "sources")
                for (key in listKeys) {
                    if (obj.has(key)) {
                        val arr = obj.optJSONArray(key)
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val itemObj = arr.optJSONObject(i) ?: continue
                                parseMediaFromJsonObj(itemObj, profileId)?.let { items.add(it) }
                            }
                            if (items.isNotEmpty()) return items
                        }
                    }
                }
                // Try as single item
                parseMediaFromJsonObj(obj, profileId)?.let { items.add(it) }
            }
        } catch (_: Exception) {}
        return items
    }

    private fun parseMediaFromJsonObj(obj: JSONObject, profileId: String): MediaItem? {
        val title = obj.optString("title", obj.optString("name", "")).trim()
        val url = obj.optString("url", obj.optString("videoUrl", obj.optString("link", ""))).trim()
        if (url.isEmpty()) return null
        val thumb = obj.optString("poster", obj.optString("thumbnail", obj.optString("icon", "")))
        val type = obj.optString("type", if (url.contains(".m3u8")) "STREAM" else "VIDEO")

        return MediaItem(
            id = UUID.randomUUID().toString(),
            profileId = profileId,
            title = if (title.isNotEmpty()) title else "Media Konten",
            url = url,
            thumbnailUrl = thumb.ifEmpty { "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=500" },
            type = type,
            sourceType = "LINK",
            category = obj.optString("category", "Ekstensi")
        )
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
