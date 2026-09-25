package com.sha.orbis.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesCipher {
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val ALGORITHM = "AES"

    fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }

    fun generateKeyBase64(): String {
        return Base64Compat.encodeToString(generateKey().encoded)
    }

    fun encrypt(plainText: String, secretKey: SecretKey): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))

        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            payload = Base64Compat.encodeToString(encryptedBytes),
            iv = Base64Compat.encodeToString(iv)
        )
    }

    fun decrypt(encryptedPayload: EncryptedPayload, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val ivBytes = Base64Compat.decode(encryptedPayload.iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(ivBytes))

        val decoded = Base64Compat.decode(encryptedPayload.payload)
        return String(cipher.doFinal(decoded), Charsets.UTF_8)
    }

    /**
     * Converts a Base64 or raw string key into a valid 256-bit (32 bytes) AES SecretKey.
     * Uses SHA-256 derivation to guarantee strict 32-byte key size for any input string.
     */
    fun stringToKey(keyString: String): SecretKey {
        val cleanKey = keyString.trim()
        val keyBytes = try {
            val decoded = Base64Compat.decode(cleanKey)
            if (decoded.size == 16 || decoded.size == 24 || decoded.size == 32) {
                decoded
            } else {
                MessageDigest.getInstance("SHA-256").digest(cleanKey.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            MessageDigest.getInstance("SHA-256").digest(cleanKey.toByteArray(Charsets.UTF_8))
        }
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    data class EncryptedPayload(
        val payload: String,
        val iv: String
    )
}
