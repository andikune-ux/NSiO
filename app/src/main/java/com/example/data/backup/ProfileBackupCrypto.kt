package com.example.data.backup

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable PIN-based encryption; unlike PinVault, this key is not tied to Android Keystore. */
internal object ProfileBackupCrypto {
    private const val ITERATIONS = 600_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun encrypt(plainJson: String, pin: String): String {
        require(pin.matches(Regex("\\d{4}"))) { "PIN profil tidak valid. Backup dibatalkan." }
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("backupType", "PROFILE_BACKUP_ENCRYPTED")
            put("encryption", "PBKDF2-HMAC-SHA256+A256GCM")
            put("formatVersion", 1)
            put("iterations", ITERATIONS)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }.toString(2)
    }

    fun isEncrypted(json: JSONObject): Boolean =
        json.optString("backupType") == "PROFILE_BACKUP_ENCRYPTED"

    fun decrypt(envelope: JSONObject, pin: String): String {
        require(pin.matches(Regex("\\d{4}"))) { "Masukkan PIN profil sumber yang terdiri dari 4 digit." }
        require(envelope.optInt("formatVersion") == 1) { "Format enkripsi backup belum didukung." }
        val iterations = envelope.optInt("iterations", -1)
        require(iterations in 100_000..2_000_000) { "Parameter enkripsi backup tidak valid." }
        val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
        val nonce = Base64.decode(envelope.getString("nonce"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        require(salt.size == 16 && nonce.size == 12 && ciphertext.size >= 16) {
            "Struktur file backup terenkripsi tidak valid."
        }
        return try {
            val key = deriveKey(pin, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalArgumentException("PIN salah atau file backup rusak. Data profil tidak diubah.")
        }
    }

    private fun deriveKey(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
