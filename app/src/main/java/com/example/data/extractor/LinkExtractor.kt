package com.example.data.extractor

import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class ParseResult {
    data class Single(val item: MediaItem) : ParseResult()
    data class Multiple(val items: List<MediaItem>, val title: String) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

object LinkExtractor {

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

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

            // Case 1b: Public GitHub repositories containing M3U/JSON catalogs.
            if (parsedUri?.host.equals("github.com", ignoreCase = true)) {
                val parts = parsedUri?.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
                if (parts.size >= 2 && parts[0] !in setOf("login", "settings", "topics")) {
                    return@withContext parseGithubRepository(parts[0], parts[1], profileId)
                }
            }

            // Case 2: Direct Video / Stream / Photo Extensions
            val lower = trimmed.lowercase()
            val hasVideoExt = directVideoExtensions.any { lower.contains(it) }
            val hasPhotoExt = directPhotoExtensions.any { lower.contains(it) }

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
                fetchText(trimmed)
            } catch (e: Exception) {
                if (lower.substringBefore('?').contains(".m3u")) {
                    return@withContext ParseResult.Error("Gagal mengambil playlist dari server: ${e.localizedMessage ?: "respons tidak dapat dibaca"}")
                }
                // If fetch fails but looks like a direct link, treat as Single Stream
                val fallbackItem = MediaItem(
                    id = UUID.randomUUID().toString(),
                    profileId = profileId,
                    title = extractFilename(trimmed, "Media Stream"),
                    url = trimmed,
                    thumbnailUrl = "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=500",
                    type = if (lower.contains(".m3u8")) "STREAM" else "VIDEO",
                    sourceType = "LINK"
                )
                return@withContext ParseResult.Single(fallbackItem)
            }

            // Case 4: M3U playlist (contains #EXTM3U)
            if (Regex("#EXTM3U|#EXTINF:", RegexOption.IGNORE_CASE).containsMatchIn(content) || trimmed.substringBefore('?').endsWith(".m3u", ignoreCase = true) || trimmed.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                val isHlsSegmentPlaylist = trimmed.substringBefore('?').endsWith(".m3u8", ignoreCase = true) &&
                    listOf("#EXT-X-TARGETDURATION", "#EXT-X-MEDIA-SEQUENCE", "#EXT-X-STREAM-INF")
                        .any { content.contains(it, ignoreCase = true) }
                if (isHlsSegmentPlaylist) {
                    return@withContext ParseResult.Single(
                        MediaItem(
                            id = UUID.randomUUID().toString(), profileId = profileId,
                            title = extractFilename(trimmed, "HLS Live Stream"), url = trimmed,
                            thumbnailUrl = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=500",
                            type = "STREAM", sourceType = "LINK", category = "Live Stream"
                        )
                    )
                }
                val m3uItems = parseM3uContent(content, profileId, trimmed)
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
                return parsePublicDriveFolder(folderId, profileId)
            }
            else -> ""
        }

        if (fileId.isNotEmpty()) {
            val metadata = fetchDriveMetadata(fileId)
            val streamUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download"
            val thumbnailUrl = "https://lh3.googleusercontent.com/d/$fileId=w1200"
            val mime = metadata.first
            val title = metadata.second.ifBlank { "Google Drive ($fileId)" }
            val item = MediaItem(
                id = UUID.randomUUID().toString(),
                profileId = profileId,
                title = title,
                url = streamUrl,
                    thumbnailUrl = thumbnailUrl,
                type = if (mime.startsWith("image/")) "PHOTO" else "VIDEO",
                sourceType = "LINK",
                category = "Google Drive"
            )
            return ParseResult.Single(item)
        }

        return ParseResult.Error("URL Google Drive tidak dikenali")
    }

    private fun parseGithubRepository(owner: String, repository: String, profileId: String): ParseResult {
        return try {
            val repoJson = JSONObject(fetchText("https://api.github.com/repos/$owner/$repository"))
            val branch = repoJson.optString("default_branch", "main")
            val tree = JSONObject(fetchText("https://api.github.com/repos/$owner/$repository/git/trees/$branch?recursive=1"))
                .optJSONArray("tree") ?: JSONArray()
            val paths = (0 until tree.length()).mapNotNull { index ->
                tree.optJSONObject(index)?.takeIf { it.optString("type") == "blob" }?.optString("path")
            }.filter { path ->
                val lower = path.lowercase()
                lower.endsWith(".m3u") || lower.endsWith(".m3u8") || lower.endsWith(".json") || lower.contains("playlist")
            }.take(32)

            val items = paths.flatMap { path ->
                val encodedPath = path.split('/').joinToString("/") { it.replace(" ", "%20") }
                val rawUrl = "https://raw.githubusercontent.com/$owner/$repository/$branch/$encodedPath"
                val payload = runCatching { fetchText(rawUrl) }.getOrDefault("")
                if (payload.contains("#EXTM3U", ignoreCase = true) || payload.contains("#EXTINF:", ignoreCase = true)) {
                    parseM3uContent(payload, profileId, rawUrl)
                } else emptyList()
            }.distinctBy { it.url to it.title }

            if (items.isNotEmpty()) {
                ParseResult.Multiple(items, "GitHub Playlist ($owner/$repository)")
            } else {
                val hasCloudStreamManifest = paths.any { it.substringAfterLast('/').equals("repo.json", ignoreCase = true) }
                if (hasCloudStreamManifest) {
                    ParseResult.Error("Repository CloudStream terdeteksi. Tambahkan URL repo.json pada tab Extension Repository; plugin CloudStream membutuhkan runtime plugin dan bukan daftar video M3U langsung.")
                } else {
                    ParseResult.Error("Repository GitHub tidak berisi playlist M3U yang dapat diurai")
                }
            }
        } catch (e: Exception) {
            ParseResult.Error("Gagal membaca repository GitHub: ${e.localizedMessage ?: "akses API GitHub gagal"}")
        }
    }

    private fun parsePublicDriveFolder(folderId: String, profileId: String): ParseResult {
        return try {
            val html = fetchText("https://drive.google.com/embeddedfolderview?id=$folderId")
            val regex = Regex("id=\\\"entry-([^\\\"]+)\\\".*?href=\\\"https://drive.google.com/file/d/([^/]+)/view[^\\\"]*\\\".*?flip-entry-title\\\">(.*?)</div>", setOf(RegexOption.DOT_MATCHES_ALL))
            val items = regex.findAll(html).mapNotNull { match ->
                val id = match.groupValues[2]
                val name = match.groupValues[3].replace(Regex("<[^>]+>"), "").trim()
                val mime = when {
                    name.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp)$", RegexOption.IGNORE_CASE)) -> "image/*"
                    name.matches(Regex(".*\\.(mp4|mkv|webm|mov|avi|m4v)$", RegexOption.IGNORE_CASE)) -> "video/*"
                    else -> "application/octet-stream"
                }
                MediaItem(
                    id = UUID.randomUUID().toString(), profileId = profileId, title = name,
                    url = "https://drive.usercontent.google.com/download?id=$id&export=download",
                    thumbnailUrl = "https://lh3.googleusercontent.com/d/$id=w800",
                    type = if (mime.startsWith("image/")) "PHOTO" else "VIDEO",
                    sourceType = "LINK", category = "Google Drive"
                )
            }.distinctBy { it.title to it.url }.toList()
            if (items.isEmpty()) ParseResult.Error("Folder Google Drive kosong, privat, atau tidak dapat dibaca. Pastikan aksesnya 'Siapa saja yang memiliki link'.")
            else ParseResult.Multiple(items, "Google Drive Folder")
        } catch (e: Exception) {
            ParseResult.Error("Gagal membaca folder Google Drive publik: ${e.localizedMessage ?: "akses ditolak"}")
        }
    }

    private fun fetchDriveMetadata(fileId: String): Pair<String, String> = runCatching {
        val html = fetchText("https://drive.google.com/file/d/$fileId/view?usp=sharing")
        val mime = when {
            html.contains("video/", ignoreCase = true) -> "video/*"
            html.contains("image/", ignoreCase = true) -> "image/*"
            else -> ""
        }
        val name = Regex("property=\\\"og:title\\\" content=\\\"([^\\\"]+)", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1).orEmpty()
        mime to name.replace("\\u0026", "&")
    }.getOrDefault("" to "")

    private fun fetchText(address: String): String {
        val request = Request.Builder()
            .url(address)
            .header("User-Agent", "Mozilla/5.0 (Android) NSiO/2.0")
            .header("Accept", "application/vnd.apple.mpegurl, audio/x-mpegurl, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun parseM3uContent(content: String, profileId: String, playlistUrl: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val reader = BufferedReader(StringReader(content))
        var line: String?
        var currentTitle = "Saluran Live"
        var currentLogo = ""
        var currentGroup = "IPTV"
        val currentHeaders = linkedMapOf<String, String>()

        while (reader.readLine().also { line = it } != null) {
            val l = line?.trim() ?: continue
            if (l.startsWith("#EXTINF:", ignoreCase = true)) {
                // Parse tvg-name, tvg-logo, group-title, and display name
                currentLogo = listOf("tvg-logo", "logo", "channel-logo").firstNotNullOfOrNull { attribute ->
                    Regex("$attribute\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
                        .find(l)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                }.orEmpty()
                currentGroup = Regex("group-title\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
                    .find(l)?.groupValues?.get(1)?.ifBlank { "IPTV" } ?: "IPTV"
                Regex("http-referrer\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
                    .find(l)?.groupValues?.get(1)?.let { currentHeaders["Referer"] = it }
                Regex("http-user-agent\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
                    .find(l)?.groupValues?.get(1)?.let { currentHeaders["User-Agent"] = it }
                currentTitle = l.substringAfterLast(",").trim()
                if (currentTitle.isEmpty()) currentTitle = "Saluran Live"
            } else if (l.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val option = l.substringAfter(':')
                when {
                    option.startsWith("http-referrer=", ignoreCase = true) -> currentHeaders["Referer"] = option.substringAfter('=')
                    option.startsWith("http-user-agent=", ignoreCase = true) -> currentHeaders["User-Agent"] = option.substringAfter('=')
                }
            } else if (l.startsWith("#KODIPROP:", ignoreCase = true)) {
                val prop = l.substringAfter(':')
                if (prop.startsWith("inputstream.adaptive.stream_headers=", ignoreCase = true)) {
                    prop.substringAfter('=').split('&').forEach { header ->
                        val separator = header.indexOf('=')
                        if (separator > 0) currentHeaders[header.substring(0, separator)] = header.substring(separator + 1)
                    }
                }
            } else if (l.startsWith("#EXTHTTP:", ignoreCase = true)) {
                val json = l.substringAfter(':').trim()
                runCatching {
                    val headers = JSONObject(json)
                    headers.keys().forEach { key ->
                        val value = headers.optString(key).trim()
                        if (value.isNotBlank()) currentHeaders[key] = value
                    }
                }
            } else if (!l.startsWith("#") && l.isNotBlank()) {
                val rawUrl = l.substringBefore('|').trim()
                val pipeHeaders = l.substringAfter('|', "").trim()
                pipeHeaders.split('&').forEach { header ->
                    val separator = header.indexOf('=')
                    if (separator > 0) currentHeaders[header.substring(0, separator).trim()] =
                        runCatching { URLDecoder.decode(header.substring(separator + 1).trim(), StandardCharsets.UTF_8.name()) }
                            .getOrDefault(header.substring(separator + 1).trim())
                }
                val resolvedUrl = try { URI(playlistUrl).resolve(rawUrl).toString() } catch (_: Exception) { rawUrl }
                val resolvedLogo = if (currentLogo.startsWith("http://") || currentLogo.startsWith("https://")) {
                    currentLogo
                } else {
                    try { URI(playlistUrl).resolve(currentLogo).toString() } catch (_: Exception) { currentLogo }
                }
                items.add(
                    MediaItem(
                        id = UUID.randomUUID().toString(),
                        profileId = profileId,
                        title = currentTitle,
                        url = resolvedUrl,
                        thumbnailUrl = resolvedLogo.ifEmpty { "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=500" },
                        type = if (resolvedUrl.substringBefore('?').contains(".m3u8", ignoreCase = true)) "STREAM" else "VIDEO",
                        sourceType = "LINK",
                        category = currentGroup,
                        extraInfo = currentHeaders.entries.joinToString("&") { "${it.key}=${it.value}" }
                    )
                )
                // reset
                currentTitle = "Saluran Live"
                currentLogo = ""
                currentHeaders.clear()
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
