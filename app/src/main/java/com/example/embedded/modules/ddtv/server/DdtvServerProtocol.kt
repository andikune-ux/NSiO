package com.example.embedded.modules.ddtv.server

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import java.util.Locale
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DdtvCategory(
    val id: String,
    val name: String,
    val videoType: Int,
    val categoryIdsJson: String,
    val related: Boolean
) {
    fun categoryIds(): JSONArray = JSONArray(categoryIdsJson)

    companion object {
        fun fromJson(value: JSONObject): DdtvCategory {
            val ids = value.optJSONArray("category_id")
                ?: value.optJSONArray("category_ids")
                ?: JSONArray().apply {
                    val categoryId = value.opt("category_id")?.takeUnless { it == JSONObject.NULL }
                    if (categoryId != null) put(categoryId)
                }
            val name = sequenceOf("name", "title", "category_name", "label")
                .map { value.optString(it, "").trim() }
                .firstOrNull { it.isNotBlank() }
                ?: "Kategori"
            val id = value.optString("id", value.optString("category", "")).trim()
            val videoType = value.optInt("video_type", value.optInt("type", 0))
            return DdtvCategory(id, name, videoType, ids.toString(), ids.length() > 0)
        }
    }
}

data class DdtvStream(
    val url: String,
    val manifestType: String,
    val headers: Map<String, String>
)

internal data class DdtvProtocolProfile(
    val version: Int,
    val envelope: String,
    val key: ByteArray,
    val publicKey: PublicKey,
    val bootstrap: List<String>
) {
    companion object {
        fun fromJson(value: JSONObject): DdtvProtocolProfile {
            val version = value.optInt("version", -1)
            val envelope = value.optString("envelope", "")
            if (version != 1 || envelope != "iv+ciphertext") {
                throw IOException("Versi atau format protokol DDTV tidak didukung.")
            }

            val key = runCatching {
                DdtvBase64.decode(value.getString("key"))
            }.getOrElse { throw IOException("Kunci protokol DDTV tidak valid.", it) }
            if (key.size != 32) throw IOException("Panjang kunci AES DDTV tidak valid.")

            val publicKeyBytes = runCatching {
                DdtvBase64.decode(value.getString("publicKey"))
            }.getOrElse { throw IOException("Public key DDTV tidak valid.", it) }
            val publicKey = runCatching {
                KeyFactory.getInstance("RSA")
                    .generatePublic(X509EncodedKeySpec(publicKeyBytes))
            }.getOrElse { throw IOException("Public key RSA DDTV tidak dapat dibaca.", it) }
            if ((publicKey as? RSAPublicKey)?.modulus?.bitLength()?.let { it >= 2048 } != true) {
                throw IOException("Ukuran public key RSA DDTV tidak memenuhi syarat.")
            }

            val rawBootstrap = value.optJSONArray("bootstrap")
                ?: throw IOException("Daftar bootstrap DDTV tidak ditemukan.")
            if (rawBootstrap.length() !in 1..8) {
                throw IOException("Jumlah domain bootstrap DDTV tidak valid.")
            }
            val bootstrap = (0 until rawBootstrap.length()).map { index ->
                DdtvServerUrlPolicy.origin(rawBootstrap.getString(index))
            }.distinct()
            if (bootstrap.isEmpty()) throw IOException("Domain bootstrap DDTV kosong.")

            return DdtvProtocolProfile(version, envelope, key, publicKey, bootstrap)
        }
    }
}

internal object DdtvBase64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val reverse = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val output = StringBuilder(((input.size + 2) / 3) * 4)
        var index = 0
        while (index < input.size) {
            val first = input[index].toInt() and 0xff
            val hasSecond = index + 1 < input.size
            val hasThird = index + 2 < input.size
            val second = if (hasSecond) input[index + 1].toInt() and 0xff else 0
            val third = if (hasThird) input[index + 2].toInt() and 0xff else 0

            output.append(ALPHABET[first ushr 2])
            output.append(ALPHABET[((first and 0x03) shl 4) or (second ushr 4)])
            output.append(if (hasSecond) ALPHABET[((second and 0x0f) shl 2) or (third ushr 6)] else '=')
            output.append(if (hasThird) ALPHABET[third and 0x3f] else '=')
            index += 3
        }
        return output.toString()
    }

    fun decode(input: String): ByteArray {
        val compact = input.filterNot(Char::isWhitespace)
        if (compact.isEmpty()) return ByteArray(0)
        if (compact.length % 4 == 1) throw IllegalArgumentException("Invalid Base64 length")
        val normalized = when (compact.length % 4) {
            2 -> compact + "=="
            3 -> compact + "="
            else -> compact
        }
        val padding = when {
            normalized.endsWith("==") -> 2
            normalized.endsWith("=") -> 1
            else -> 0
        }
        val output = ByteArray(normalized.length / 4 * 3 - padding)
        var outputIndex = 0
        var inputIndex = 0
        while (inputIndex < normalized.length) {
            val lastBlock = inputIndex + 4 == normalized.length
            val c0 = normalized[inputIndex]
            val c1 = normalized[inputIndex + 1]
            val c2 = normalized[inputIndex + 2]
            val c3 = normalized[inputIndex + 3]
            val v0 = valueOf(c0)
            val v1 = valueOf(c1)
            val v2 = if (c2 == '=') 0 else valueOf(c2)
            val v3 = if (c3 == '=') 0 else valueOf(c3)

            if (c0 == '=' || c1 == '=' ||
                (!lastBlock && (c2 == '=' || c3 == '=')) ||
                (c2 == '=' && c3 != '=') ||
                (c2 == '=' && padding != 2) ||
                (c3 == '=' && padding == 0)
            ) throw IllegalArgumentException("Invalid Base64 padding")

            if (outputIndex < output.size) {
                output[outputIndex++] = ((v0 shl 2) or (v1 ushr 4)).toByte()
            }
            if (c2 != '=' && outputIndex < output.size) {
                output[outputIndex++] = (((v1 and 0x0f) shl 4) or (v2 ushr 2)).toByte()
            }
            if (c3 != '=' && outputIndex < output.size) {
                output[outputIndex++] = (((v2 and 0x03) shl 6) or v3).toByte()
            }
            inputIndex += 4
        }
        return output
    }

    private fun valueOf(char: Char): Int {
        if (char.code >= reverse.size || reverse[char.code] < 0) {
            throw IllegalArgumentException("Invalid Base64 character")
        }
        return reverse[char.code]
    }
}

internal object DdtvProtocolCodec {
    private const val IV_BYTES = 16
    private const val AES_BLOCK_BYTES = 16
    private const val MAX_PLAINTEXT_BYTES = 4 * 1024 * 1024

    fun encodeRequest(payload: JSONObject, key: ByteArray): JSONObject {
        val iv = ByteArray(IV_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        return JSONObject().put(
            "data",
            encryptPayload(payload.toString().toByteArray(StandardCharsets.UTF_8), key, iv)
        )
    }

    fun encryptPayload(plainText: String, key: ByteArray, iv: ByteArray): String =
        encryptPayload(plainText.toByteArray(StandardCharsets.UTF_8), key, iv)

    fun encryptPayload(plainText: ByteArray, key: ByteArray, iv: ByteArray): String {
        if (key.size != 32 || iv.size != IV_BYTES) {
            throw IOException("Parameter enkripsi DDTV tidak valid.")
        }
        if (plainText.size > MAX_PLAINTEXT_BYTES) {
            throw IOException("Permintaan DDTV terlalu besar.")
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(iv)
        )
        val encrypted = cipher.doFinal(plainText)
        val envelope = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, envelope, 0, iv.size)
        System.arraycopy(encrypted, 0, envelope, iv.size, encrypted.size)
        return DdtvBase64.encode(envelope)
    }

    fun decryptPayload(encoded: String, key: ByteArray): String {
        if (encoded.length > MAX_PLAINTEXT_BYTES) {
            throw IOException("Respons DDTV terlalu besar.")
        }
        val envelope = runCatching { DdtvBase64.decode(encoded) }
            .getOrElse { throw IOException("Envelope respons DDTV bukan Base64 yang valid.", it) }
        if (envelope.size < IV_BYTES + AES_BLOCK_BYTES ||
            (envelope.size - IV_BYTES) % AES_BLOCK_BYTES != 0
        ) {
            throw IOException("Respons DDTV tidak lengkap.")
        }
        if (key.size != 32) throw IOException("Kunci AES DDTV tidak valid.")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(envelope, 0, IV_BYTES)
        )
        val plain = cipher.doFinal(envelope, IV_BYTES, envelope.size - IV_BYTES)
        if (plain.size > MAX_PLAINTEXT_BYTES) throw IOException("Respons DDTV terlalu besar.")
        return String(plain, StandardCharsets.UTF_8)
    }

    fun decodeResponse(
        response: JSONObject,
        key: ByteArray,
        publicKey: PublicKey
    ): Any {
        val code = response.optInt("code", 15)
        if (code != 0 && code != 200) {
            throw IOException("Layanan DDTV menolak permintaan. Coba lagi nanti.")
        }

        val plainText = decryptPayload(response.getString("data"), key)
        val signatureText = response.optString("s", "")
        if (signatureText.isNotEmpty()) {
            val signatureBytes = runCatching { DdtvBase64.decode(signatureText) }
                .getOrElse { throw IOException("Tanda tangan respons DDTV bukan Base64 yang valid.", it) }
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(plainText.toByteArray(StandardCharsets.UTF_8))
            if (!verifier.verify(signatureBytes)) {
                throw IOException("Tanda tangan respons DDTV tidak valid.")
            }
        }
        return JSONTokener(plainText).nextValue()
    }
}

internal object DdtvServerUrlPolicy {
    private val ipv4Pattern = Pattern.compile("[0-9.]+")
    private val safeVideoCode = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,159}")

    fun origin(raw: String): String {
        if (!isPublicHttps(raw)) throw IOException("Alamat layanan DDTV tidak valid.")
        val uri = runCatching { URI(raw) }
            .getOrElse { throw IOException("Alamat layanan DDTV tidak valid.", it) }
        if (uri.rawQuery != null || uri.rawFragment != null ||
            (uri.path.isNotEmpty() && uri.path != "/")
        ) {
            throw IOException("Alamat bootstrap DDTV harus berupa domain HTTPS.")
        }
        return "https://${uri.host.lowercase(Locale.ROOT)}"
    }

    fun isPublicHttps(raw: String): Boolean {
        if (raw.isBlank() || raw.length > 16_384 ||
            raw.contains('\r') || raw.contains('\n') || raw.contains('\\')
        ) return false
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null ||
            uri.rawFragment != null ||
            (uri.port != -1 && uri.port != 443)
        ) return false

        val host = uri.host.lowercase(Locale.ROOT)
        if (!host.contains(".") || host.endsWith(".") || host.endsWith(".local") ||
            host.endsWith(".localhost") || host.endsWith(".internal") ||
            host == "localhost" || host.contains('%') || host.contains(':')
        ) return false

        if (ipv4Pattern.matcher(host).matches()) {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
            return isPublicAddress(address)
        }
        return host.matches(Regex("[a-z0-9.-]+")) &&
            !host.startsWith(".") && !host.contains("..")
    }

    fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return false

        val bytes = address.address
        if (address is Inet4Address && bytes.size == 4) {
            val a = bytes[0].toInt() and 0xff
            val b = bytes[1].toInt() and 0xff
            val c = bytes[2].toInt() and 0xff
            if (a == 0 || a == 10 || a == 127 || a >= 224) return false
            if (a == 100 && b in 64..127) return false
            if (a == 169 && b == 254) return false
            if (a == 172 && b in 16..31) return false
            if (a == 192 && (b == 0 || b == 168)) return false
            if (a == 192 && b == 88 && c == 99) return false
            if (a == 198 && b in 18..19) return false
            if (a == 198 && b == 51 && c == 100) return false
            if (a == 203 && b == 0 && c == 113) return false
        } else if (address is Inet6Address && bytes.isNotEmpty()) {
            val first = bytes[0].toInt() and 0xff
            if ((first and 0xfe) == 0xfc || first == 0xff) return false
        }
        return true
    }

    fun validateVideoCode(raw: String): String {
        if (!safeVideoCode.matcher(raw).matches() || raw.contains("..")) {
            throw IOException("Identitas video DDTV tidak valid.")
        }
        return raw
    }

    fun manifestType(raw: String): String {
        if (!isPublicHttps(raw)) return ""
        val path = runCatching { URI(raw).path.lowercase(Locale.ROOT) }.getOrDefault("")
        return when {
            path.endsWith(".m3u8") -> "hls"
            path.endsWith(".mpd") -> "dash"
            else -> ""
        }
    }
}

internal object DdtvStreamResolver {
    private val urlKeys = setOf(
        "url", "play_url", "playurl", "video_url", "videourl",
        "stream_url", "streamurl", "m3u8", "hls", "dash",
        "play", "src", "file", "link", "source", "sources",
        "urls", "streams", "play_link", "playlink", "file_url",
        "source_url", "download_url", "video_path", "url_m3u8",
        "url_mp4", "playlist", "manifest", "master", "address"
    )
    private val progressiveExtensions = setOf(".mp4", ".m4v", ".mkv", ".webm", ".ts", ".flv", ".mp3", ".m4a")

    fun resolve(video: JSONObject, detail: JSONObject): List<DdtvStream> {
        val signature = detail.optString("sig", "").trim()
        if (signature.length > 16_384 || signature.contains('\r') || signature.contains('\n')) {
            throw IOException("Otorisasi streaming DDTV tidak valid.")
        }
        val headers = linkedMapOf("User-Agent" to "okhttp/4.12.0")
        if (signature.isNotEmpty()) headers["Authorization"] = signature

        val bases = linkedSetOf<String>()
        val domainArray = video.optJSONArray("domains")
        if (domainArray != null) {
            for (index in 0 until minOf(domainArray.length(), 32)) {
                addBase(domainArray.optString(index, ""), bases)
            }
        }
        addBase(video.optString("domain", ""), bases)

        // Use playback URLs returned by the server first. Do not fabricate a
        // /<code>/index.m3u8 path: the DDTV API does not guarantee that route.
        val candidates = linkedSetOf<String>()
        collectPlaybackUrls(detail, candidates, 0)
        collectPlaybackUrls(video, candidates, 0)

        val resolved = linkedSetOf<String>()
        candidates.forEach { raw ->
            val absolute = resolveCandidate(raw, bases) ?: return@forEach
            resolved.add(absolute)
        }
        if (resolved.isEmpty()) {
            throw IOException("Respons detail DDTV tidak memuat URL streaming yang valid. Server tidak memberikan link pemutaran.")
        }

        return resolved.mapNotNull { url ->
            val type = playbackType(url)
            if (type.isEmpty()) null else DdtvStream(url, type, headers.toMap())
        }.also {
            if (it.isEmpty()) throw IOException("URL dari server DDTV bukan format media yang didukung.")
        }
    }

    private fun addBase(raw: String, output: MutableSet<String>) {
        val value = raw.trim()
        if (value.isBlank()) return
        val candidate = if (value.startsWith("https://", true)) value else "https://$value"
        runCatching { DdtvServerUrlPolicy.origin(candidate) }.getOrNull()?.let(output::add)
    }

    private fun collectPlaybackUrls(value: Any?, output: MutableSet<String>, depth: Int) {
        if (depth > 6 || output.size >= 32) return
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext() && output.size < 32) {
                    val key = keys.next()
                    val child = value.opt(key)
                    if (key.lowercase(Locale.ROOT) in urlKeys) {
                        when (child) {
                            is String -> output.add(child.trim())
                            is JSONArray, is JSONObject -> collectPlaybackUrls(child, output, depth + 1)
                        }
                    } else if (child is JSONObject || child is JSONArray) {
                        collectPlaybackUrls(child, output, depth + 1)
                    } else if (child is String && child.trim().startsWith("https://", true)) {
                        // Some DDTV payload revisions use an unlabelled URL field.
                        output.add(child.trim())
                    }
                }
            }
            is JSONArray -> for (index in 0 until minOf(value.length(), 64)) {
                collectPlaybackUrls(value.opt(index), output, depth + 1)
            }
        }
    }

    private fun resolveCandidate(raw: String, bases: Set<String>): String? {
        val value = raw.trim()
        if (value.isBlank() || value.length > 16_384 ||
            value.contains('\r') || value.contains('\n') || value.startsWith("//")
        ) return null
        val absolute = if (value.startsWith("https://", true)) {
            value
        } else {
            val base = bases.firstOrNull() ?: return null
            runCatching { URI(base).resolve(value).toString() }.getOrNull() ?: return null
        }
        return absolute.takeIf(DdtvServerUrlPolicy::isPublicHttps)
    }

    private fun playbackType(url: String): String {
        val manifest = DdtvServerUrlPolicy.manifestType(url)
        if (manifest.isNotEmpty()) return manifest
        val path = runCatching { URI(url).path.lowercase(Locale.ROOT) }.getOrDefault("")
        return if (progressiveExtensions.any(path::endsWith)) "progressive" else ""
    }
}
