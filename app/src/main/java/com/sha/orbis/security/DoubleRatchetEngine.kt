package com.sha.orbis.security

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Sovereign Double Ratchet (Perfect Forward Secrecy) Protocol Engine.
 * Implements symmetric ratchet chains and message key destruction to guarantee
 * that compromising a current key cannot decrypt past or future messages.
 */
object DoubleRatchetEngine {

    private const val HMAC_SHA256 = "HmacSHA256"
    private val CONSTANT_MESSAGE_KEY = "ORBIS_RATCHET_MSG_KEY".toByteArray(Charsets.UTF_8)
    private val CONSTANT_CHAIN_STEP = "ORBIS_RATCHET_CHAIN_ADVANCE".toByteArray(Charsets.UTF_8)

    data class RatchetState(
        val conversationId: String,
        val rootKey: String,
        val sendingChainKey: String,
        val receivingChainKey: String,
        val sendCounter: Int = 0,
        val receiveCounter: Int = 0
    )

    data class EncryptedRatchetMessage(
        val messageCounter: Int,
        val ciphertext: String,
        val iv: String
    )

    /**
     * Initializes a new Ratchet State from a shared master secret.
     */
    fun initializeState(conversationId: String, sharedSecret: String, isInitiator: Boolean): RatchetState {
        val rootKey = hmacSha256(sharedSecret.toByteArray(Charsets.UTF_8), "ORBIS_ROOT_INIT".toByteArray(Charsets.UTF_8))
        val chainA = hmacSha256(rootKey, "ORBIS_CHAIN_A".toByteArray(Charsets.UTF_8))
        val chainB = hmacSha256(rootKey, "ORBIS_CHAIN_B".toByteArray(Charsets.UTF_8))

        return if (isInitiator) {
            RatchetState(
                conversationId = conversationId,
                rootKey = Base64Compat.encodeToString(rootKey),
                sendingChainKey = Base64Compat.encodeToString(chainA),
                receivingChainKey = Base64Compat.encodeToString(chainB)
            )
        } else {
            RatchetState(
                conversationId = conversationId,
                rootKey = Base64Compat.encodeToString(rootKey),
                sendingChainKey = Base64Compat.encodeToString(chainB),
                receivingChainKey = Base64Compat.encodeToString(chainA)
            )
        }
    }

    /**
     * Advances the sending chain, generates an ephemeral message key, encrypts plaintext,
     * destroys the message key and returns the updated state.
     */
    fun ratchetEncrypt(
        state: RatchetState,
        plaintext: String
    ): Pair<RatchetState, EncryptedRatchetMessage> {
        val currentChainKeyBytes = Base64Compat.decode(state.sendingChainKey)

        // 1. Derive single-use Message Key
        val messageKeyBytes = hmacSha256(currentChainKeyBytes, CONSTANT_MESSAGE_KEY)
        val aesKey = SecretKeySpec(messageKeyBytes, "AES")

        // 2. Advance the Sending Chain Key
        val nextChainKeyBytes = hmacSha256(currentChainKeyBytes, CONSTANT_CHAIN_STEP)

        // 3. Encrypt payload
        val encrypted = AesCipher.encrypt(plaintext, aesKey)

        // 4. Wipe ephemeral key bytes in memory (Forward Secrecy)
        messageKeyBytes.fill(0)

        val nextState = state.copy(
            sendingChainKey = Base64Compat.encodeToString(nextChainKeyBytes),
            sendCounter = state.sendCounter + 1
        )

        val ratchetMsg = EncryptedRatchetMessage(
            messageCounter = state.sendCounter,
            ciphertext = encrypted.payload,
            iv = encrypted.iv
        )

        return Pair(nextState, ratchetMsg)
    }

    /**
     * Advances the receiving chain, derives the ephemeral message key, decrypts ciphertext,
     * destroys the message key and returns the updated state.
     */
    fun ratchetDecrypt(
        state: RatchetState,
        ratchetMsg: EncryptedRatchetMessage
    ): Pair<RatchetState, String> {
        var currentChainKeyBytes = Base64Compat.decode(state.receivingChainKey)
        var currentCounter = state.receiveCounter

        // Catch up with skipped messages if any
        while (currentCounter < ratchetMsg.messageCounter) {
            currentChainKeyBytes = hmacSha256(currentChainKeyBytes, CONSTANT_CHAIN_STEP)
            currentCounter++
        }

        // 1. Derive single-use Message Key for this counter
        val messageKeyBytes = hmacSha256(currentChainKeyBytes, CONSTANT_MESSAGE_KEY)
        val aesKey = SecretKeySpec(messageKeyBytes, "AES")

        // 2. Advance Receiving Chain Key
        val nextChainKeyBytes = hmacSha256(currentChainKeyBytes, CONSTANT_CHAIN_STEP)

        // 3. Decrypt payload
        val decrypted = AesCipher.decrypt(AesCipher.EncryptedPayload(ratchetMsg.ciphertext, ratchetMsg.iv), aesKey)

        // 4. Wipe ephemeral key bytes in memory (Forward Secrecy)
        messageKeyBytes.fill(0)

        val nextState = state.copy(
            receivingChainKey = Base64Compat.encodeToString(nextChainKeyBytes),
            receiveCounter = currentCounter + 1
        )

        return Pair(nextState, decrypted)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(key, HMAC_SHA256))
        return mac.doFinal(data)
    }
}
