package com.example.data.telegram

/**
 * Safety gate for reclaiming a legacy Telegram copy.
 *
 * A metadata lookup or tiny cloud range probe is deliberately insufficient.
 * Removal is allowed only after the actual Media3 playback path has read data,
 * those bytes are confirmed present in the shared cache, the old path is inside
 * the managed legacy directory, and no other database row references it.
 */
internal object TelegramLegacyMigrationPolicy {
    fun mayDeleteLegacyCopy(
        actualPlaybackReadSucceeded: Boolean,
        cachedBytesVerified: Boolean,
        legacyPathIsCanonical: Boolean,
        hasOtherDatabaseReferences: Boolean
    ): Boolean =
        actualPlaybackReadSucceeded &&
            cachedBytesVerified &&
            legacyPathIsCanonical &&
            !hasOtherDatabaseReferences
}
