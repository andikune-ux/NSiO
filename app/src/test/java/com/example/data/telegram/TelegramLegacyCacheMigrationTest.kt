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
@Config(sdk = [35])
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
        // Media3 requires a locked hole span before startFile/commitFile.
        // Calling startFile directly leaves no CachedContent entry and throws NPE.
        val holeSpan = cache.startReadWrite(cacheKey, 0L, 4L)
        try {
            val cacheFile = cache.startFile(cacheKey, 0L, 4L)
            FileOutputStream(cacheFile).use { it.write(byteArrayOf(5, 6, 7, 8)) }
            cache.commitFile(cacheFile, 4L)
        } finally {
            cache.releaseHoleSpan(holeSpan)
        }

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

    @OptIn(UnstableApi::class)
    @Test
    fun cacheClearDoesNotDeleteLegacyCopyWithoutVerifiedPlayback() {
        val imported = File(context.filesDir, "telegram/imported").apply { mkdirs() }
        val legacy = File(imported, "not-yet-played.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val cacheKey = "telegram:-100123:459:792"
        val cache = DriveMediaCache.get(context)
        val holeSpan = cache.startReadWrite(cacheKey, 0L, 4L)
        try {
            val cacheFile = cache.startFile(cacheKey, 0L, 4L)
            FileOutputStream(cacheFile).use { it.write(byteArrayOf(5, 6, 7, 8)) }
            cache.commitFile(cacheFile, 4L)
        } finally {
            cache.releaseHoleSpan(holeSpan)
        }
        TelegramLegacyCacheMigration.remember(context, cacheKey, legacy)

        TelegramLegacyCacheMigration.clearPreviouslyVerifiedCopies(context)

        assertTrue("Cache data alone must not authorize deleting an unverified legacy video", legacy.exists())
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
