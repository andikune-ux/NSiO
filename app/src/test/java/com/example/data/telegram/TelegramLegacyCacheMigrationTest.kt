package com.example.data.telegram

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.media3.common.util.UnstableApi
import com.example.player.DriveMediaCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TelegramLegacyCacheMigrationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun legacyFileIsRetainedUntilActualPlaybackAndCachedSpanAreVerified() {
        val imported = File(context.filesDir, "telegram/imported").apply { mkdirs() }
        val legacy = File(imported, "old-video.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val cacheKey = "telegram:-100123:456:789"

        TelegramLegacyCacheMigration.remember(context, cacheKey, legacy)

        assertFalse(
            TelegramLegacyCacheMigration.cleanupIfCached(
                context,
                cacheKey,
                actualPlaybackReadSucceeded = false
            )
        )
        assertTrue("Legacy video must remain until real cache data exists", legacy.exists())
    }

    @OptIn(UnstableApi::class)
    @Test
    fun deletesLegacyCopyOnlyAfterACommittedCacheSpanAndSuccessfulRead() {
        val imported = File(context.filesDir, "telegram/imported").apply { mkdirs() }
        val legacy = File(imported, "verified-video.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val cacheKey = "telegram:-100123:458:791"
        val cache = DriveMediaCache.get(context)
        val cacheFile = cache.startFile(cacheKey, 0L, 4L)
        FileOutputStream(cacheFile).use { it.write(byteArrayOf(5, 6, 7, 8)) }
        cache.commitFile(cacheFile, 4L)

        TelegramLegacyCacheMigration.remember(context, cacheKey, legacy)

        assertTrue(
            TelegramLegacyCacheMigration.cleanupIfCached(
                context,
                cacheKey,
                actualPlaybackReadSucceeded = true
            )
        )
        assertFalse(legacy.exists())
    }

    @Test
    fun fileOutsideManagedImportedDirectoryIsNeverRegisteredForDeletion() {
        val outside = File(context.filesDir, "manual-downloads/keep.mp4").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9, 8, 7))
        }
        val cacheKey = "telegram:-100123:457:790"

        TelegramLegacyCacheMigration.remember(context, cacheKey, outside)

        assertFalse(
            TelegramLegacyCacheMigration.cleanupIfCached(
                context,
                cacheKey,
                actualPlaybackReadSucceeded = true
            )
        )
        assertTrue(outside.exists())
    }
}
