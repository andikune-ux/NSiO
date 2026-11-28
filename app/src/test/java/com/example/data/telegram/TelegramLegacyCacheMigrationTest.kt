package com.example.data.telegram

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
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
