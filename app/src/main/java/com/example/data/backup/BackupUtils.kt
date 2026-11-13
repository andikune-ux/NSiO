package com.example.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.Profile
import com.example.data.model.ProfileSettings
import com.example.data.model.SourcePlaylist
import com.example.data.security.PinVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    /**
     * Backup Biasa: Ekspor spesifik data profil (Media Items & Pengaturan Profil) ke JSON.
     * Lokasi: Penyimpanan Internal -> NSiO -> Backup
     */
    suspend fun createProfileBackup(
        context: Context,
        profile: Profile,
        settings: ProfileSettings?,
        mediaList: List<MediaItem>
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val rootObj = JSONObject()
            rootObj.put("backupType", "PROFILE_BACKUP")
            rootObj.put("version", 2)
            rootObj.put("securityCredentialsExcluded", true)
            rootObj.put("createdAt", System.currentTimeMillis())
            rootObj.put("profileName", profile.name)

            // Profile info
            val profileObj = JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("avatarEmoji", profile.avatarEmoji)
                // PINs are intentionally not exported in portable backups.
                put("isPinEnabled", false)
            }
            rootObj.put("profile", profileObj)

            // Settings
            settings?.let { s ->
                val settingsObj = JSONObject().apply {
                    put("profileId", s.profileId)
                    put("language", s.language)
                    put("themeMode", s.themeMode)
                    put("displayMode", s.displayMode)
                    put("gridColumns", s.gridColumns)
                    // Server PIN is device-local and must never be exported.
                }
                rootObj.put("settings", settingsObj)
            }

            // Media list
            val mediaArray = JSONArray()
            mediaList.forEach { m ->
                val mObj = JSONObject().apply {
                    put("id", m.id)
                    put("profileId", m.profileId)
                    put("title", m.title)
                    put("url", m.url)
                    put("thumbnailUrl", m.thumbnailUrl)
                    put("type", m.type)
                    put("sourceType", m.sourceType)
                    put("category", m.category)
                    put("playlistId", m.playlistId)
                    put("sourceUrl", m.sourceUrl)
                    put("durationMs", m.durationMs)
                    put("progressMs", m.progressMs)
                    put("isFavorite", m.isFavorite)
                    put("createdAt", m.createdAt)
                    put("extraInfo", m.extraInfo)
                }
                mediaArray.put(mObj)
            }
            rootObj.put("mediaItems", mediaArray)

            // Target directory: NSiO/Backup
            val baseDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "NSiO/Backup")
            if (!baseDir.exists()) baseDir.mkdirs()

            val fileName = "backup_${profile.name.replace(" ", "_")}_${getTimestamp()}.json"
            val file = File(baseDir, fileName)
            file.writeText(rootObj.toString(2))

            BackupResult(
                isSuccess = true,
                filePath = file.absolutePath,
                message = "Backup Profil '${profile.name}' berhasil disimpan (${mediaList.size} item)"
            )
        } catch (e: Exception) {
            BackupResult(isSuccess = false, message = "Gagal membuat backup profil: ${e.localizedMessage}")
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
    suspend fun restoreFromFile(context: Context, database: AppDatabase, jsonContent: String): BackupResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(BackupAmanManager.extractJson(jsonContent))
            val backupType = root.optString("backupType", "UNKNOWN")
            val result = database.withTransaction {
                val profileDao = database.profileDao()
                val mediaDao = database.mediaDao()

                if (backupType == "FULL_BACKUP_AMAN") {
                // Restore all profiles
                val profArr = root.optJSONArray("profiles")
                if (profArr != null) {
                    for (i in 0 until profArr.length()) {
                        val p = profArr.getJSONObject(i)
                        profileDao.insertProfile(
                            Profile(
                                id = p.getString("id"),
                                name = p.getString("name"),
                                avatarEmoji = p.optString("avatarEmoji", "🎬"),
                                pin = if (root.optBoolean("securityCredentialsExcluded", false)) "" else p.optString("pin", ""),
                                isPinEnabled = if (root.optBoolean("securityCredentialsExcluded", false)) false else p.optBoolean("isPinEnabled", false),
                                isDefault = p.optBoolean("isDefault", false),
                                createdAt = p.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // Restore all settings
                val settArr = root.optJSONArray("settings")
                if (settArr != null) {
                    for (i in 0 until settArr.length()) {
                        val s = settArr.getJSONObject(i)
                        profileDao.insertSettings(
                            ProfileSettings(
                                profileId = s.getString("profileId"),
                                language = s.optString("language", "ID"),
                                themeMode = s.optString("themeMode", "DARK"),
                                displayMode = s.optString("displayMode", "TV"),
                                gridColumns = s.optInt("gridColumns", 4),
                                serverPin = PinVault.encrypt(
                                context,
                                if (root.optBoolean("securityCredentialsExcluded", false)) {
                                    ProfileSettings(profileId = s.getString("profileId")).serverPin
                                } else {
                                    s.optString("serverPin", "")
                                }
                            )
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
                                durationMs = m.optLong("durationMs", 0L),
                                progressMs = m.optLong("progressMs", 0L),
                                isFavorite = m.optBoolean("isFavorite", false),
                                createdAt = m.optLong("createdAt", System.currentTimeMillis()),
                                extraInfo = m.optString("extraInfo", "")
                            )
                        )
                    }
                    val normalizedItems = restoreSourcePlaylists(database, restoredItems)
                    mediaDao.insertAll(normalizedItems)
                }

                return@withTransaction BackupResult(
                    isSuccess = true,
                    message = if (root.optBoolean("securityCredentialsExcluded", false)) "Restore berhasil. Demi keamanan, PIN profil perlu dibuat ulang dan PIN server dibuat ulang khusus perangkat ini." else "Restore Backup Aman berhasil dipulihkan secara penuh!"
                )
                } else if (backupType == "PROFILE_BACKUP") {
                // Restore single profile
                val p = root.getJSONObject("profile")
                val profileId = p.getString("id")
                profileDao.insertProfile(
                    Profile(
                        id = profileId,
                        name = p.getString("name"),
                        avatarEmoji = p.optString("avatarEmoji", "🎬"),
                        pin = if (root.optBoolean("securityCredentialsExcluded", false)) "" else p.optString("pin", ""),
                        isPinEnabled = if (root.optBoolean("securityCredentialsExcluded", false)) false else p.optBoolean("isPinEnabled", false),
                        isDefault = false
                    )
                )

                if (root.has("settings")) {
                    val s = root.getJSONObject("settings")
                    profileDao.insertSettings(
                        ProfileSettings(
                            profileId = profileId,
                            language = s.optString("language", "ID"),
                            themeMode = s.optString("themeMode", "DARK"),
                            displayMode = s.optString("displayMode", "TV"),
                            gridColumns = s.optInt("gridColumns", 4),
                            serverPin = PinVault.encrypt(
                            context,
                            if (root.optBoolean("securityCredentialsExcluded", false)) {
                                ProfileSettings(profileId = profileId).serverPin
                            } else {
                                s.optString("serverPin", "")
                            }
                        )
                        )
                    )
                }

                val mediaArr = root.optJSONArray("mediaItems")
                if (mediaArr != null) {
                    val items = mutableListOf<MediaItem>()
                    for (i in 0 until mediaArr.length()) {
                        val m = mediaArr.getJSONObject(i)
                        items.add(
                            MediaItem(
                                id = m.getString("id"),
                                profileId = profileId,
                                title = m.getString("title"),
                                url = m.getString("url"),
                                thumbnailUrl = m.optString("thumbnailUrl", ""),
                                type = m.optString("type", "VIDEO"),
                                sourceType = m.optString("sourceType", "LINK"),
                                category = m.optString("category", "Umum"),
                                playlistId = m.optString("playlistId", ""),
                                sourceUrl = m.optString("sourceUrl", ""),
                                durationMs = m.optLong("durationMs", 0L),
                                progressMs = m.optLong("progressMs", 0L),
                                isFavorite = m.optBoolean("isFavorite", false),
                                createdAt = m.optLong("createdAt", System.currentTimeMillis()),
                                extraInfo = m.optString("extraInfo", "")
                            )
                        )
                    }
                    val normalizedItems = restoreSourcePlaylists(database, items)
                    mediaDao.insertAll(normalizedItems)
                }

                return@withTransaction BackupResult(
                    isSuccess = true,
                    message = if (root.optBoolean("securityCredentialsExcluded", false)) "Restore profil berhasil. PIN profil tidak disertakan; buat PIN baru dari menu Profil. PIN server dibuat ulang khusus perangkat ini." else "Restore Profil berhasil!"
                )
            }

                BackupResult(isSuccess = false, message = "Format file backup tidak dikenali")
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
