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
import java.util.Base64

class DdtvProtocolTest {
    private val aesKey = ByteArray(32) { (it + 3).toByte() }

    @Test
    fun aesCbcEnvelopePrefixesTheSixteenByteIv() {
        val iv = ByteArray(16) { (it * 7).toByte() }
        val plain = """{"method":"categories","lang":"id"}"""
        val encoded = DdtvProtocolCodec.encryptPayload(plain, aesKey, iv)
        val raw = Base64.getDecoder().decode(encoded)

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
            .put("s", Base64.getEncoder().encodeToString(signer.sign()))

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
            .put("s", Base64.getEncoder().encodeToString(ByteArray(256)))

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
                .put("name", "Film")
                .put("video_type", 1)
                .put("category_id", JSONArray().put(12).put(18))
        )

        assertEquals("Film", category.name)
        assertEquals(1, category.videoType)
        assertTrue(category.related)
        assertEquals(12, category.categoryIds().getInt(0))
        assertEquals(18, category.categoryIds().getInt(1))
    }

    @Test
    fun streamResolverBuildsHlsUrlAndAuthorizationHeader() {
        val streams = DdtvStreamResolver.resolve(
            JSONObject()
                .put("code", "movie_123")
                .put("domains", JSONArray().put("media.example.test")),
            JSONObject().put("sig", "signature-token")
        )

        assertEquals(1, streams.size)
        assertEquals("https://media.example.test/movie_123/index.m3u8", streams.first().url)
        assertEquals("hls", streams.first().manifestType)
        assertEquals("okhttp/4.12.0", streams.first().headers["User-Agent"])
        assertEquals("signature-token", streams.first().headers["Authorization"])
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
