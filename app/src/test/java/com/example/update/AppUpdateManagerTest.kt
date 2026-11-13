package com.example.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.example.data.download.isVerifiedCompletedRange
import com.example.data.download.isStrongEntityTag
import com.example.data.download.selectResumeValidator
import com.example.data.download.calculateRetryProgress
import com.example.player.shouldUseNativePlayer
import com.example.player.normalizePlaybackUrl
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun parsesVersionTagsAndRejectsMalformedValues() {
        assertEquals(listOf(1, 0, 2), AppUpdateManager.parseVersion("v1.0.2"))
        assertEquals(listOf(1, 13, 203), AppUpdateManager.parseVersion("v.1.13.203"))
        assertEquals("1.13.203", AppUpdateManager.normalizeVersionTag("v.1.13.203"))
        assertEquals(listOf(1, 10, 0), AppUpdateManager.parseVersion("1.10.0"))
        assertNull(AppUpdateManager.parseVersion("v1.x.2"))
        assertNull(AppUpdateManager.parseVersion(""))
        assertNull(AppUpdateManager.parseVersion("1.999999999999999999999"))
    }

    @Test
    fun selectsHighestSemanticVersionInsteadOfGithubLatestReleaseOrder() {
        val releases = org.json.JSONArray(
            """[
              {"tag_name":"v1.0.1","draft":false,"prerelease":false},
              {"tag_name":"v.1.14.295","draft":false,"prerelease":false},
              {"tag_name":"v.1.14.300","draft":false,"prerelease":false},
              {"tag_name":"v.99.0.0-rc1","draft":false,"prerelease":true},
              {"tag_name":"v.100.0.0","draft":true,"prerelease":false},
              {"tag_name":"invalid","draft":false,"prerelease":false}
            ]"""
        )
        assertEquals(
            "v.1.14.300",
            AppUpdateManager.selectLatestStableRelease(releases)?.optString("tag_name")
        )
    }

    @Test
    fun returnsNoStableReleaseWhenOnlyDraftsPrereleasesOrInvalidTagsExist() {
        val releases = org.json.JSONArray(
            """[
              {"tag_name":"v.99.0.0","draft":true,"prerelease":false},
              {"tag_name":"v.98.0.0","draft":false,"prerelease":true},
              {"tag_name":"not-a-version","draft":false,"prerelease":false}
            ]"""
        )
        assertNull(AppUpdateManager.selectLatestStableRelease(releases))
    }

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(AppUpdateManager.compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(AppUpdateManager.compareVersions("1.0.3", "1.0.2") > 0)
        assertEquals(0, AppUpdateManager.compareVersions("1.0", "1.0.0"))
        assertTrue(AppUpdateManager.compareVersions("1.0.1", "1.0.2") < 0)
        assertTrue(AppUpdateManager.compareVersions("2.1.1", "1.31.999") > 0)
        assertTrue(AppUpdateManager.compareVersions("1.14.1", "1.13.999") > 0)
        assertTrue(AppUpdateManager.compareVersions("1.13.204", "1.13.203") > 0)
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
    @Test
    fun preservesKnownDownloadSizeAndProgressWhenRetrying() {
        val half = calculateRetryProgress(500L, 1_000L)
        assertEquals(50, half.percent)
        assertEquals(500L, half.downloadedBytes)
        assertEquals(1_000L, half.totalBytes)

        val unknownSize = calculateRetryProgress(500L, 0L)
        assertEquals(0, unknownSize.percent)
        assertEquals(500L, unknownSize.downloadedBytes)
        assertEquals(0L, unknownSize.totalBytes)

        val oversizedPartial = calculateRetryProgress(1_200L, 1_000L)
        assertEquals(99, oversizedPartial.percent)
        assertEquals(1_200L, oversizedPartial.downloadedBytes)
        assertEquals(1_000L, oversizedPartial.totalBytes)

        val negativeInput = calculateRetryProgress(-1L, -1L)
        assertEquals(0, negativeInput.percent)
        assertEquals(0L, negativeInput.downloadedBytes)
        assertEquals(0L, negativeInput.totalBytes)
    }


    @Test
    fun onlyUsesStrongEtagsForIfRangeAndFallsBackToLastModified() {
        assertTrue(isStrongEntityTag("\"v1\""))
        assertFalse(isStrongEntityTag("W/\"v1\""))
        assertFalse(isStrongEntityTag("v1"))
        assertFalse(isStrongEntityTag("\"v1\"tail\""))
        assertFalse(isStrongEntityTag("\"v1 value\""))
        assertFalse(isStrongEntityTag("\"v1\n\""))
        assertEquals("\"v1\"", selectResumeValidator(" \"v1\" ", "Wed, 21 Oct 2015 07:28:00 GMT"))
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", selectResumeValidator("W/\"v1\"", "Wed, 21 Oct 2015 07:28:00 GMT"))
        assertEquals("", selectResumeValidator("W/\"v1\"", ""))
    }

    @Test
    fun classifiesNativeAndWebViewPlaybackSourcesConsistently() {
        assertTrue(shouldUseNativePlayer("VIDEO", "https://cdn.example/video.mp4"))
        assertTrue(shouldUseNativePlayer("STREAM", "https://cdn.example/live.m3u8"))
        assertFalse(shouldUseNativePlayer("PHOTO", "https://cdn.example/poster.jpg"))
        assertFalse(shouldUseNativePlayer("PHOTO", "https://drive.google.com/file/d/id/preview"))
        assertTrue(shouldUseNativePlayer("VIDEO", "https://drive.google.com/file/d/id/preview"))
        assertEquals("https://drive.usercontent.google.com/download?id=abc_123-xyz&export=download&confirm=t", normalizePlaybackUrl("https://drive.google.com/file/d/abc_123-xyz/preview"))
        assertFalse(shouldUseNativePlayer("VIDEO", "   "))
    }

    @Test
    fun acceptsHttp416AsCompleteOnlyWhenSizeAndValidatorMatch() {
        assertTrue(
            isVerifiedCompletedRange(
                contentRange = "bytes */1048576",
                existingBytes = 1048576L,
                responseEtag = "\"v1\"",
                responseLastModified = "",
                savedEtag = "\"v1\"",
                savedLastModified = ""
            )
        )
        assertFalse(
            isVerifiedCompletedRange(
                contentRange = "bytes */1048576",
                existingBytes = 1048576L,
                responseEtag = "\"v2\"",
                responseLastModified = "",
                savedEtag = "\"v1\"",
                savedLastModified = ""
            )
        )
        assertFalse(
            isVerifiedCompletedRange(
                contentRange = "bytes */1048577",
                existingBytes = 1048576L,
                responseEtag = "\"v1\"",
                responseLastModified = "",
                savedEtag = "\"v1\"",
                savedLastModified = ""
            )
        )
        assertFalse(
            isVerifiedCompletedRange(
                contentRange = "bytes */1048576",
                existingBytes = 1048576L,
                responseEtag = "",
                responseLastModified = "",
                savedEtag = "",
                savedLastModified = ""
            )
        )
    }

}
