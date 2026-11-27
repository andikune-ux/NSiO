package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-local encrypted storage for Telegram API credentials.
 * Credentials are never synchronized to Firestore or written to BuildConfig.
 */
class TelegramApiCredentialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "nsio_telegram_credentials", Context.MODE_PRIVATE
    )

    data class Credentials(val apiId: String, val apiHash: String)

    fun load(): Credentials? {
        val payload = preferences.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching {
            val decoded = Base64.decode(payload, Base64.NO_WRAP)
            val ivSize = decoded[0].toInt() and 0xff
            require(ivSize in 12..16 && decoded.size > ivSize + 1)
            val iv = decoded.copyOfRange(1, 1 + ivSize)
            val ciphertext = decoded.copyOfRange(1 + ivSize, decoded.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8))
            Credentials(json.getString("api_id"), json.getString("api_hash"))
        }.getOrNull()
    }

    fun save(apiId: String, apiHash: String) {
        require(apiId.toLongOrNull()?.let { it > 0L } == true) { "API ID harus berupa angka positif." }
        require(apiHash.matches(Regex("^[A-Fa-f0-9]{32}$"))) { "API Hash harus terdiri dari 32 karakter heksadesimal." }
        val json = JSONObject().put("api_id", apiId).put("api_hash", apiHash)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json.toString().toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        val payload = byteArrayOf(iv.size.toByte()) + iv + encrypted
        check(preferences.edit().putString(KEY_PAYLOAD, Base64.encodeToString(payload, Base64.NO_WRAP)).commit()) {
            "Gagal menyimpan kredensial Telegram."
        }
    }

    /**
     * Returns a stable 256-bit TDLib database key. The key itself is encrypted with
     * the same Android Keystore AES-GCM key as the API credentials.
     */
    fun loadOrCreateDatabaseEncryptionKey(): ByteArray {
        val existing = preferences.getString(KEY_TDLIB_DATABASE_KEY, null)
        if (existing != null) {
            val decoded = Base64.decode(existing, Base64.NO_WRAP)
            val ivSize = decoded[0].toInt() and 0xff
            require(ivSize in 12..16 && decoded.size > ivSize + 1)
            val iv = decoded.copyOfRange(1, 1 + ivSize)
            val ciphertext = decoded.copyOfRange(1 + ivSize, decoded.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            return cipher.doFinal(ciphertext).also { require(it.size == 32) }
        }
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(key)
        val payload = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
        check(preferences.edit().putString(
            KEY_TDLIB_DATABASE_KEY,
            Base64.encodeToString(payload, Base64.NO_WRAP)
        ).commit()) { "Gagal menyimpan kunci database Telegram." }
        return key
    }

    fun clear() {
        preferences.edit().remove(KEY_PAYLOAD).remove(KEY_TDLIB_DATABASE_KEY).commit()
    }

    private fun getOrCreateKey(): java.security.Key {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? java.security.Key)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        generator.generateKey()
        return keyStore.getKey(KEY_ALIAS, null)
    }

    private companion object {
        const val KEY_ALIAS = "nsio_telegram_api_credentials_v1"
        const val KEY_PAYLOAD = "encrypted_credentials"
        const val KEY_TDLIB_DATABASE_KEY = "encrypted_tdlib_database_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
