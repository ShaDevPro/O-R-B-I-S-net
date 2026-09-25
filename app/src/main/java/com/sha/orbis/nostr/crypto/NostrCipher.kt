package com.sha.orbis.nostr.crypto

import com.sha.orbis.security.Base64Compat
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement et déchiffrement de bout en bout (E2EE) conforme NIP-04.
 * Utilise Diffie-Hellman (ECDH secp256k1) + AES-256-CBC.
 */
object NostrCipher {

    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val ALGORITHM = "AES"

    /**
     * Chiffre un texte en clair avec la clé publique du destinataire.
     * Format de sortie : "<payloadBase64>?iv=<ivBase64>"
     */
    fun encrypt(
        plainText: String,
        myPrivKey32: ByteArray,
        theirPubKey32: ByteArray
    ): String {
        val sharedSecret = Secp256k1.computeSharedSecret(myPrivKey32, theirPubKey32)
        val secretKey = SecretKeySpec(sharedSecret, ALGORITHM)

        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))

        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payloadB64 = Base64Compat.encodeToString(encryptedBytes)
        val ivB64 = Base64Compat.encodeToString(iv)

        return "$payloadB64?iv=$ivB64"
    }

    /**
     * Déchiffre un texte chiffré au format NIP-04 avec la clé publique de l'expéditeur.
     */
    fun decrypt(
        encryptedContent: String,
        myPrivKey32: ByteArray,
        theirPubKey32: ByteArray
    ): String {
        val parts = encryptedContent.split("?iv=")
        if (parts.size != 2) {
            throw IllegalArgumentException("Format de payload chiffré NIP-04 invalide: attendu 'payload?iv=...'")
        }

        val payloadB64 = parts[0]
        val ivB64 = parts[1]

        val sharedSecret = Secp256k1.computeSharedSecret(myPrivKey32, theirPubKey32)
        val secretKey = SecretKeySpec(sharedSecret, ALGORITHM)

        val iv = Base64Compat.decode(ivB64)
        val encryptedBytes = Base64Compat.decode(payloadB64)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
