package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-bound encryption for short local PIN values.
 * Legacy plaintext values are accepted for one-time migration and should be re-saved encrypted.
 */
object PinVault {
    private const val KEY_ALIAS = "nsio_local_pin_v1"
    private const val PREFIX = "enc:v1:"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    fun encrypt(context: Context, value: String): String {
        if (value.isBlank() || isEncrypted(value)) return value
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(context: Context, value: String): String {
        if (value.isBlank() || !isEncrypted(value)) return value
        return try {
            val payload = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            require(payload.size > 12) { "PIN terenkripsi tidak valid" }
            val iv = payload.copyOfRange(0, 12)
            val encrypted = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: KeyPermanentlyInvalidatedException) {
            // Android invalidated the device-bound key (for example after a lock-screen
            // security change). The ciphertext cannot be recovered; remove the dead alias
            // so the app can create a fresh key and explicitly reset affected local PINs.
            runCatching {
                KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
