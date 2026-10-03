package com.sha.orbis.security

import java.util.Base64

class GroupKeyManager {
    fun generateGroupKey(): String = AesCipher.generateKeyBase64()

    fun encryptForMember(groupKey: String, memberPublicKeyBase64: String, data: String): String {
        val key = AesCipher.stringToKey(groupKey)
        val encrypted = AesCipher.encrypt(data, key)
        val signature = RsaSigner.sign(encrypted.payload + encrypted.iv, memberPublicKeyBase64)
        return encrypted.payload + ":" + encrypted.iv + ":" + signature
    }

    fun decryptForMember(groupKey: String, encryptedPayload: String): String {
        val parts = encryptedPayload.split(":")
        if (parts.size < 2) return ""
        val key = AesCipher.stringToKey(groupKey)
        return AesCipher.decrypt(
            AesCipher.EncryptedPayload(payload = parts[0], iv = parts[1]),
            key
        )
    }
}
