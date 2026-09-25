package com.sha.orbis.backup

import android.content.Context
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.AccountProfile
import com.sha.orbis.security.AesCipher
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

/**
 * Sovereign P2P Identity & Key Migration Helper.
 * Enables zero-cloud, direct device-to-device account and cryptographic key migration.
 */
object P2pMigrationHelper {

    const val MIGRATION_HEADER = "ORBIS_P2P_MIGRATION_V1"

    data class MigrationResult(
        val success: Boolean,
        val accountId: String? = null,
        val userPhone: String? = null,
        val userName: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Generates a compact, encrypted JSON migration payload for QR Code or direct peer transfer.
     */
    fun exportAccountMigrationPayload(context: Context, pairingPassphrase: String): String? {
        return try {
            val sessionManager = SessionManager(context)
            val activeAccount = sessionManager.activeAccount ?: return null

            val migrationData = JSONObject().apply {
                put("type", "account_identity")
                put("timestamp", System.currentTimeMillis())
                put("account", activeAccount.toJson())
            }

            val key = deriveMigrationKey(pairingPassphrase)
            val encrypted = AesCipher.encrypt(migrationData.toString(), key)

            val root = JSONObject().apply {
                put("format", MIGRATION_HEADER)
                put("payload", encrypted.payload)
                put("iv", encrypted.iv)
            }
            root.toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Restores an account from a migration payload using the pairing passphrase.
     */
    fun importAccountMigrationPayload(
        context: Context,
        rawPayload: String,
        pairingPassphrase: String
    ): MigrationResult {
        return try {
            val root = JSONObject(rawPayload)
            val format = root.optString("format")
            if (format != MIGRATION_HEADER) {
                return MigrationResult(false, errorMessage = "Format de migration non reconnu.")
            }

            val payload = root.getString("payload")
            val iv = root.getString("iv")

            val key = deriveMigrationKey(pairingPassphrase)
            val decryptedStr = AesCipher.decrypt(AesCipher.EncryptedPayload(payload, iv), key)
            val migrationData = JSONObject(decryptedStr)

            val accountJson = migrationData.getJSONObject("account")
            val account = AccountProfile.fromJson(accountJson)

            val sessionManager = SessionManager(context)
            val existing = sessionManager.getAccounts().toMutableList()
            val existingIdx = existing.indexOfFirst { it.id == account.id || it.phoneNumber == account.phoneNumber }
            if (existingIdx >= 0) {
                existing[existingIdx] = account
            } else {
                existing.add(account)
            }

            sessionManager.saveAccounts(existing)
            sessionManager.activeAccountId = account.id
            sessionManager.isAuthenticated = true
            sessionManager.isOnboardingCompleted = true

            MigrationResult(
                success = true,
                accountId = account.id,
                userPhone = account.phoneNumber,
                userName = account.name
            )
        } catch (e: Exception) {
            MigrationResult(false, errorMessage = "Déchiffrement échoué : vérifiez le code secret.")
        }
    }

    private fun deriveMigrationKey(passphrase: String): javax.crypto.SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest("ORBIS_P2P_MIGRATION_${passphrase}".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }
}
