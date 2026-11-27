package com.example

import android.content.Context
import android.os.SystemClock
import java.util.UUID

internal data class SessionSnapshot(
    val unlocked: Boolean,
    val profileLoggedIn: Boolean
)

internal object SessionTimeoutPolicy {
    const val TIMEOUT_MS = 30_000L

    fun shouldLock(backgroundAtElapsedMs: Long, nowElapsedMs: Long): Boolean {
        if (backgroundAtElapsedMs < 0L || nowElapsedMs < backgroundAtElapsedMs) return true
        return nowElapsedMs - backgroundAtElapsedMs >= TIMEOUT_MS
    }
}

/**
 * Keeps the hidden-calculator session alive only inside the current app process.
 * A process restart always requires the calculator access code again.
 */
internal object SessionLockManager {
    private const val PREFS = "nsio_session_guard"
    private const val KEY_PROCESS = "process_token"
    private const val KEY_UNLOCKED = "session_unlocked"
    private const val KEY_PROFILE_LOGGED_IN = "profile_logged_in"
    private const val KEY_BACKGROUND_AT = "background_at_elapsed_ms"

    private val processToken: String = UUID.randomUUID().toString()

    fun restore(context: Context): SessionSnapshot = resume(context)

    fun resume(context: Context): SessionSnapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sameProcess = prefs.getString(KEY_PROCESS, null) == processToken
        val unlocked = prefs.getBoolean(KEY_UNLOCKED, false)
        if (!sameProcess || !unlocked) {
            clear(prefs)
            return SessionSnapshot(false, false)
        }

        val backgroundAt = prefs.getLong(KEY_BACKGROUND_AT, -1L)
        if (backgroundAt >= 0L &&
            SessionTimeoutPolicy.shouldLock(backgroundAt, SystemClock.elapsedRealtime())
        ) {
            clear(prefs)
            return SessionSnapshot(false, false)
        }

        prefs.edit().remove(KEY_BACKGROUND_AT).apply()
        return SessionSnapshot(
            unlocked = true,
            profileLoggedIn = prefs.getBoolean(KEY_PROFILE_LOGGED_IN, false)
        )
    }

    fun markUnlocked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PROCESS, processToken)
            .putBoolean(KEY_UNLOCKED, true)
            .putBoolean(KEY_PROFILE_LOGGED_IN, false)
            .remove(KEY_BACKGROUND_AT)
            .apply()
    }

    fun markProfileLoggedIn(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PROFILE_LOGGED_IN, true)
            .apply()
    }

    fun markBackgrounded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_PROCESS, null) != processToken ||
            !prefs.getBoolean(KEY_UNLOCKED, false)
        ) return

        prefs.edit()
            .putLong(KEY_BACKGROUND_AT, SystemClock.elapsedRealtime())
            .apply()
    }

    private fun clear(prefs: android.content.SharedPreferences) {
        prefs.edit().clear().apply()
    }
}
