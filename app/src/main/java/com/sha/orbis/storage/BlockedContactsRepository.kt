package com.sha.orbis.storage

import android.content.Context
import android.content.Intent
import com.sha.orbis.model.BlockedContact
import com.sha.orbis.notification.OrbisEventBus
import com.sha.orbis.social.PresenceHelper
import org.json.JSONArray
import java.io.File

class BlockedContactsRepository(
    private val context: Context,
    private val accountId: String? = null
) {
    private val currentAccountId: String = accountId?.takeIf { it.isNotBlank() } ?: try {
        com.sha.orbis.data.SessionManager(context).activeAccountId
    } catch (_: Exception) { "" }

    private val storageFile: File by lazy {
        AccountStorageManager.getAccountFile(context, "blocked_contacts.json", currentAccountId)
    }

    @Synchronized
    fun loadBlocked(): List<BlockedContact> {
        if (!storageFile.exists()) {
            return emptyList()
        }
        return try {
            val jsonStr = storageFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<BlockedContact>()
            for (i in 0 until array.length()) {
                list.add(BlockedContact.fromJson(array.getJSONObject(i)))
            }
            list.sortedByDescending { it.blockedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveBlocked(list: List<BlockedContact>) {
        try {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            storageFile.writeText(array.toString(2))
        } catch (_: Exception) {}
    }

    /**
     * Vérifie de façon universelle et étanche si un identifiant (téléphone, clé publique Nostr hex 64,
     * npub1..., ou nom) est actuellement bloqué.
     */
    @Synchronized
    fun isBlocked(identifier: String?): Boolean {
        if (identifier.isNullOrBlank()) return false
        val clean = identifier.trim()
        val list = loadBlocked()
        if (list.isEmpty()) return false

        val hexCandidate = FriendRequestRepository.resolveNostrPubkeyHex(clean)

        return list.any { blocked ->
            // 1. Correspondance directe ou normalisée par téléphone
            (clean.isNotBlank() && (
                blocked.phone.equals(clean, ignoreCase = true) ||
                FriendRequestRepository.isSamePhone(blocked.phone, clean)
            )) ||
            // 2. Correspondance directe ou décodée par clé publique Nostr
            (!blocked.publicKey.isNullOrBlank() && (
                blocked.publicKey.equals(clean, ignoreCase = true) ||
                (hexCandidate != null && FriendRequestRepository.resolveNostrPubkeyHex(blocked.publicKey) == hexCandidate)
            )) ||
            // 3. Si l'identifiant testé est une clé publique Nostr, vérifier si elle correspond au contact du téléphone bloqué
            (hexCandidate != null && matchesPubkeyWithPhone(blocked.phone, hexCandidate)) ||
            // 4. Si l'identifiant testé est un téléphone, vérifier s'il correspond à la clé publique bloquée
            (!blocked.publicKey.isNullOrBlank() && matchesPhoneWithPubkey(clean, blocked.publicKey))
        }
    }

    private fun matchesPubkeyWithPhone(blockedPhone: String, candidateHex: String): Boolean {
        if (blockedPhone.isBlank() || candidateHex.isBlank()) return false
        return try {
            val knownKey = FriendRequestRepository(context).resolveNostrPubkeyForPhone(blockedPhone)
            val hex = FriendRequestRepository.resolveNostrPubkeyHex(knownKey)
            hex != null && hex.equals(candidateHex, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun matchesPhoneWithPubkey(candidatePhone: String, blockedPubkey: String): Boolean {
        if (candidatePhone.isBlank() || blockedPubkey.isBlank()) return false
        val blockedHex = FriendRequestRepository.resolveNostrPubkeyHex(blockedPubkey) ?: return false
        return try {
            val knownKey = FriendRequestRepository(context).resolveNostrPubkeyForPhone(candidatePhone)
            val hex = FriendRequestRepository.resolveNostrPubkeyHex(knownKey)
            hex != null && hex.equals(blockedHex, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Bloque un contact en enregistrant à la fois son numéro et sa clé publique Nostr si disponible.
     * Nettoie automatiquement les liens d'amitié, les requêtes en attente et la présence.
     */
    @Synchronized
    fun blockContact(
        phone: String,
        name: String,
        reason: String = "Bloqué",
        publicKey: String? = null
    ): Boolean {
        if (phone.isBlank() && publicKey.isNullOrBlank()) return false
        val cleanPhone = phone.trim()
        val current = loadBlocked().toMutableList()

        // Résolution automatique de la clé publique si non fournie
        val resolvedPublicKey = publicKey?.takeIf { it.isNotBlank() } ?: run {
            if (FriendRequestRepository.isValidNostrKey(cleanPhone)) cleanPhone
            else try {
                FriendRequestRepository(context).resolveNostrPubkeyForPhone(cleanPhone)
            } catch (_: Exception) { null }
        }

        val alreadyBlocked = current.any {
            (cleanPhone.isNotBlank() && (FriendRequestRepository.isSamePhone(it.phone, cleanPhone) || it.phone.equals(cleanPhone, ignoreCase = true))) ||
            (!resolvedPublicKey.isNullOrBlank() && !it.publicKey.isNullOrBlank() && it.publicKey.equals(resolvedPublicKey, ignoreCase = true))
        }

        if (!alreadyBlocked) {
            val newEntry = BlockedContact(
                phone = cleanPhone,
                name = name.ifBlank { cleanPhone.ifBlank { resolvedPublicKey?.take(10) ?: "Inconnu" } },
                blockedAt = System.currentTimeMillis(),
                reason = reason,
                publicKey = resolvedPublicKey
            )
            current.add(0, newEntry)
            saveBlocked(current)

            // Nettoyage automatique des relations d'amis
            try {
                val friendRepo = FriendRequestRepository(context)
                if (cleanPhone.isNotBlank()) {
                    friendRepo.removeFriend(cleanPhone)
                    friendRepo.rejectRequestForPhone(cleanPhone)
                }
            } catch (_: Exception) {}

            // Extinction immédiate de la présence de ce contact
            try {
                if (cleanPhone.isNotBlank()) PresenceHelper.recordPeerOffline(cleanPhone)
                if (!resolvedPublicKey.isNullOrBlank()) PresenceHelper.recordPeerOffline(resolvedPublicKey)
            } catch (_: Exception) {}

            // Notification globale pour actualiser l'interface instantanément
            try {
                val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
            } catch (_: Exception) {}

            return true
        }
        return false
    }

    @Synchronized
    fun unblockContact(identifier: String): Boolean {
        if (identifier.isBlank()) return false
        val clean = identifier.trim()
        val current = loadBlocked().toMutableList()
        val hexCandidate = FriendRequestRepository.resolveNostrPubkeyHex(clean)

        val removed = current.removeAll {
            it.phone.equals(clean, ignoreCase = true) ||
            FriendRequestRepository.isSamePhone(it.phone, clean) ||
            (!it.publicKey.isNullOrBlank() && (
                it.publicKey.equals(clean, ignoreCase = true) ||
                (hexCandidate != null && FriendRequestRepository.resolveNostrPubkeyHex(it.publicKey) == hexCandidate)
            ))
        }

        if (removed) {
            saveBlocked(current)
            try {
                val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
            } catch (_: Exception) {}
        }
        return removed
    }
}
