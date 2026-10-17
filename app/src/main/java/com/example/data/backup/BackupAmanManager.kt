package com.example.data.backup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.Profile
import com.example.data.model.ProfileSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Backup Aman NSiO (TXT).
 * Lokasi utama : /sdcard/NSiO/backup aman/Backup Aman-NSiO-DD-MM-YYYY.TXT
 * Fallback     : folder app (kalau izin belum diberikan atau tulis gagal)
 */
object BackupAmanManager {

    const val JSON_BEGIN = "---BEGIN-RESTORE-JSON---"
    const val JSON_END = "---END-RESTORE-JSON---"
    private const val PUBLIC_FOLDER = "NSiO/backup aman"
    private const val LINE = "============================================================"

    private fun jakartaFormat(pattern: String): SimpleDateFormat {
        return SimpleDateFormat(pattern, Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }
    }

    /** Apakah app boleh menulis ke /sdcard/NSiO/backup aman/ */
    fun hasStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Buka halaman izin: "Akses semua file" (Android 11+) atau info app (Android 10-). */
    private fun openStorageAccessSettings(context: Context) {
        try {
            val uri = Uri.parse("package:${context.packageName}")
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val general = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                general.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(general)
            } catch (e2: Exception) {
                // Perangkat tidak punya halaman izin ini (misal sebagian Android TV)
            }
        }
    }

    /** Ambil blok JSON restore dari file TXT. Kalau isinya sudah JSON biasa, dikembalikan apa adanya. */
    fun extractJson(content: String): String {
        val start = content.indexOf(JSON_BEGIN)
        val end = content.indexOf(JSON_END)
        if (start >= 0 && end > start) {
            return content.substring(start + JSON_BEGIN.length, end).trim()
        }
        return content
    }

    private fun buildRestoreJson(
        profiles: List<Profile>,
        settings: List<ProfileSettings>,
        media: List<MediaItem>
    ): JSONObject {
        val rootObj = JSONObject()
        rootObj.put("backupType", "FULL_BACKUP_AMAN")
        rootObj.put("appVersion", BuildConfig.VERSION_NAME)
        rootObj.put("version", 1)
        rootObj.put("timestamp", System.currentTimeMillis())

        val profilesArr = JSONArray()
        profiles.forEach { p ->
            profilesArr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("avatarEmoji", p.avatarEmoji)
                put("pin", p.pin)
                put("isPinEnabled", p.isPinEnabled)
                put("isDefault", p.isDefault)
                put("createdAt", p.createdAt)
            })
        }
        rootObj.put("profiles", profilesArr)

        val settingsArr = JSONArray()
        settings.forEach { s ->
            settingsArr.put(JSONObject().apply {
                put("profileId", s.profileId)
                put("language", s.language)
                put("themeMode", s.themeMode)
                put("displayMode", s.displayMode)
                put("gridColumns", s.gridColumns)
                put("serverPin", s.serverPin)
            })
        }
        rootObj.put("settings", settingsArr)

        val mediaArr = JSONArray()
        media.forEach { m ->
            mediaArr.put(JSONObject().apply {
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
            })
        }
        rootObj.put("mediaItems", mediaArr)
        return rootObj
    }

    private fun buildText(
        context: Context,
        profiles: List<Profile>,
        settings: List<ProfileSettings>,
        media: List<MediaItem>,
        restoreJson: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine(LINE)
        sb.appendLine("BACKUP AMAN - NSiO")
        sb.appendLine(LINE)
        sb.appendLine("Tanggal Export  : ${jakartaFormat("dd-MM-yyyy HH:mm:ss").format(Date())}")
        sb.appendLine("Versi Aplikasi  : ${BuildConfig.VERSION_NAME}")
        sb.appendLine("Package         : ${context.packageName}")
        sb.appendLine("Device          : ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android Version : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine(LINE)
        sb.appendLine()

        sb.appendLine(LINE)
        sb.appendLine("PENGATURAN USER SAAT INI")
        sb.appendLine(LINE)
        profiles.forEach { p ->
            val defaultTag = if (p.isDefault) " (default)" else ""
            sb.appendLine("PROFIL : ${p.avatarEmoji} ${p.name}$defaultTag")
            sb.appendLine("- PIN Profil   : ${if (p.isPinEnabled) "Aktif" else "Nonaktif"}")
            val s = settings.firstOrNull { it.profileId == p.id }
            if (s != null) {
                sb.appendLine("- Bahasa       : ${s.language}")
                sb.appendLine("- Tema         : ${s.themeMode}")
                sb.appendLine("- Mode Tampil  : ${s.displayMode}")
                sb.appendLine("- Kolom Grid   : ${s.gridColumns}")
            }
            sb.appendLine("- Jumlah Media : ${media.count { it.profileId == p.id }}")
            sb.appendLine()
        }

        sb.appendLine(LINE)
        sb.appendLine("RINGKASAN DATA")
        sb.appendLine(LINE)
        sb.appendLine("- Total Profil : ${profiles.size}")
        sb.appendLine("- Total Media  : ${media.size}")
        sb.appendLine()

        sb.appendLine(LINE)
        sb.appendLine("DATA UNTUK RESTORE (JANGAN DIUBAH)")
        sb.appendLine(LINE)
        sb.appendLine(JSON_BEGIN)
        sb.appendLine(restoreJson)
        sb.appendLine(JSON_END)
        sb.appendLine()
        sb.appendLine(LINE)
        sb.appendLine("END OF BACKUP")
        sb.appendLine(LINE)
        return sb.toString()
    }

    suspend fun createBackupAman(context: Context, database: AppDatabase): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val profiles = database.profileDao().getAllProfilesList()
                val settings = database.profileDao().getAllSettingsList()
                val media = database.mediaDao().getAllMediaList()

                val json = buildRestoreJson(profiles, settings, media).toString(2)
                val text = buildText(context, profiles, settings, media, json)
                val fileName = "Backup Aman-NSiO-${jakartaFormat("dd-MM-yyyy").format(Date())}.TXT"

                var savedFile: File? = null
                var reason = ""

                if (hasStorageAccess(context)) {
                    try {
                        val dir = File(Environment.getExternalStorageDirectory(), PUBLIC_FOLDER)
                        dir.mkdirs()
                        val target = File(dir, fileName)
                        target.writeText(text, Charsets.UTF_8)
                        if (target.exists() && target.length() > 0L) {
                            savedFile = target
                        } else {
                            reason = "File tidak berhasil dibuat di folder publik."
                        }
                    } catch (e: Exception) {
                        reason = "Gagal menulis ke folder publik: ${e.localizedMessage}"
                    }
                } else {
                    reason = "Izin akses penyimpanan belum diberikan."
                }

                if (savedFile != null) {
                    return@withContext BackupResult(
                        isSuccess = true,
                        filePath = savedFile.absolutePath,
                        message = "Backup Aman berhasil disimpan!\n" +
                            "Lokasi: ${savedFile.absolutePath}\n" +
                            "${profiles.size} Profil, ${media.size} Media."
                    )
                }

                // Fallback: folder app, supaya data tetap aman walau izin belum ada
                val fallbackDir = File(context.getExternalFilesDir(null) ?: context.filesDir, PUBLIC_FOLDER)
                fallbackDir.mkdirs()
                val fallbackFile = File(fallbackDir, fileName)
                fallbackFile.writeText(text, Charsets.UTF_8)

                if (!hasStorageAccess(context)) {
                    openStorageAccessSettings(context)
                }

                BackupResult(
                    isSuccess = true,
                    filePath = fallbackFile.absolutePath,
                    message = "Backup tersimpan sementara di folder app:\n${fallbackFile.absolutePath}\n" +
                        "$reason Beri izin \"Akses semua file\", lalu tekan tombol lagi " +
                        "untuk menyimpan ke /sdcard/NSiO/backup aman/"
                )
            } catch (e: Exception) {
                BackupResult(
                    isSuccess = false,
                    message = "Gagal membuat Backup Aman: ${e.localizedMessage}"
                )
            }
        }
}
