package com.example.data.cache

import android.content.Context
import com.example.player.DriveMediaCache
import java.io.File

/**
 * Clears only disposable media caches. Profile/database/preferences, Drive
 * playlist URLs, Firebase authentication, Telegram credentials and TDLib data
 * are deliberately outside these paths.
 */
object AppCacheCleaner {
    fun clear(context: Context): Boolean {
        val app = context.applicationContext
        val driveCleared = DriveMediaCache.clear(app)
        val telegramDirectory = File(app.cacheDir, "telegram_media_cache")
        val telegramCleared = !telegramDirectory.exists() || runCatching {
            telegramDirectory.deleteRecursively()
        }.getOrDefault(false)
        return driveCleared && telegramCleared
    }
}
