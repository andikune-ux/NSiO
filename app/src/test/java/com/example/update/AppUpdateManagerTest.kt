package com.example.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun parsesVersionTagsAndRejectsMalformedValues() {
        assertEquals(listOf(1, 0, 2), AppUpdateManager.parseVersion("v1.0.2"))
        assertEquals(listOf(1, 10, 0), AppUpdateManager.parseVersion("1.10.0"))
        assertNull(AppUpdateManager.parseVersion("v1.x.2"))
        assertNull(AppUpdateManager.parseVersion(""))
        assertNull(AppUpdateManager.parseVersion("1.999999999999999999999"))
    }

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(AppUpdateManager.compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(AppUpdateManager.compareVersions("1.0.3", "1.0.2") > 0)
        assertEquals(0, AppUpdateManager.compareVersions("1.0", "1.0.0"))
        assertTrue(AppUpdateManager.compareVersions("1.0.1", "1.0.2") < 0)
    }

    @Test
    fun onlyAcceptsOfficialHttpsReleaseAssetUrls() {
        assertTrue(AppUpdateManager.isTrustedReleaseUrl(
            "https://github.com/andikune-ux/NSiO/releases/download/v1.0.2/NSiO-v1.0.2-debug.apk"
        ))
        assertFalse(AppUpdateManager.isTrustedReleaseUrl(
            "http://github.com/andikune-ux/NSiO/releases/download/v1.0.2/app.apk"
        ))
        assertFalse(AppUpdateManager.isTrustedReleaseUrl(
            "https://evil.example/andikune-ux/NSiO/releases/download/v1.0.2/app.apk"
        ))
        assertFalse(AppUpdateManager.isTrustedReleaseUrl(
            "https://github.com/attacker/NSiO/releases/download/v1.0.2/app.apk"
        ))
        assertFalse(AppUpdateManager.isTrustedReleaseUrl(
            "https://github.com:444/andikune-ux/NSiO/releases/download/v1.0.2/app.apk"
        ))
    }
}
