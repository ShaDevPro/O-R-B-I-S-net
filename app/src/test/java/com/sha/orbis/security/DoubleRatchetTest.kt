package com.sha.orbis.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleRatchetTest {

    @Test
    fun ratchet_encryption_and_decryption_works() {
        val sharedMasterSecret = "ORBIS_SHARED_TEST_SECRET_2026"
        val convId = "conv_alice_bob_1"

        // Alice (initiator) and Bob (receiver)
        var aliceState = DoubleRatchetEngine.initializeState(convId, sharedMasterSecret, isInitiator = true)
        var bobState = DoubleRatchetEngine.initializeState(convId, sharedMasterSecret, isInitiator = false)

        val plaintext1 = "Bonjour Bob, message chiffré #1 avec Perfect Forward Secrecy !"

        // Alice encrypts message 1
        val (newAliceState1, encryptedMsg1) = DoubleRatchetEngine.ratchetEncrypt(aliceState, plaintext1)
        aliceState = newAliceState1

        assertEquals(0, encryptedMsg1.messageCounter)
        assertNotEquals(plaintext1, encryptedMsg1.ciphertext)

        // Bob decrypts message 1
        val (newBobState1, decrypted1) = DoubleRatchetEngine.ratchetDecrypt(bobState, encryptedMsg1)
        bobState = newBobState1

        assertEquals(plaintext1, decrypted1)
        assertEquals(1, bobState.receiveCounter)

        // Alice encrypts message 2
        val plaintext2 = "Deuxième message chiffré #2 avec ratchet avancé."
        val (newAliceState2, encryptedMsg2) = DoubleRatchetEngine.ratchetEncrypt(aliceState, plaintext2)
        aliceState = newAliceState2

        assertEquals(1, encryptedMsg2.messageCounter)
        assertNotEquals(encryptedMsg1.ciphertext, encryptedMsg2.ciphertext)

        // Bob decrypts message 2
        val (newBobState2, decrypted2) = DoubleRatchetEngine.ratchetDecrypt(bobState, encryptedMsg2)
        bobState = newBobState2

        assertEquals(plaintext2, decrypted2)
        assertEquals(2, bobState.receiveCounter)
    }

    @Test
    fun ratchet_different_counters_produce_unique_ciphertexts() {
        val sharedMasterSecret = "ORBIS_SHARED_TEST_SECRET_2026"
        val convId = "conv_test_uniqueness"

        var aliceState = DoubleRatchetEngine.initializeState(convId, sharedMasterSecret, isInitiator = true)

        val sameText = "Exact same message content"

        val (state1, msg1) = DoubleRatchetEngine.ratchetEncrypt(aliceState, sameText)
        val (_, msg2) = DoubleRatchetEngine.ratchetEncrypt(state1, sameText)

        // Even with identical plaintext, forward secrecy ratchet generates different ciphertexts
        assertNotEquals(msg1.ciphertext, msg2.ciphertext)
    }
}
