package com.sha.orbis.nostr.identity

import android.content.Context
import androidx.core.content.edit
import com.sha.orbis.nostr.crypto.Bech32
import com.sha.orbis.nostr.crypto.Secp256k1
import com.sha.orbis.nostr.model.NostrEvent

/**
 * Gestionnaire de l'identité souveraine Nostr de l'utilisateur.
 * Assure le stockage sécurisé de la clé privée (nsec) et de la clé publique (npub),
 * ainsi que la signature des événements et le calcul du secret partagé ECDH pour le chiffrement E2EE.
 */
class NostrIdentityManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "orbis_nostr_identity"
        private const val KEY_PRIV_KEY_HEX = "nostr_priv_key_hex"
        private const val KEY_PUB_KEY_HEX = "nostr_pub_key_hex"

        @Volatile
        private var INSTANCE: NostrIdentityManager? = null

        fun getInstance(context: Context): NostrIdentityManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NostrIdentityManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun getAccountPrefs(accountId: String? = null): android.content.SharedPreferences {
        val rawId = accountId?.takeIf { it.isNotBlank() } ?: try {
            com.sha.orbis.data.SessionManager(context).activeAccountId
        } catch (_: Exception) { "" }
        val clean = rawId.filter { it.isLetterOrDigit() || it == '_' }
        val prefsName = if (clean.isNotBlank()) "orbis_nostr_identity_$clean" else PREFS_NAME
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    /**
     * Récupère l'identité active ou génère une nouvelle paire de clés souveraine secp256k1 pour le compte.
     */
    @Synchronized
    fun getOrCreateIdentity(accountId: String? = null): Secp256k1.KeyPair {
        val currentPrefs = getAccountPrefs(accountId)
        val privHex = currentPrefs.getString(KEY_PRIV_KEY_HEX, null)
        val pubHex = currentPrefs.getString(KEY_PUB_KEY_HEX, null)

        if (!privHex.isNullOrBlank() && !pubHex.isNullOrBlank()) {
            return Secp256k1.KeyPair(
                privateKey = Bech32.hexToBytes(privHex),
                publicKey = Bech32.hexToBytes(pubHex)
            )
        }

        // Migration legacy uniquement pour le compte primaire
        try {
            val sessionManager = com.sha.orbis.data.SessionManager(context)
            val accounts = sessionManager.getAccounts()
            val activeId = accountId ?: sessionManager.activeAccountId
            val isPrimary = accounts.firstOrNull()?.id == activeId || accounts.isEmpty()
            if (isPrimary) {
                val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val legPriv = legacyPrefs.getString(KEY_PRIV_KEY_HEX, null)
                val legPub = legacyPrefs.getString(KEY_PUB_KEY_HEX, null)
                if (!legPriv.isNullOrBlank() && !legPub.isNullOrBlank()) {
                    currentPrefs.edit {
                        putString(KEY_PRIV_KEY_HEX, legPriv)
                        putString(KEY_PUB_KEY_HEX, legPub)
                    }
                    return Secp256k1.KeyPair(
                        privateKey = Bech32.hexToBytes(legPriv),
                        publicKey = Bech32.hexToBytes(legPub)
                    )
                }
            }
        } catch (_: Exception) {}

        // Génération d'une nouvelle identité souveraine isolée pour ce compte
        val newKeyPair = Secp256k1.generateKeyPair()
        currentPrefs.edit {
            putString(KEY_PRIV_KEY_HEX, newKeyPair.privateKeyHex)
            putString(KEY_PUB_KEY_HEX, newKeyPair.publicKeyHex)
        }
        return newKeyPair
    }

    val publicKeyHex: String
        get() = getOrCreateIdentity().publicKeyHex

    val npub: String
        get() = getOrCreateIdentity().npub

    val nsec: String
        get() = getOrCreateIdentity().nsec

    /**
     * Signe un événement Nostr avec la clé privée de l'utilisateur.
     */
    fun signEvent(
        kind: Int,
        tags: List<List<String>> = emptyList(),
        content: String = "",
        createdAtSeconds: Long = System.currentTimeMillis() / 1000L
    ): NostrEvent {
        val identity = getOrCreateIdentity()
        return NostrEvent.createAndSign(
            pubkeyHex = identity.publicKeyHex,
            privkey32 = identity.privateKey,
            kind = kind,
            tags = tags,
            content = content,
            createdAtSeconds = createdAtSeconds
        )
    }

    /**
     * Calcule le secret partagé Diffie-Hellman (ECDH) avec un interlocuteur.
     */
    fun computeSharedSecret(recipientPubKeyHex: String): ByteArray {
        val identity = getOrCreateIdentity()
        val recipientBytes = Bech32.hexToBytes(recipientPubKeyHex)
        return Secp256k1.computeSharedSecret(identity.privateKey, recipientBytes)
    }

    /**
     * Exporte les clés de l'identité Nostr sous forme d'objet JSON pour la sauvegarde du coffre.
     */
    fun exportIdentityJson(): org.json.JSONObject {
        val identity = getOrCreateIdentity()
        return org.json.JSONObject().apply {
            put("privateKeyHex", identity.privateKeyHex)
            put("publicKeyHex", identity.publicKeyHex)
            put("npub", identity.npub)
            put("nsec", identity.nsec)
        }
    }

    /**
     * Restaure une identité souveraine Nostr à partir de clés hexadécimales secp256k1.
     */
    @Synchronized
    fun importIdentity(privateKeyHex: String, publicKeyHex: String): Boolean {
        return try {
            if (privateKeyHex.isBlank() || publicKeyHex.isBlank()) return false
            Bech32.hexToBytes(privateKeyHex)
            Bech32.hexToBytes(publicKeyHex)

            getAccountPrefs().edit {
                putString(KEY_PRIV_KEY_HEX, privateKeyHex)
                putString(KEY_PUB_KEY_HEX, publicKeyHex)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
