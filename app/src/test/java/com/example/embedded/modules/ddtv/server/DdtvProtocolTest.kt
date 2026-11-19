package com.example.embedded.modules.ddtv.server

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.Signature

class DdtvProtocolTest {
    private val aesKey = ByteArray(32) { (it + 3).toByte() }

    @Test
    fun aesCbcEnvelopePrefixesTheSixteenByteIv() {
        val iv = ByteArray(16) { (it * 7).toByte() }
        val plain = """{"method":"categories","lang":"id"}"""
        val encoded = DdtvProtocolCodec.encryptPayload(plain, aesKey, iv)
        val raw = DdtvBase64.decode(encoded)

        assertTrue(raw.copyOfRange(0, 16).contentEquals(iv))
        assertEquals(plain, DdtvProtocolCodec.decryptPayload(encoded, aesKey))
    }

    @Test
    fun signedResponseIsDecryptedAndVerifiedWithRsaSha256() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val plain = """{"categories":[{"id":1}]}"""
        val iv = ByteArray(16) { it.toByte() }
        val encrypted = DdtvProtocolCodec.encryptPayload(plain, aesKey, iv)
        val signer = Signature.getInstance("SHA256withRSA").apply {
            initSign(pair.private)
            update(plain.toByteArray(Charsets.UTF_8))
        }
        val response = JSONObject()
            .put("code", 200)
            .put("data", encrypted)
            .put("s", DdtvBase64.encode(signer.sign()))

        val decoded = DdtvProtocolCodec.decodeResponse(response, aesKey, pair.public) as JSONObject
        assertTrue(decoded.getJSONArray("categories").getJSONObject(0).getInt("id") == 1)
    }

    @Test
    fun invalidRsaSignatureIsRejected() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val plain = """{"ok":true}"""
        val encrypted = DdtvProtocolCodec.encryptPayload(plain, aesKey, ByteArray(16))
        val response = JSONObject()
            .put("code", 0)
            .put("data", encrypted)
            .put("s", DdtvBase64.encode(ByteArray(256)))

        var rejected = false
        try {
            DdtvProtocolCodec.decodeResponse(response, aesKey, pair.public)
        } catch (_: IOException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun categoryParserPreservesServerCategoryIdsAndVideoType() {
        val category = DdtvCategory.fromJson(
            JSONObject()
                .put("id", "film")
                .put("name", "Film")
                .put("video_type", 1)
                .put("category_id", JSONArray().put(12).put(18))
        )

        assertEquals("film", category.id)
        assertEquals("Film", category.name)
        assertEquals(1, category.videoType)
        assertTrue(category.related)
        assertEquals(12, category.categoryIds().getInt(0))
        assertEquals(18, category.categoryIds().getInt(1))
    }

    @Test
    fun streamResolverUsesExactPlaybackUrlReturnedByServer() {
        val streams = DdtvStreamResolver.resolve(
            JSONObject().put("code", "movie_123"),
            JSONObject()
                .put("sig", "signature-token")
                .put("data", JSONObject().put("play_url", "https://media.example.test/live/master.m3u8?token=abc"))
        )

        assertEquals(1, streams.size)
        assertEquals("https://media.example.test/live/master.m3u8?token=abc", streams.first().url)
        assertEquals("hls", streams.first().manifestType)
        assertEquals("okhttp/4.12.0", streams.first().headers["User-Agent"])
        assertEquals("signature-token", streams.first().headers["Authorization"])
    }

    @Test
    fun streamResolverSupportsNestedUrlArraysAndProgressiveMedia() {
        val streams = DdtvStreamResolver.resolve(
            JSONObject(),
            JSONObject().put(
                "sources",
                JSONArray().put(JSONObject().put("url", "https://cdn.example.test/movie.mp4?auth=xyz"))
            )
        )

        assertEquals(1, streams.size)
        assertEquals("https://cdn.example.test/movie.mp4?auth=xyz", streams.first().url)
        assertEquals("progressive", streams.first().manifestType)
    }

    @Test
    fun streamResolverExtractsUrlsFromMacCmsVodPlayUrlStrings() {
        val streams = DdtvStreamResolver.resolve(
            JSONObject(),
            JSONObject().put(
                "data",
                JSONObject().put(
                    "vod_play_url",
                    "Main\$https://cdn.example.test/video/master.m3u8?token=abc#Backup\$https://backup.example.test/video.mp4"
                )
            )
        )

        assertEquals(2, streams.size)
        assertEquals("https://cdn.example.test/video/master.m3u8?token=abc", streams[0].url)
        assertEquals("hls", streams[0].manifestType)
        assertEquals("https://backup.example.test/video.mp4", streams[1].url)
        assertEquals("progressive", streams[1].manifestType)
    }

    @Test
    fun streamResolverDoesNotInventAnIndexPlaylistWhenServerReturnsNoUrl() {
        var rejected = false
        try {
            DdtvStreamResolver.resolve(
                JSONObject()
                    .put("code", "movie_123")
                    .put("domains", JSONArray().put("media.example.test")),
                JSONObject().put("sig", "signature-token")
            )
        } catch (_: IOException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun urlPolicyRejectsNonHttpsCredentialsAndNonRootBootstrapUrls() {
        assertFalse(DdtvServerUrlPolicy.isPublicHttps("http://media.example.test"))
        assertFalse(DdtvServerUrlPolicy.isPublicHttps("https://user:pass@media.example.test"))
        assertFalse(DdtvServerUrlPolicy.isPublicHttps("https://media.example.test:444"))
        assertEquals("https://media.example.test", DdtvServerUrlPolicy.origin("https://media.example.test/"))
        var rejected = false
        try {
            DdtvServerUrlPolicy.origin("https://media.example.test/api")
        } catch (_: IOException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
