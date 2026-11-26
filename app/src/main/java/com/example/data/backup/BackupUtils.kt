package com.example.data.backup

import android.content.Context
import android.os.Environment
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.Profile
import com.example.data.model.ProfileSettings
import com.example.data.model.SourcePlaylist
import com.example.data.security.PinVault
import com.example.tiktok.TikTodLocalStore
import com.example.tiktok.TikTodFeedPolicy
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BackupResult(
    val isSuccess: Boolean,
    val filePath: String = "",
    val message: String = ""
)

object BackupUtils {

    // Keys that should never be exported in portable backups for security
    val nonTransferableKeys = listOf(
        "biometric_key",
        "auth_token",
        "access_token",
        "google_id_token",
        "internal_device_id"
    )

    private fun safeFolderName(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim('_', '.', ' ').ifBlank { "Profil" }.take(64)

    private fun canonicalMediaIdentity(item: MediaItem): String {
        val candidates = listOf(item.url, item.sourceUrl, item.thumbnailUrl).filter(String::isNotBlank)
        for (raw in candidates) {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: continue
            val host = uri.host?.lowercase().orEmpty()
            if (host.endsWith("google.com") || host.endsWith("googleusercontent.com")) {
                val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
                val marker = segments.indexOfFirst { it in setOf("file", "d", "folders", "document", "spreadsheets", "presentation") }
                if (marker >= 0 && marker + 1 < segments.size) return "drive:" + segments[marker + 1]
                val id = uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "id" }
                    ?.substringAfter('=', "").orEmpty()
                if (id.isNotBlank()) return "drive:" + java.net.URLDecoder.decode(id, "UTF-8")
            }
        }
        val uri = runCatching { URI(item.url.trim()) }.getOrNull()
        if (uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            val port = if (uri.port == 80 || uri.port == 443) -1 else uri.port
            return "url:" + URI(uri.scheme.lowercase(), uri.userInfo, uri.host.lowercase(), port,
                uri.path?.ifBlank { "/" } ?: "/", uri.query, null).toString()
        }
        return "url:" + item.url.trim()
    }

    /**
     * Membuat satu backup per profil. File terenkripsi memakai PIN profil yang sudah
     * diverifikasi oleh sesi aktif; PIN tidak pernah ditulis sebagai teks biasa.
     */
    suspend fun createProfileBackup(
        context: Context,
        profile: Profile,
        settings: ProfileSettings?,
        mediaList: List<MediaItem>,
        sourcePlaylists: List<SourcePlaylist>,
        profilePin: String,
        avatarAssignments: Map<String, String>
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            require(!profile.isPinEnabled || profilePin.matches(Regex("\\d{4}"))) {
                "PIN profil tidak dapat dibaca. Backup dibatalkan agar file tidak dibuat tanpa perlindungan."
            }
            val profileMedia = mediaList.filter { it.profileId == profile.id }
            val payload = JSONObject().apply {
                put("backupType", "PROFILE_BACKUP")
                put("version", 3)
                put("createdAt", System.currentTimeMillis())
                put("sourceProfileName", profile.name)
                put("mediaItems", JSONArray().apply {
                    profileMedia.forEach { m ->
                        put(JSONObject().apply {
                            put("id", m.id); put("title", m.title); put("url", m.url)
                            put("thumbnailUrl", m.thumbnailUrl); put("type", m.type)
                            put("sourceType", m.sourceType); put("category", m.category)
                            put("playlistId", m.playlistId); put("sourceUrl", m.sourceUrl)
                            put("pluginId", m.pluginId); put("repositoryId", m.repositoryId)
                            put("durationMs", m.durationMs); put("progressMs", m.progressMs)
                            put("lastWatchedAt", m.lastWatchedAt); put("isFavorite", m.isFavorite)
                            put("createdAt", m.createdAt); put("extraInfo", m.extraInfo)
                        })
                    }
                })
                put("sourcePlaylists", JSONArray().apply {
                    sourcePlaylists.filter { it.profileId == profile.id }.forEach { playlist ->
                        put(JSONObject().apply {
                            put("id", playlist.id); put("name", playlist.name); put("url", playlist.url)
                            put("sourceType", playlist.sourceType); put("createdAt", playlist.createdAt)
                            put("updatedAt", playlist.updatedAt)
                        })
                    }
                })
                // Export only Avatar ownership relationships, not profile IDs or preferences.
                val ownedAssignmentKeys = profileMedia.flatMap { item ->
                    listOf(item.id, TikTodFeedPolicy.assignmentKey(item), canonicalMediaIdentity(item))
                }.toSet()
                put("avatarAssignments", JSONObject().apply {
                    avatarAssignments.forEach { (mediaKey, avatarId) ->
                        if (mediaKey in ownedAssignmentKeys && avatarId.isNotBlank()) put(mediaKey, avatarId)
                    }
                })
            }
            val fileContent = if (profile.isPinEnabled) {
                ProfileBackupCrypto.encrypt(payload.toString(), profilePin)
            } else payload.toString(2)

            val profileFolder = safeFolderName(profile.name)
            val fileName = "${profileFolder}_${profile.id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json"
            val roots = buildList {
                if (BackupAmanManager.hasStorageAccess(context)) {
                    add(File(Environment.getExternalStorageDirectory(), "NSiO/Backup Profil"))
                }
                add(File(context.getExternalFilesDir(null) ?: context.filesDir, "NSiO/Backup Profil"))
                add(File(context.filesDir, "NSiO/Backup Profil"))
            }.distinctBy { it.absolutePath }

            var saved: File? = null
            var lastError: Exception? = null
            for (root in roots) {
                try {
                    val directory = File(root, profileFolder)
                    if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
                        throw IllegalStateException("Folder backup tidak dapat dibuat.")
                    }
                    val target = File(directory, fileName)
                    val temporary = File(directory, fileName + ".tmp")
                    temporary.writeText(fileContent, Charsets.UTF_8)
                    require(temporary.isFile && temporary.length() > 0L) { "File sementara backup kosong." }
                    if (target.exists() && !target.delete()) throw IllegalStateException("Backup lama tidak dapat ditimpa.")
                    if (!temporary.renameTo(target)) {
                        target.writeText(fileContent, Charsets.UTF_8)
                        temporary.delete()
                    }
                    require(target.isFile && target.length() > 0L) { "File backup gagal disimpan." }
                    saved = target
                    break
                } catch (e: Exception) { lastError = e }
            }
            val file = saved ?: throw IllegalStateException("Semua lokasi penyimpanan backup gagal digunakan.", lastError)
            BackupResult(true, file.absolutePath,
                "Backup Profil '${profile.name}' berhasil diperbarui (${profileMedia.size} video/link).")
        } catch (e: Exception) {
            BackupResult(false, message = "Gagal membuat backup profil: ${e.localizedMessage}")
        }
    }

    /**
     * Backup Aman (Full Backup): satu file TXT berisi pengaturan, ringkasan data,
     * dan blok JSON untuk restore. Logika ada di BackupAmanManager.
     * Lokasi: /sdcard/NSiO/backup aman/ (fallback: folder app)
     */
    suspend fun createFullBackup(context: Context, database: AppDatabase): BackupResult {
        return BackupAmanManager.createBackupAman(context, database)
    }

    /**
     * Restore database and settings from any backup JSON file.
     */
    private fun validateBackupRoot(root: JSONObject, backupType: String) {
        require(backupType == "FULL_BACKUP_AMAN" || backupType == "PROFILE_BACKUP") {
            "Jenis file backup tidak didukung."
        }
        val version = root.optInt("version", 1)
        require(version in 1..3) { "Versi backup $version belum didukung aplikasi ini." }
        val media = root.optJSONArray("mediaItems")
            ?: throw IllegalArgumentException("Backup tidak memiliki daftar media.")
        require(media.length() <= 50_000) { "Backup berisi terlalu banyak item media." }
        if (backupType == "FULL_BACKUP_AMAN") {
            val profiles = root.optJSONArray("profiles")
                ?: throw IllegalArgumentException("Backup penuh tidak memiliki daftar profil.")
            require(profiles.length() in 1..100) { "Jumlah profil dalam backup tidak valid." }
            require(root.optJSONArray("settings") != null) {
                "Backup penuh tidak memiliki pengaturan profil."
            }
        } else {
            require(root.optString("sourceProfileName").isNotBlank() || root.optJSONObject("profile") != null) {
                "Backup profil tidak memiliki identitas sumber."
            }
        }
        val seenIds = HashSet<String>(media.length())
        for (index in 0 until media.length()) {
            val item = media.optJSONObject(index)
                ?: throw IllegalArgumentException("Item media ke-${index + 1} tidak valid.")
            val id = item.optString("id").trim()
            val profileId = item.optString("profileId").trim()
            val title = item.optString("title").trim()
            val url = item.optString("url").trim()
            require(id.isNotEmpty() && title.isNotEmpty() && url.isNotEmpty()) {
                "Item media ke-${index + 1} memiliki identitas atau URL kosong."
            }
            require(seenIds.add(id)) { "Backup memiliki ID media duplikat: $id" }
            require(item.optLong("progressMs", 0L) >= 0L && item.optLong("durationMs", 0L) >= 0L) {
                "Durasi atau posisi tontonan item ke-${index + 1} tidak valid."
            }
        }
    }

    suspend fun restoreFromFile(context: Context, database: AppDatabase, jsonContent: String, currentProfileId: String, backupPin: String = ""): BackupResult = withContext(Dispatchers.IO) {
        try {
            require(jsonContent.length <= 64 * 1024 * 1024) { "File backup melebihi batas 64 MB." }
            val outer = JSONObject(BackupAmanManager.extractJson(jsonContent))
            val root = if (ProfileBackupCrypto.isEncrypted(outer)) {
                JSONObject(ProfileBackupCrypto.decrypt(outer, backupPin))
            } else outer
            val backupType = root.optString("backupType", "UNKNOWN")
            validateBackupRoot(root, backupType)
            val result = database.withTransaction {
                val profileDao = database.profileDao()
                val mediaDao = database.mediaDao()

                if (backupType == "FULL_BACKUP_AMAN") {
                // Restore all profiles
                val profArr = root.optJSONArray("profiles")
                if (profArr != null) {
                    val credentialsExcluded = root.optBoolean("securityCredentialsExcluded", false)
                    var hasDefaultProfile = profileDao.getAllProfilesList().any { it.isDefault }
                    for (i in 0 until profArr.length()) {
                        val p = profArr.getJSONObject(i)
                        val profileId = p.getString("id")
                        val existing = profileDao.getProfileById(profileId)
                        val makeDefault = existing?.isDefault ?: (p.optBoolean("isDefault", false) && !hasDefaultProfile)
                        profileDao.insertProfile(
                            Profile(
                                id = profileId,
                                name = p.getString("name"),
                                avatarEmoji = p.optString("avatarEmoji", "🎬"),
                                pin = if (credentialsExcluded) PinVault.encrypt(context, existing?.pin.orEmpty()) else PinVault.encrypt(context, p.optString("pin", "")),
                                isPinEnabled = if (credentialsExcluded) existing?.isPinEnabled ?: false else p.optBoolean("isPinEnabled", false),
                                isDefault = makeDefault,
                                createdAt = existing?.createdAt ?: p.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                        if (makeDefault) hasDefaultProfile = true
                    }
                }

                // Restore all settings
                val settArr = root.optJSONArray("settings")
                if (settArr != null) {
                    for (i in 0 until settArr.length()) {
                        val s = settArr.getJSONObject(i)
                        val profileId = s.getString("profileId")
                        val existingSettings = profileDao.getSettings(profileId)
                        val credentialsExcluded = root.optBoolean("securityCredentialsExcluded", false)
                        profileDao.insertSettings(
                            ProfileSettings(
                                profileId = profileId,
                                language = s.optString("language", "ID"),
                                themeMode = s.optString("themeMode", "DARK"),
                                displayMode = s.optString("displayMode", "TV"),
                                gridColumns = s.optInt("gridColumns", 4).coerceIn(1, 8),
                            gridColumnsPhone = s.optInt("gridColumnsPhone", 2).coerceIn(1, 8),
                            gridColumnsTv = s.optInt("gridColumnsTv", 4).coerceIn(1, 8),
                                serverPin = if (credentialsExcluded) {
                                    existingSettings?.serverPin?.let { PinVault.encrypt(context, it) }
                                        ?: PinVault.encrypt(context, ProfileSettings(profileId = profileId).serverPin)
                                } else {
                                    PinVault.encrypt(context, s.optString("serverPin", ""))
                                }
                            )
                        )
                    }
                }

                // Restore all media items
                val mediaArr = root.optJSONArray("mediaItems")
                if (mediaArr != null) {
                    val restoredItems = mutableListOf<MediaItem>()
                    for (i in 0 until mediaArr.length()) {
                        val m = mediaArr.getJSONObject(i)
                        restoredItems.add(
                            MediaItem(
                                id = m.getString("id"),
                                profileId = m.getString("profileId"),
                                title = m.getString("title"),
                                url = m.getString("url"),
                                thumbnailUrl = m.optString("thumbnailUrl", ""),
                                type = m.optString("type", "VIDEO"),
                                sourceType = m.optString("sourceType", "LINK"),
                                category = m.optString("category", "Umum"),
                                playlistId = m.optString("playlistId", ""),
                                sourceUrl = m.optString("sourceUrl", ""),
                                pluginId = m.optString("pluginId", ""),
                                repositoryId = m.optString("repositoryId", ""),
                                durationMs = m.optLong("durationMs", 0L),
                                progressMs = m.optLong("progressMs", 0L),
                                lastWatchedAt = m.optLong("lastWatchedAt", 0L),
                                isFavorite = m.optBoolean("isFavorite", false),
                                createdAt = m.optLong("createdAt", System.currentTimeMillis()),
                                extraInfo = m.optString("extraInfo", "")
                            )
                        )
                    }
                    val normalizedItems = restoreSourcePlaylists(database, restoredItems)
                    mediaDao.insertAll(normalizedItems)
                }

                BackupResult(
                    isSuccess = true,
                    message = if (root.optBoolean("securityCredentialsExcluded", false)) "Restore berhasil. Kredensial lokal yang sudah ada dipertahankan; profil baru perlu mengatur PIN dan mendapatkan PIN server khusus perangkat ini." else "Restore Backup Aman berhasil dipulihkan secara penuh!"
                )
                } else if (backupType == "PROFILE_BACKUP") {
                    require(currentProfileId.isNotBlank()) { "Profil penerima tidak ditemukan." }
                    val existingItems = mediaDao.getMediaListForProfile(currentProfileId)
                    val existingKeys = existingItems.associateBy(::canonicalMediaIdentity).toMutableMap()
                    val playlistDao = database.sourcePlaylistDao()
                    val existingPlaylists = playlistDao.getForProfile(currentProfileId).toMutableList()
                    val playlistIdMap = mutableMapOf<String, String>()
                    val playlistArray = root.optJSONArray("sourcePlaylists") ?: JSONArray()
                    for (i in 0 until playlistArray.length()) {
                        val source = playlistArray.optJSONObject(i) ?: continue
                        val sourceId = source.optString("id")
                        val name = source.optString("name").trim()
                        val url = source.optString("url").trim()
                        if (sourceId.isBlank() || name.isBlank() || url.isBlank()) continue
                        val existing = existingPlaylists.firstOrNull { it.name.trim().equals(name, true) }
                        val target = existing ?: SourcePlaylist(
                            id = UUID.randomUUID().toString(),
                            profileId = currentProfileId,
                            name = name,
                            url = url,
                            sourceType = source.optString("sourceType", "LINK"),
                            itemCount = 0
                        ).also {
                            playlistDao.insert(it)
                            existingPlaylists.add(it)
                        }
                        playlistIdMap[sourceId] = target.id
                    }

                    val sourceAssignments = root.optJSONObject("avatarAssignments") ?: JSONObject()
                    val importedAssignments = mutableMapOf<String, String>()
                    val mediaArray = root.getJSONArray("mediaItems")
                    var importedCount = 0
                    var skippedCount = 0
                    for (i in 0 until mediaArray.length()) {
                        val m = mediaArray.getJSONObject(i)
                        val sourceId = m.optString("id")
                        val sourceIdentity = canonicalMediaIdentity(MediaItem(id = sourceId, profileId = currentProfileId, title = m.optString("title"), url = m.optString("url"), sourceUrl = m.optString("sourceUrl", ""), thumbnailUrl = m.optString("thumbnailUrl", "")))
                        val item = MediaItem(
                            id = UUID.randomUUID().toString(),
                            profileId = currentProfileId,
                            title = m.getString("title"),
                            url = m.getString("url"),
                            thumbnailUrl = m.optString("thumbnailUrl", ""),
                            type = m.optString("type", "VIDEO"),
                            sourceType = m.optString("sourceType", "LINK"),
                            category = m.optString("category", "Umum"),
                            playlistId = playlistIdMap[m.optString("playlistId")].orEmpty(),
                            sourceUrl = m.optString("sourceUrl", ""),
                            pluginId = m.optString("pluginId", ""),
                            repositoryId = m.optString("repositoryId", ""),
                            durationMs = m.optLong("durationMs", 0L),
                            progressMs = 0L,
                            lastWatchedAt = 0L,
                            isFavorite = m.optBoolean("isFavorite", false),
                            createdAt = System.currentTimeMillis(),
                            extraInfo = m.optString("extraInfo", "")
                        )
                        val identity = canonicalMediaIdentity(item)
                        if (identity in existingKeys) {
                            skippedCount++
                            continue
                        }
                        mediaDao.insertMedia(item)
                        existingKeys[identity] = item
                        val sourceAvatar = sourceAssignments.optString(sourceId).takeIf(String::isNotBlank)
                            ?: sourceAssignments.optString(sourceIdentity).takeIf(String::isNotBlank)
                        if (sourceAvatar != null) importedAssignments[TikTodFeedPolicy.assignmentKey(item)] = sourceAvatar
                        importedCount++
                    }

                    // Preserve recipient ownership for all existing videos; only assign newly imported IDs.
                    val tikTodStore = TikTodLocalStore(context)
                    importedAssignments.forEach { (mediaKey, avatarId) ->
                        tikTodStore.assignVideos(currentProfileId, listOf(mediaKey), avatarId)
                    }
                    existingPlaylists.forEach { playlist ->
                        playlistDao.updateCount(playlist.id, mediaDao.countForPlaylist(playlist.id))
                    }

                    BackupResult(
                        isSuccess = true,
                        message = "Restore berhasil ke profil aktif. Ditambahkan $importedCount video/link, $skippedCount duplikat dilewati. Avatar dan playlist lama tetap dipertahankan."
                    )
                } else {
                    BackupResult(isSuccess = false, message = "Format file backup tidak dikenali.")
                }
            }
            return@withContext result
        } catch (e: Exception) {
            BackupResult(isSuccess = false, message = "Gagal memulihkan backup: ${e.localizedMessage}")
        }
    }

    /**
     * Reuses an existing source playlist by profile + URL so a locally edited
     * title survives restore. Media IDs are remapped to the retained playlist ID.
     */
    private suspend fun restoreSourcePlaylists(database: AppDatabase, media: List<MediaItem>): List<MediaItem> {
        val playlistDao = database.sourcePlaylistDao()
        val normalized = media.toMutableList()
        media.filter { it.sourceUrl.isNotBlank() }
            .groupBy { it.profileId to it.sourceUrl }
            .forEach { (_, items) ->
                val first = items.first()
                val existing = playlistDao.findByUrl(first.profileId, first.sourceUrl)
                val playlistId = existing?.id ?: first.playlistId.ifBlank { first.sourceUrl }
                val playlist = existing?.copy(
                    itemCount = items.size,
                    updatedAt = System.currentTimeMillis()
                ) ?: SourcePlaylist(
                    id = playlistId,
                    profileId = first.profileId,
                    name = first.sourceUrl.substringAfter("//").substringBefore("/").ifBlank { first.title },
                    url = first.sourceUrl,
                    sourceType = first.sourceType,
                    itemCount = items.size
                )
                playlistDao.insert(playlist)
                val itemIds = items.mapTo(hashSetOf()) { it.id }
                normalized.indices.forEach { index ->
                    val item = normalized[index]
                    if (item.id in itemIds) normalized[index] = item.copy(playlistId = playlistId)
                }
            }
        return normalized
    }
}
