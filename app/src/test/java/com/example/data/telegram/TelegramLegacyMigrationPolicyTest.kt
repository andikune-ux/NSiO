package com.example.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramLegacyMigrationPolicyTest {
    @Test
    fun cloudProbeAloneMustNeverAuthorizeLegacyFileDeletion() {
        assertFalse(
            TelegramLegacyMigrationPolicy.mayDeleteLegacyCopy(
                actualPlaybackReadSucceeded = false,
                cachedBytesVerified = false,
                legacyPathIsCanonical = true,
                hasOtherDatabaseReferences = false
            )
        )
    }

    @Test
    fun actualPlaybackAndCachedBytesAreBothRequired() {
        assertFalse(
            TelegramLegacyMigrationPolicy.mayDeleteLegacyCopy(
                actualPlaybackReadSucceeded = true,
                cachedBytesVerified = false,
                legacyPathIsCanonical = true,
                hasOtherDatabaseReferences = false
            )
        )
        assertTrue(
            TelegramLegacyMigrationPolicy.mayDeleteLegacyCopy(
                actualPlaybackReadSucceeded = true,
                cachedBytesVerified = true,
                legacyPathIsCanonical = true,
                hasOtherDatabaseReferences = false
            )
        )
    }

    @Test
    fun legacyFileIsRetainedWhenPathIsUnsafeOrStillReferenced() {
        assertFalse(
            TelegramLegacyMigrationPolicy.mayDeleteLegacyCopy(
                actualPlaybackReadSucceeded = true,
                cachedBytesVerified = true,
                legacyPathIsCanonical = false,
                hasOtherDatabaseReferences = false
            )
        )
        assertFalse(
            TelegramLegacyMigrationPolicy.mayDeleteLegacyCopy(
                actualPlaybackReadSucceeded = true,
                cachedBytesVerified = true,
                legacyPathIsCanonical = true,
                hasOtherDatabaseReferences = true
            )
        )
    }
}
