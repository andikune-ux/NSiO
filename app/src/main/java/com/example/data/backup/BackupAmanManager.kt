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
        rootObj.put("version", 2)
        rootObj.put("securityCredentialsExcluded", true)
        rootObj.put("timestamp", System.currentTimeMillis())

        val profilesArr = JSONArray()
        profiles.forEach { p ->
            profilesArr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("avatarEmoji", p.avatarEmoji)
                // PINs are device-local credentials; never include them in portable backups.
                put("isPinEnabled", false)
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
                put("gridColumnsPhone", s.gridColumnsPhone)
                put("gridColumnsTv", s.gridColumnsTv)
                // Server PIN is intentionally excluded from portable backups.
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
                put("pluginId", m.pluginId)
                put("repositoryId", m.repositoryId)
                put("durationMs", m.durationMs)
                put("progressMs", m.progressMs)
                put("lastWatchedAt", m.lastWatchedAt)
                put("isFavorite", m.isFavorite)
                put("createdAt", m.createdAt)
                put("extraInfo", m.extraInfo)
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
            sb.appendLine("- PIN Profil   : ${if (p.isPinEnabled) "Aktif (tidak disertakan dalam backup)" else "Nonaktif"}")
            val s = settings.firstOrNull { it.profileId == p.id }
            if (s != null) {
                sb.appendLine("- Bahasa       : ${s.language}")
                sb.appendLine("- Tema         : ${s.themeMode}")
                sb.appendLine("- Mode Tampil  : ${s.displayMode}")
                sb.appendLine("- Kolom Grid HP: ${s.gridColumnsPhone}")
                sb.appendLine("- Kolom Grid TV: ${s.gridColumnsTv}")
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

    /**
     * Exports a technical application-structure note for AI assistance.
     * It deliberately excludes personal profile names, media URLs, PINs and restore JSON.
     */
    suspend fun createBackupAman(context: Context, database: AppDatabase): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val profileCount = database.profileDao().getAllProfilesList().size
                val mediaCount = database.mediaDao().getAllMediaList().size
                val text = buildString {
                    appendLine(LINE)
                    appendLine("NSiO - BACKUP AMAN / STRUKTUR APLIKASI")
                    appendLine(LINE)
                    appendLine("Tanggal Export  : ${jakartaFormat("dd-MM-yyyy HH:mm:ss").format(Date())}")
                    appendLine("Versi Aplikasi  : ${BuildConfig.VERSION_NAME}")
                    appendLine("Package         : ${context.packageName}")
                    appendLine("Perangkat       : ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android         : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine()
                    appendLine("RINGKASAN LOKAL (AGREGAT, TANPA IDENTITAS)")
                    appendLine("- Jumlah profil : $profileCount")
                    appendLine("- Jumlah media  : $mediaCount")
                    appendLine()
                    appendLine("ALUR AKSES")
                    appendLine("1. Kalkulator biasa menerima kode akses tersembunyi.")
                    appendLine("2. Kode benar membuka NSiO dan halaman pemilihan profil.")
                    appendLine("3. Profil dengan PIN meminta PIN profil; profil tanpa PIN langsung dibuka.")
                    appendLine("4. Beranda hanya menampilkan data setelah profil dipilih.")
                    appendLine()
                    appendLine("STRUKTUR FITUR UTAMA")
                    appendLine("- Profil: identitas foto/nama, PIN profil, pemisahan data.")
                    appendLine("- Beranda: katalog media, pencarian, filter, dan pengurutan.")
                    appendLine("- Pemutar: pemutaran media, subtitle, kualitas, rasio, dan kontrol gestur.")
                    appendLine("- TikTod: Untuk Mu, Jedag Jedug, Avatar, dan pengelolaan video per Avatar.")
                    appendLine("- Sumber: Google Drive, playlist, file lokal, server, dan plugin CloudStream.")
                    appendLine("- Penyimpanan: database lokal, cache media bersama, dan unduhan.")
                    appendLine("- Cadangan: Backup Profil JSON dan Backup Aman TXT untuk konteks teknis AI.")
                    appendLine()
                    appendLine("BATASAN PRIVASI")
                    appendLine("File ini TIDAK memuat nama/foto profil, ID akun, PIN, token, URL video,")
                    appendLine("daftar playlist pribadi, histori tontonan, atau blok JSON untuk pemulihan.")
                    appendLine("File ini adalah ringkasan struktur aplikasi, bukan file restore.")
                    appendLine()
                    appendLine("PETUNJUK UNTUK AI ASISTEN")
                    appendLine("Gunakan informasi ini sebagai konteks teknis NSiO. Minta source code")
                    appendLine("atau log terkait secara terpisah jika analisis membutuhkan detail implementasi.")
                    appendLine(LINE)
                    appendLine("END OF STRUCTURE REPORT")
                    appendLine(LINE)
                }
                val fileName = "Backup Aman-NSiO-Struktur-${jakartaFormat("dd-MM-yyyy_HH-mm-ss").format(Date())}.TXT"
                var savedFile: File? = null
                var reason = ""

                if (hasStorageAccess(context)) {
                    try {
                        val dir = File(Environment.getExternalStorageDirectory(), PUBLIC_FOLDER)
                        dir.mkdirs()
                        val target = File(dir, fileName)
                        target.writeText(text, Charsets.UTF_8)
                        if (target.isFile && target.length() > 0L) savedFile = target
                        else reason = "File tidak berhasil dibuat di folder publik."
                    } catch (e: Exception) {
                        reason = "Gagal menulis ke folder publik: ${e.localizedMessage}"
                    }
                } else {
                    reason = "Izin akses penyimpanan belum diberikan."
                }

                if (savedFile == null) {
                    val fallbackDir = File(context.getExternalFilesDir(null) ?: context.filesDir, PUBLIC_FOLDER)
                    if (!fallbackDir.exists()) fallbackDir.mkdirs()
                    val fallbackFile = File(fallbackDir, fileName)
                    fallbackFile.writeText(text, Charsets.UTF_8)
                    if (!hasStorageAccess(context)) openStorageAccessSettings(context)
                    savedFile = fallbackFile
                }

                BackupResult(
                    isSuccess = savedFile.isFile && savedFile.length() > 0L,
                    filePath = savedFile.absolutePath,
                    message = if (hasStorageAccess(context)) {
                        "Backup Aman TXT berhasil dibuat. File berisi ringkasan struktur NSiO tanpa data pribadi. Lokasi: ${savedFile.absolutePath}"
                    } else {
                        "Backup Aman TXT tersimpan di folder aplikasi. File berisi ringkasan struktur NSiO tanpa data pribadi. Lokasi: ${savedFile.absolutePath}. $reason"
                    }
                )
            } catch (e: Exception) {
                BackupResult(isSuccess = false, message = "Gagal membuat Backup Aman: ${e.localizedMessage}")
            }
        }

}
