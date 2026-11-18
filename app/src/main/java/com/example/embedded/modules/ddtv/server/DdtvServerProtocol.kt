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
import java.util.Base64
import java.util.Locale
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DdtvCategory(
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
                    val categoryId = value.opt("category_id")
                        ?.takeUnless { it == JSONObject.NULL }
                        ?: value.opt("id")?.takeUnless { it == JSONObject.NULL }
                    if (categoryId != null) put(categoryId)
                }
            val name = sequenceOf("name", "title", "category_name", "label")
                .map { value.optString(it, "").trim() }
                .firstOrNull { it.isNotBlank() }
                ?: "Kategori"
            val videoType = value.optInt("video_type", value.optInt("type", 0))
            return DdtvCategory(name, videoType, ids.toString(), ids.length() > 0)
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
                Base64.getDecoder().decode(value.getString("key"))
            }.getOrElse { throw IOException("Kunci protokol DDTV tidak valid.", it) }
            if (key.size != 32) throw IOException("Panjang kunci AES DDTV tidak valid.")

            val publicKeyBytes = runCatching {
                Base64.getDecoder().decode(value.getString("publicKey"))
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
        return Base64.getEncoder().encodeToString(envelope)
    }

    fun decryptPayload(encoded: String, key: ByteArray): String {
        if (encoded.length > MAX_PLAINTEXT_BYTES) {
            throw IOException("Respons DDTV terlalu besar.")
        }
        val envelope = runCatching { Base64.getDecoder().decode(encoded) }
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
            val signatureBytes = runCatching { Base64.getDecoder().decode(signatureText) }
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
        if (host.endsWith(".") || host.endsWith(".local") ||
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
    fun resolve(video: JSONObject, detail: JSONObject): List<DdtvStream> {
        val code = DdtvServerUrlPolicy.validateVideoCode(video.optString("code", ""))
        val domains = linkedSetOf<String>()
        val domainArray = video.optJSONArray("domains")
        if (domainArray != null) {
            for (index in 0 until minOf(domainArray.length(), 32)) {
                domainArray.optString(index, "").takeIf { it.isNotBlank() }?.let(domains::add)
            }
        }
        video.optString("domain", "").takeIf { it.isNotBlank() }?.let(domains::add)
        if (domains.isEmpty()) throw IOException("Alamat streaming DDTV belum tersedia.")

        val signature = detail.optString("sig", "")
        if (signature.length > 16_384 || signature.contains('\r') || signature.contains('\n')) {
            throw IOException("Otorisasi streaming DDTV tidak valid.")
        }
        val headers = linkedMapOf("User-Agent" to "okhttp/4.12.0")
        if (signature.isNotEmpty()) headers["Authorization"] = signature

        val streams = domains.mapNotNull { rawDomain ->
            val base = runCatching {
                DdtvServerUrlPolicy.origin(
                    if (rawDomain.startsWith("https://", ignoreCase = true)) rawDomain
                    else "https://$rawDomain"
                )
            }.getOrNull() ?: return@mapNotNull null
            val url = "$base/$code/index.m3u8"
            val type = DdtvServerUrlPolicy.manifestType(url)
            if (type.isEmpty()) null else DdtvStream(url, type, headers.toMap())
        }
        if (streams.isEmpty()) throw IOException("Tidak ada link HLS/DASH untuk video ini.")
        return streams
    }
}
