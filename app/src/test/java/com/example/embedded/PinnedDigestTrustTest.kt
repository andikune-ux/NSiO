package com.example.embedded

import com.example.embedded.security.PinnedDigestTrust
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedDigestTrustTest {
    @Test
    fun rejectsArtifactsWhenNoDigestIsPinned() {
        val artifact = File.createTempFile("nsio-module-", ".nmod")
        artifact.writeText("module payload")
        artifact.deleteOnExit()

        assertFalse(PinnedDigestTrust().isTrusted(artifact))
    }

    @Test
    fun acceptsOnlyAnExactSha256Match() {
        val artifact = File.createTempFile("nsio-module-", ".nmod")
        artifact.writeText("reviewed module payload")
        artifact.deleteOnExit()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(artifact.readBytes())
            .joinToString("") { "%02x".format(it) }

        assertTrue(PinnedDigestTrust(setOf(digest)).isTrusted(artifact))
        assertFalse(PinnedDigestTrust(setOf("0".repeat(64))).isTrusted(artifact))
    }
}
