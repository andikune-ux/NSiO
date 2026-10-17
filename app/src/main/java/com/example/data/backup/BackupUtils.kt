package com.example.data.backup

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.Profile
import com.example.data.model.ProfileSettings
import com.example.data.model.SourcePlaylist
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
            rootObj.put("version", 1)
            rootObj.put("createdAt", System.currentTimeMillis())
            rootObj.put("profileName", profile.name)

            // Profile info
            val profileObj = JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("avatarEmoji", profile.avatarEmoji)
                put("isPinEnabled", profile.isPinEnabled)
                put("pin", profile.pin) // Stored encrypted/hashed in production
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
                    put("serverPin", s.serverPin)
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
                                pin = p.optString("pin", ""),
                                isPinEnabled = p.optBoolean("isPinEnabled", false),
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
                                serverPin = s.optString("serverPin", "1234")
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
                                createdAt = m.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                    mediaDao.insertAll(restoredItems)
                    restoreSourcePlaylists(database, restoredItems)
                }

                return@withContext BackupResult(
                    isSuccess = true,
                    message = "Restore Backup Aman berhasil dipulihkan secara penuh!"
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
                        pin = p.optString("pin", ""),
                        isPinEnabled = p.optBoolean("isPinEnabled", false),
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
                            serverPin = s.optString("serverPin", "1234")
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
                                progressMs = m.optLong("progressMs", 0L)
                            )
                        )
                    }
                    mediaDao.insertAll(items)
                    restoreSourcePlaylists(database, items)
                }

                return@withContext BackupResult(
                    isSuccess = true,
                    message = "Restore Profil '${p.getString("name")}' berhasil!"
                )
            }

            BackupResult(isSuccess = false, message = "Format file backup tidak dikenali")
        } catch (e: Exception) {
            BackupResult(isSuccess = false, message = "Gagal memulihkan backup: ${e.localizedMessage}")
        }
    }

    private suspend fun restoreSourcePlaylists(database: AppDatabase, media: List<MediaItem>) {
        val playlistDao = database.sourcePlaylistDao()
        media.filter { it.sourceUrl.isNotBlank() }.groupBy { it.playlistId.ifBlank { it.sourceUrl } }
            .forEach { (key, items) ->
                val first = items.first()
                playlistDao.insert(
                    SourcePlaylist(
                        id = if (first.playlistId.isNotBlank()) first.playlistId else key,
                        profileId = first.profileId,
                        name = first.sourceUrl.substringAfter("//").substringBefore("/").ifBlank { first.title },
                        url = first.sourceUrl,
                        sourceType = first.sourceType,
                        itemCount = items.size
                    )
                )
            }
    }
}
