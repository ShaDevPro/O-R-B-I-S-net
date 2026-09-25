package com.sha.orbis.storage

import com.sha.orbis.security.AesCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class VaultBackupEngineTest {

    @Test
    fun pbkdf2_key_derivation_is_deterministic_with_same_salt() {
        val passphrase = "MasterSecurePassphrase2026!"
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        val key1 = VaultBackupEngine.deriveKeyPbkdf2(passphrase, salt, 1000)
        val key2 = VaultBackupEngine.deriveKeyPbkdf2(passphrase, salt, 1000)

        assertEquals(key1.algorithm, key2.algorithm)
        assertTrue(key1.encoded.contentEquals(key2.encoded))
    }

    @Test
    fun pbkdf2_key_derivation_differs_with_different_salt() {
        val passphrase = "MasterSecurePassphrase2026!"
        val salt1 = ByteArray(16) { 1 }
        val salt2 = ByteArray(16) { 2 }

        val key1 = VaultBackupEngine.deriveKeyPbkdf2(passphrase, salt1, 1000)
        val key2 = VaultBackupEngine.deriveKeyPbkdf2(passphrase, salt2, 1000)

        assertFalse(key1.encoded.contentEquals(key2.encoded))
    }

    @Test
    fun aes_vault_encryption_round_trip() {
        val payload = """{"format":"ORBIS_SOVEREIGN_VAULT_V3","version":3,"accounts":[{"id":"acc_1","name":"Alice"}]}"""
        val passphrase = "MySecretPassphrase"
        val salt = ByteArray(16) { 42 }

        val key = VaultBackupEngine.deriveKeyPbkdf2(passphrase, salt, 1000)
        val encrypted = AesCipher.encrypt(payload, key)

        assertNotNull(encrypted.payload)
        assertNotNull(encrypted.iv)

        val decrypted = AesCipher.decrypt(encrypted, key)
        assertEquals(payload, decrypted)
    }

    @Test
    fun nostr_identity_payload_in_vault_round_trip() {
        val keyPair = com.sha.orbis.nostr.crypto.Secp256k1.generateKeyPair()
        val nostrJson = org.json.JSONObject().apply {
            put("privateKeyHex", keyPair.privateKeyHex)
            put("publicKeyHex", keyPair.publicKeyHex)
            put("npub", keyPair.npub)
            put("nsec", keyPair.nsec)
        }

        val vaultPayload = org.json.JSONObject().apply {
            put("format", "ORBIS_SOVEREIGN_VAULT_V3")
            put("version", 3)
            put("nostrIdentity", nostrJson)
        }.toString()

        val passphrase = "MasterSecureNostrPassphrase2026!"
        val salt = ByteArray(16) { 77 }
        val key = VaultBackupEngine.deriveKeyPbkdf2(passphrase, salt, 1000)
        val encrypted = AesCipher.encrypt(vaultPayload, key)
        val decrypted = AesCipher.decrypt(encrypted, key)

        val restoredRoot = org.json.JSONObject(decrypted)
        val restoredNostr = restoredRoot.getJSONObject("nostrIdentity")

        assertEquals(keyPair.privateKeyHex, restoredNostr.getString("privateKeyHex"))
        assertEquals(keyPair.publicKeyHex, restoredNostr.getString("publicKeyHex"))
        assertEquals(keyPair.npub, restoredNostr.getString("npub"))
        assertEquals(keyPair.nsec, restoredNostr.getString("nsec"))
    }

    @Test
    fun backup_stats_captures_nostr_identity_restored() {
        val stats = VaultBackupEngine.BackupStats(
            accountsCount = 1,
            contactsCount = 12,
            conversationsCount = 5,
            messagesCount = 88,
            nostrIdentityRestored = true
        )
        assertTrue(stats.nostrIdentityRestored)
        assertEquals(1, stats.accountsCount)
        assertEquals(12, stats.contactsCount)
        assertEquals(88, stats.messagesCount)
    }

    private fun assertFalse(condition: Boolean) {
        org.junit.Assert.assertFalse(condition)
    }
}
