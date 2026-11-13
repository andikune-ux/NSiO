package com.example.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

object AppUpdateManager {
    private const val RELEASE_API = "https://api.github.com/repos/andikune-ux/NSiO/releases/latest"
    private const val PREFS = "nsio_app_updates"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_VERSION = "download_version"
    private const val MAX_APK_SIZE = 512L * 1024L * 1024L

    data class Release(
        val available: Boolean,
        val currentVersion: String,
        val latestVersion: String,
        val downloadUrl: String?,
        val assetName: String?,
        val assetSize: Long,
        val notes: String,
        val releasePage: String,
        val error: String? = null
    )

    data class DownloadState(
        val status: Int,
        val progress: Int,
        val reason: Int = 0
    )

    enum class InstallResult { OPENED, PERMISSION_REQUIRED, INVALID_APK, FAILED }

    suspend fun checkLatest(): Release = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val current = BuildConfig.VERSION_NAME
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "NSiO-Android-Updater")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("GitHub merespons HTTP ${connection.responseCode}")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
                throw IllegalStateException("Rilis terbaru belum dipublikasikan sebagai versi stabil")
            }
            val tag = json.optString("tag_name").trim()
            val latest = tag.removePrefix("v").removePrefix("V")
            require(parseVersion(latest) != null) { "Format versi rilis tidak valid: $tag" }
            require(parseVersion(current) != null) { "Versi aplikasi terpasang tidak valid: $current" }
            val assets = json.optJSONArray("assets")
            val apks = (0 until (assets?.length() ?: 0))
                .mapNotNull { assets?.optJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            val newer = compareVersions(latest, current) > 0
            if (newer && apks.size != 1) {
                throw IllegalStateException("Rilis harus menyediakan tepat satu file APK; ditemukan ${apks.size}")
            }
            val asset = apks.singleOrNull()
            val url = asset?.optString("browser_download_url")?.takeIf(::isTrustedReleaseUrl)
            if (newer && url == null) throw IllegalStateException("Tautan APK rilis tidak valid atau tidak tersedia")
            val size = asset?.optLong("size") ?: 0L
            if (newer && (size <= 0L || size > MAX_APK_SIZE)) {
                throw IllegalStateException("Ukuran APK rilis tidak valid")
            }
            Release(
                available = newer,
                currentVersion = current,
                latestVersion = latest,
                downloadUrl = url,
                assetName = asset?.optString("name"),
                assetSize = size,
                notes = json.optString("body").trim().ifBlank { "Tidak ada catatan pembaruan." },
                releasePage = json.optString("html_url").takeIf { it.startsWith("https://github.com/andikune-ux/NSiO/releases/") }
                    ?: "https://github.com/andikune-ux/NSiO/releases"
            )
        } finally {
            connection.disconnect()
        }
    }

    fun startDownload(context: Context, release: Release): Long {
        val url = release.downloadUrl?.takeIf(::isTrustedReleaseUrl)
            ?: throw IllegalArgumentException("Tautan APK tidak tepercaya")
        val version = release.latestVersion
        val fileName = "NSiO-update-${version.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk"
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("Penyimpanan aplikasi tidak tersedia")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Folder unduhan tidak dapat dibuat")
        }
        File(directory, fileName).takeIf { it.exists() }?.delete()
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Pembaruan NSiO $version")
            setDescription("Mengunduh paket instalasi NSiO")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putString(KEY_VERSION, version)
            .apply()
        return id
    }

    fun pendingDownloadId(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_DOWNLOAD_ID, -1L).takeIf { it > 0L }

    fun pendingVersion(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_VERSION, null)

    fun clearPending(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_DOWNLOAD_ID).remove(KEY_VERSION).apply()
    }

    fun queryDownload(context: Context, id: Long): DownloadState? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val progress = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
            return DownloadState(status, progress, reason)
        }
    }

    fun isDownloadedApkValid(context: Context, version: String): Boolean {
        val file = downloadedFile(context, version)
        if (!file.isFile || file.length() < 256 * 1024L || file.length() > MAX_APK_SIZE) return false
        return runCatching {
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) == 4 } &&
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() && header[3] == 0x04.toByte() &&
                isCompatiblePackage(context, file, version)
        }.getOrDefault(false)
    }

    private fun downloadedFile(context: Context, version: String): File {
        val fileName = "NSiO-update-${version.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk"
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download")
        return File(directory, fileName)
    }

    private fun isCompatiblePackage(context: Context, apk: File, expectedVersion: String): Boolean {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        if (archive.packageName != BuildConfig.APPLICATION_ID || archive.versionName != expectedVersion) return false
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, flags)
        }
        val archiveCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
        val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installed.longVersionCode else installed.versionCode.toLong()
        if (archiveCode <= installedCode) return false
        return signerHashes(archive) == signerHashes(installed) && signerHashes(archive).isNotEmpty()
    }

    private fun signerHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        } ?: return emptySet()
        return signatures.map { signature ->
            Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()),
                Base64.NO_WRAP
            )
        }.toSet()
    }

    fun install(context: Context, version: String): InstallResult {
        val file = downloadedFile(context, version)
        if (!isDownloadedApkValid(context, version)) return InstallResult.INVALID_APK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching {
                context.startActivity(permissionIntent)
                InstallResult.PERMISSION_REQUIRED
            }.getOrDefault(InstallResult.FAILED)
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.ota.fileprovider", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            InstallResult.OPENED
        }.getOrDefault(InstallResult.FAILED)
    }

    internal fun isTrustedReleaseUrl(raw: String): Boolean {
        val url = runCatching { URL(raw) }.getOrNull() ?: return false
        return url.protocol.equals("https", true) &&
            (url.port == -1 || url.port == 443) &&
            url.userInfo.isNullOrEmpty() &&
            url.host.equals("github.com", true) &&
            url.path.startsWith("/andikune-ux/NSiO/releases/download/")
    }

    internal fun parseVersion(value: String): List<Int>? {
        val normalized = value.trim().removePrefix("v").removePrefix("V")
        if (!normalized.matches(Regex("\\d+(\\.\\d+)*"))) return null
        return normalized.split(".").map { it.toIntOrNull() ?: return null }
    }

    internal fun compareVersions(first: String, second: String): Int {
        val a = parseVersion(first) ?: return -1
        val b = parseVersion(second) ?: return 1
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }
}
