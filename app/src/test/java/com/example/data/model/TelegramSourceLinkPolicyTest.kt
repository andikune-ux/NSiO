package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TelegramSourceLinkPolicyTest {
    @Test
    fun parsesPublicChannelLink() {
        val result = TelegramSourceLinkPolicy.parse("https://t.me/ExampleChannel")
        assertEquals("https://t.me/ExampleChannel", result.url)
        assertEquals("TELEGRAM_CHAT", result.sourceType)
    }

    @Test
    fun parsesPublicPostAndDropsTrackingQueryAndFragment() {
        val result = TelegramSourceLinkPolicy.parse("https://t.me/ExampleChannel/123?single#share")
        assertEquals("https://t.me/ExampleChannel/123", result.url)
        assertEquals("TELEGRAM_POST", result.sourceType)
    }

    @Test
    fun normalizesPublicPreviewPostLink() {
        val result = TelegramSourceLinkPolicy.parse("https://t.me/s/ExampleChannel/123")
        assertEquals("https://t.me/ExampleChannel/123", result.url)
        assertEquals("TELEGRAM_POST", result.sourceType)
    }

    @Test
    fun acceptsPrivateInviteLinkWithoutTreatingItAsPost() {
        val result = TelegramSourceLinkPolicy.parse("https://t.me/+AbCdEf123")
        assertEquals("https://t.me/+AbCdEf123", result.url)
        assertEquals("TELEGRAM_CHAT", result.sourceType)
    }

    @Test
    fun acceptsLegacyPrivateInviteLink() {
        val result = TelegramSourceLinkPolicy.parse("https://t.me/joinchat/AbCdEf123")
        assertEquals("TELEGRAM_CHAT", result.sourceType)
    }

    @Test
    fun rejectsNonTelegramHostsAndNonHttpsLinks() {
        assertThrows(IllegalArgumentException::class.java) {
            TelegramSourceLinkPolicy.parse("https://example.com/channel")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TelegramSourceLinkPolicy.parse("http://t.me/channel")
        }
    }

    @Test
    fun rejectsTelegramUtilityLinksAndRoot() {
        assertThrows(IllegalArgumentException::class.java) {
            TelegramSourceLinkPolicy.parse("https://t.me/share/url")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TelegramSourceLinkPolicy.parse("https://t.me/")
        }
    }
}
