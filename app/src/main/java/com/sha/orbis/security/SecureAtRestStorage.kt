package com.sha.orbis.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Sovereign At-Rest Storage Encryption Engine.
 * Utilizes the Hardware-backed Android KeyStore (AES-256-GCM) to protect all sensitive
 * local files against physical extraction, ADB dumping, and forensic analysis.
 */
object SecureAtRestStorage {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "OrbisMasterAtRestKey_v1"
    private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    @Synchronized
    private fun getOrCreateMasterKey(): SecretKey {
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts plaintext bytes using hardware-backed AES-256-GCM.
     * Output format: [12-byte IV] + [Ciphertext + 16-byte Auth Tag]
     */
    fun encrypt(plainBytes: ByteArray): ByteArray {
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainBytes)

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return combined
    }

    /**
     * Decrypts hardware-encrypted AES-256-GCM bytes.
     */
    fun decrypt(encryptedBytes: ByteArray): ByteArray {
        if (encryptedBytes.size < GCM_IV_LENGTH) {
            throw IllegalArgumentException("Payload trop court pour AES-GCM.")
        }

        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, encryptedBytes, 0, GCM_IV_LENGTH)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        return cipher.doFinal(encryptedBytes, GCM_IV_LENGTH, encryptedBytes.size - GCM_IV_LENGTH)
    }

    /**
     * Writes encrypted text atomically to a local file.
     */
    fun writeEncryptedFile(file: File, text: String) {
        val encrypted = encrypt(text.toByteArray(Charsets.UTF_8))
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.writeBytes(encrypted)
        if (tempFile.renameTo(file).not()) {
            file.delete()
            tempFile.renameTo(file)
        }
    }

    /**
     * Reads and decrypts a local file. Falls back gracefully if plaintext exists (auto-migration).
     */
    fun readEncryptedFile(file: File): String? {
        if (!file.exists()) return null
        val rawBytes = file.readBytes()
        return try {
            val decrypted = decrypt(rawBytes)
            String(decrypted, Charsets.UTF_8)
        } catch (_: Exception) {
            // Fallback: If legacy unencrypted JSON, read plaintext and re-encrypt
            val plainText = String(rawBytes, Charsets.UTF_8)
            if (plainText.trim().startsWith("{") || plainText.trim().startsWith("[")) {
                try {
                    writeEncryptedFile(file, plainText)
                } catch (_: Exception) {}
                plainText
            } else {
                null
            }
        }
    }
}
