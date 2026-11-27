package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTimeoutPolicyTest {
    @Test
    fun returningBeforeThirtySecondsKeepsSessionUnlocked() {
        assertFalse(SessionTimeoutPolicy.shouldLock(10_000L, 39_999L))
    }

    @Test
    fun returningAtThirtySecondsLocksSession() {
        assertTrue(SessionTimeoutPolicy.shouldLock(10_000L, 40_000L))
    }

    @Test
    fun returningAfterThirtySecondsLocksSession() {
        assertTrue(SessionTimeoutPolicy.shouldLock(10_000L, 40_001L))
    }

    @Test
    fun invalidOrReversedElapsedTimestampFailsClosed() {
        assertTrue(SessionTimeoutPolicy.shouldLock(-1L, 10_000L))
        assertTrue(SessionTimeoutPolicy.shouldLock(20_000L, 10_000L))
    }
}
