package com.sha.orbis.nostr

import com.sha.orbis.call.OrbisCallManager
import com.sha.orbis.nostr.crypto.NostrCipher
import com.sha.orbis.nostr.crypto.Secp256k1
import com.sha.orbis.nostr.model.NostrEvent
import com.sha.orbis.nostr.protocol.NostrProtocolEngine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrCallSignalTest {

    @Test
    fun testCallSignalOfferBuildingAndDecryption() {
        val alicePair = Secp256k1.generateKeyPair()
        val bobPair = Secp256k1.generateKeyPair()

        val offerPayload = JSONObject().apply {
            put("callerPhone", "+33600000001")
            put("callerName", "Alice")
            put("safetyNumber", "4321")
            put("ip", "192.168.1.50")
            put("port", 16002)
        }

        val callId = "call_${System.currentTimeMillis()}"
        val event = NostrProtocolEngine.buildCallSignalEvent(
            myIdentity = alicePair,
            recipientPubKeyHex = bobPair.publicKeyHex,
            signalType = "OFFER",
            callId = callId,
            payloadJson = offerPayload
        )

        assertNotNull(event)
        assertEquals(NostrEvent.KIND_EPHEMERAL_CALL_SIGNAL, event.kind)
        assertTrue(event.tags.any { it.size >= 2 && it[0] == "p" && it[1].equals(bobPair.publicKeyHex, ignoreCase = true) })
        assertTrue(event.tags.any { it.size >= 2 && it[0] == "t" && it[1] == "orbisnet" })

        // Bob decrypts Alice's call signal
        val decrypted = NostrCipher.decrypt(
            encryptedContent = event.content,
            myPrivKey32 = bobPair.privateKey,
            theirPubKey32 = alicePair.publicKey
        )

        val json = JSONObject(decrypted)
        assertEquals("OFFER", json.getString("sigType"))
        assertEquals(callId, json.getString("callId"))
        assertEquals("+33600000001", json.getString("callerPhone"))
        assertEquals("Alice", json.getString("callerName"))
        assertEquals("4321", json.getString("safetyNumber"))
        assertEquals("192.168.1.50", json.getString("ip"))
        assertEquals(16002, json.getInt("port"))
    }

    @Test
    fun testCallSignalAnswerAcceptedAndDecryption() {
        val alicePair = Secp256k1.generateKeyPair()
        val bobPair = Secp256k1.generateKeyPair()

        val answerPayload = JSONObject().apply {
            put("action", "ACCEPTED")
            put("ip", "192.168.1.55")
            put("port", 16002)
        }

        val callId = "call_answer_test"
        val event = NostrProtocolEngine.buildCallSignalEvent(
            myIdentity = bobPair,
            recipientPubKeyHex = alicePair.publicKeyHex,
            signalType = "ANSWER",
            callId = callId,
            payloadJson = answerPayload
        )

        assertNotNull(event)
        assertEquals(NostrEvent.KIND_EPHEMERAL_CALL_SIGNAL, event.kind)

        // Alice decrypts Bob's answer
        val decrypted = NostrCipher.decrypt(
            encryptedContent = event.content,
            myPrivKey32 = alicePair.privateKey,
            theirPubKey32 = bobPair.publicKey
        )

        val json = JSONObject(decrypted)
        assertEquals("ANSWER", json.getString("sigType"))
        assertEquals("ACCEPTED", json.getString("action"))
        assertEquals(callId, json.getString("callId"))
        assertEquals("192.168.1.55", json.getString("ip"))
        assertEquals(16002, json.getInt("port"))
    }

    @Test
    fun testSasCodeDeterminismAndIndependence() {
        val phoneA = "+33611223344"
        val phoneB = "+33655667788"

        val sas1 = OrbisCallManager.computeSasCode(phoneA, phoneB)
        val sas2 = OrbisCallManager.computeSasCode(phoneB, phoneA)

        assertEquals("Le code SAS doit être symétrique et identique des deux côtés de l'appel", sas1, sas2)
        assertEquals(4, sas1.length)
        assertTrue(sas1.toInt() in 1000..9999)
    }
}
