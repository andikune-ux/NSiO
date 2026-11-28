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
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "nsio_telegram_credentials", Context.MODE_PRIVATE
    )

    data class Credentials(val apiId: String, val apiHash: String)

    fun load(): Credentials? = runCatching { loadOrThrow() }.getOrNull()

    /**
     * Unlike load(), this preserves the actual failure reason for the connection flow.
     * Decryption must never create a replacement Keystore key: doing so would hide the
     * loss of the original key and make the encrypted TDLib session impossible to open.
     */
    fun loadOrThrow(): Credentials? {
        val payload = preferences.getString(KEY_PAYLOAD, null) ?: return null
        return try {
            val decoded = Base64.decode(payload, Base64.NO_WRAP)
            require(decoded.isNotEmpty()) { "Data kredensial Telegram kosong." }
            val ivSize = decoded[0].toInt() and 0xff
            require(ivSize in 12..16 && decoded.size > ivSize + 1) { "Format kredensial Telegram rusak." }
            val iv = decoded.copyOfRange(1, 1 + ivSize)
            val ciphertext = decoded.copyOfRange(1 + ivSize, decoded.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8))
            Credentials(json.getString("api_id"), json.getString("api_hash"))
        } catch (error: Exception) {
            throw IllegalStateException(
                "Kredensial Telegram tersimpan tetapi gagal dibuka. Kunci Android Keystore mungkin hilang/tidak cocok atau data terenkripsi rusak. Data tidak dihapus.",
                error
            )
        }
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
    fun loadOrCreateDatabaseEncryptionKey(allowCreate: Boolean = true): ByteArray {
        val existing = preferences.getString(KEY_TDLIB_DATABASE_KEY, null)
        if (existing != null) {
            try {
                val decoded = Base64.decode(existing, Base64.NO_WRAP)
                require(decoded.isNotEmpty()) { "Data kunci database Telegram kosong." }
                val ivSize = decoded[0].toInt() and 0xff
                require(ivSize in 12..16 && decoded.size > ivSize + 1) { "Format kunci database Telegram rusak." }
                val iv = decoded.copyOfRange(1, 1 + ivSize)
                val ciphertext = decoded.copyOfRange(1 + ivSize, decoded.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(128, iv))
                return cipher.doFinal(ciphertext).also { require(it.size == 32) }
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Kunci database Telegram lama tidak dapat dibuka. Database sesi tetap dipertahankan dan kunci tidak diganti.",
                    error
                )
            }
        }
        check(allowCreate) {
            "Database Telegram lama ditemukan tetapi kunci enkripsinya tidak ada. Pembuatan kunci baru dibatalkan agar sesi lama tidak tertimpa."
        }
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        storeDatabaseEncryptionKey(key)
        return key
    }

    fun clear() {
        // Keep the wrapped database key so a user can re-enter the same API credentials
        // without making an existing encrypted TDLib database permanently unreadable.
        preferences.edit().remove(KEY_PAYLOAD).commit()
    }

    /** Remove credentials and the database key when the profile explicitly releases Telegram. */
    fun clearAll() {
        preferences.edit().clear().commit()
        runCatching {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    /** Portable secrets are included only inside the PIN-encrypted profile backup envelope. */
    fun exportPortableData(): JSONObject? {
        val credentials = load() ?: return null
        val databaseKey = loadOrCreateDatabaseEncryptionKey()
        return JSONObject()
            .put("apiId", credentials.apiId)
            .put("apiHash", credentials.apiHash)
            .put("tdlibDatabaseKey", Base64.encodeToString(databaseKey, Base64.NO_WRAP))
    }

    /** Re-wraps the portable TDLib key with this installation's Android Keystore key. */
    fun importPortableData(payload: JSONObject) {
        val apiId = payload.optString("apiId")
        val apiHash = payload.optString("apiHash")
        val databaseKey = Base64.decode(payload.getString("tdlibDatabaseKey"), Base64.NO_WRAP)
        require(databaseKey.size == 32) { "Kunci database Telegram dalam backup tidak valid." }
        save(apiId, apiHash)
        storeDatabaseEncryptionKey(databaseKey)
    }

    private fun storeDatabaseEncryptionKey(key: ByteArray) {
        require(key.size == 32) { "Kunci database Telegram harus 256-bit." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(key)
        val payload = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
        check(preferences.edit().putString(
            KEY_TDLIB_DATABASE_KEY,
            Base64.encodeToString(payload, Base64.NO_WRAP)
        ).commit()) { "Gagal menyimpan kunci database Telegram." }
    }

    private fun getExistingKey(): java.security.Key {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? java.security.Key
            ?: throw IllegalStateException("Kunci Android Keystore Telegram tidak ditemukan. Kunci baru tidak dibuat untuk membuka data lama.")
    }

    private fun getOrCreateKey(): java.security.Key {
        runCatching { getExistingKey() }.getOrNull()?.let { return it }
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
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "nsio_telegram_api_credentials_v1"
        const val KEY_PAYLOAD = "encrypted_credentials"
        const val KEY_TDLIB_DATABASE_KEY = "encrypted_tdlib_database_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
