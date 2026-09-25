package com.sha.orbis.nostr

import com.sha.orbis.nostr.crypto.Bech32
import com.sha.orbis.nostr.crypto.Secp256k1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrCryptoTest {

    @Test
    fun testKeyPairGenerationAndBech32() {
        val keyPair = Secp256k1.generateKeyPair()
        assertNotNull(keyPair.privateKeyHex)
        assertNotNull(keyPair.publicKeyHex)
        assertEquals(64, keyPair.privateKeyHex.length)
        assertEquals(64, keyPair.publicKeyHex.length)

        assertTrue(keyPair.npub.startsWith("npub1"))
        assertTrue(keyPair.nsec.startsWith("nsec1"))

        val (hrpPub, decodedPubHex) = Bech32.decodeToHex(keyPair.npub)
        assertEquals("npub", hrpPub)
        assertEquals(keyPair.publicKeyHex, decodedPubHex)

        val (hrpPriv, decodedPrivHex) = Bech32.decodeToHex(keyPair.nsec)
        assertEquals("nsec", hrpPriv)
        assertEquals(keyPair.privateKeyHex, decodedPrivHex)
    }

    @Test
    fun testBip340SchnorrSignatureAndVerification() {
        val keyPair = Secp256k1.generateKeyPair()
        val message = "O R B I S net Sovereign E2EE Message"
        val messageHash = Secp256k1.sha256(message.toByteArray(Charsets.UTF_8))

        val sigHex = Secp256k1.sign(messageHash, keyPair.privateKey)
        assertEquals(128, sigHex.length) // 64 bytes hex

        val sigBytes = Bech32.hexToBytes(sigHex)
        val isValid = Secp256k1.verify(messageHash, keyPair.publicKey, sigBytes)
        assertTrue("La signature BIP-340 valide doit être acceptée", isValid)

        // Test with corrupted message hash
        val tamperedHash = messageHash.clone().apply { this[0] = (this[0].toInt() xor 0xFF).toByte() }
        val isTamperedValid = Secp256k1.verify(tamperedHash, keyPair.publicKey, sigBytes)
        assertFalse("La signature avec un hash altéré doit être rejetée", isTamperedValid)
    }

    @Test
    fun testEcdhSharedSecretSymmetry() {
        val alice = Secp256k1.generateKeyPair()
        val bob = Secp256k1.generateKeyPair()

        val secretAlice = Secp256k1.computeSharedSecret(alice.privateKey, bob.publicKey)
        val secretBob = Secp256k1.computeSharedSecret(bob.privateKey, alice.publicKey)

        assertEquals(
            "Le secret partagé ECDH dérivé par Alice et Bob doit être strictement identique",
            Bech32.bytesToHex(secretAlice),
            Bech32.bytesToHex(secretBob)
        )
    }

    @Test
    fun testNip04EncryptionAndDecryption() {
        val alice = Secp256k1.generateKeyPair()
        val bob = Secp256k1.generateKeyPair()

        val secretMessage = "{\"text\":\"Hello sovereign OrbisNet!\",\"audio\":null,\"ephemeral\":0}"
        
        // Alice encrypts for Bob
        val cipherText = com.sha.orbis.nostr.crypto.NostrCipher.encrypt(
            plainText = secretMessage,
            myPrivKey32 = alice.privateKey,
            theirPubKey32 = bob.publicKey
        )
        assertTrue(cipherText.contains("?iv="))

        // Bob decrypts from Alice
        val decrypted = com.sha.orbis.nostr.crypto.NostrCipher.decrypt(
            encryptedContent = cipherText,
            myPrivKey32 = bob.privateKey,
            theirPubKey32 = alice.publicKey
        )

        assertEquals("Le message déchiffré par Bob doit correspondre exactement au message envoyé par Alice", secretMessage, decrypted)
    }

    @Test
    fun testCanonicalNip01EventIdAndSignature() {
        val alice = Secp256k1.generateKeyPair()
        val tags = listOf(
            listOf("t", "orbisnet"),
            listOf("t", "orbisnet-invite"),
            listOf("p", alice.publicKeyHex),
            listOf("url", "https://orbisnet.app/download")
        )
        val content = "{\"type\":\"ORBISNET_INVITE\",\"avatar\":\"data:image/jpeg;base64,/9j/4AAQSkZJRg==\",\"path\":\"/usr/bin\"}"

        val event = com.sha.orbis.nostr.model.NostrEvent.createAndSign(
            pubkeyHex = alice.publicKeyHex,
            privkey32 = alice.privateKey,
            kind = 1,
            tags = tags,
            content = content,
            createdAtSeconds = 1700000000L
        )

        assertNotNull(event.id)
        assertEquals(64, event.id.length)
        assertTrue("Signature BIP-340 doit être valide", event.verifySignature())

        val canonicalJson = event.toCanonicalJson()
        assertFalse("Le JSON canonique ne doit JAMAIS contenir de slash échappé '\\/'", canonicalJson.contains("\\/"))
        assertTrue("Le JSON canonique doit contenir le slash direct '/'", canonicalJson.contains("https://orbisnet.app/download"))

        val expectedId = com.sha.orbis.nostr.model.NostrEvent.computeEventId(
            pubkeyHex = alice.publicKeyHex,
            createdAt = 1700000000L,
            kind = 1,
            tags = tags,
            content = content
        )
        assertEquals(expectedId, event.id)
    }
}

