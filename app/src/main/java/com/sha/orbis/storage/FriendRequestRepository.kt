package com.sha.orbis.storage

import android.content.Context
import com.sha.orbis.model.Contact
import com.sha.orbis.model.FriendRequest
import com.sha.orbis.model.FriendRequestStatus
import com.sha.orbis.model.RequestDirection
import org.json.JSONArray
import java.io.File

class FriendRequestRepository(
    private val context: Context,
    private val accountId: String? = null
) {
    private val currentAccountId: String = accountId?.takeIf { it.isNotBlank() } ?: try {
        com.sha.orbis.data.SessionManager(context).activeAccountId
    } catch (_: Exception) { "" }

    private val storageFile: File by lazy {
        AccountStorageManager.getAccountFile(context, "friend_requests.json", currentAccountId)
    }

    private val convRepo: ConversationRepository get() = ConversationRepository(context, currentAccountId)
    private val blockedRepo: BlockedContactsRepository get() = BlockedContactsRepository(context, currentAccountId)

    companion object {
        fun isSamePhone(phone1: String, phone2: String): Boolean {
            val p1 = phone1.trim()
            val p2 = phone2.trim()
            if (p1.isBlank() || p2.isBlank()) return false
            if (p1.equals(p2, ignoreCase = true)) return true

            val hex1 = resolveNostrPubkeyHex(p1)
            val hex2 = resolveNostrPubkeyHex(p2)
            if (hex1 != null && hex2 != null && hex1.equals(hex2, ignoreCase = true)) return true

            val d1 = p1.filter { it.isDigit() }
            val d2 = p2.filter { it.isDigit() }
            if (d1.isBlank() || d2.isBlank()) return false
            if (d1 == d2) return true
            if (d1.length >= 8 && d2.length >= 8) {
                return d1.takeLast(8) == d2.takeLast(8)
            }
            return false
        }

        fun isValidNostrKey(key: String?): Boolean {
            if (key.isNullOrBlank()) return false
            val trimmed = key.trim()
            if (trimmed.equals("NPUB_PENDING", ignoreCase = true) || trimmed.equals("PUB_KEY_PENDING", ignoreCase = true)) return false
            if (trimmed.startsWith("npub1") && trimmed.length >= 58) return true
            if (trimmed.length == 64 && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return true
            return false
        }

        fun resolveNostrPubkeyHex(key: String?): String? {
            if (!isValidNostrKey(key)) return null
            val trimmed = key!!.trim()
            return if (trimmed.startsWith("npub1")) {
                try { com.sha.orbis.nostr.crypto.Bech32.decodeToHex(trimmed).second.lowercase() } catch (_: Exception) { null }
            } else {
                trimmed.lowercase()
            }
        }
    }

    @Synchronized
    fun loadRequests(): List<FriendRequest> {
        if (!storageFile.exists()) {
            return emptyList()
        }
        return try {
            val jsonStr = storageFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<FriendRequest>()
            for (i in 0 until array.length()) {
                list.add(FriendRequest.fromJson(array.getJSONObject(i)))
            }

            // Auto-réparation intelligente du compte Fondateur / Dev officiel
            var autoRepaired = false
            list.forEachIndexed { idx, req ->
                val isDev = com.sha.orbis.admin.AdminSecurityHelper.isAdmin(req.senderPhone)
                if (isDev) {
                    if (req.senderName.isBlank() || req.senderName == "Ami Orbis" || req.senderName == "Ami OrbisNet") {
                        list[idx] = req.copy(senderName = "O R B I S net")
                        autoRepaired = true
                    }
                } else {
                    if (req.senderName == "O R B I S net" || com.sha.orbis.admin.AdminSecurityHelper.isReservedName(req.senderName)) {
                        list[idx] = req.copy(senderName = "")
                        autoRepaired = true
                    }
                }
            }
            if (autoRepaired) {
                try { saveRequests(list) } catch (_: Exception) {}
            }

            list.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveRequests(requests: List<FriendRequest>) {
        try {
            val array = JSONArray()
            requests.forEach { array.put(it.toJson()) }
            storageFile.writeText(array.toString(2))
        } catch (_: Exception) {}
    }

    @Synchronized
    fun addOrUpdate(request: FriendRequest) {
        val current = loadRequests().toMutableList()
        val index = current.indexOfFirst { it.id == request.id || isSamePhone(it.senderPhone, request.senderPhone) }
        if (index >= 0) {
            val existing = current[index]
            val targetStatus = when {
                existing.status == FriendRequestStatus.ACCEPTED -> FriendRequestStatus.ACCEPTED
                existing.status == FriendRequestStatus.REJECTED && request.status == FriendRequestStatus.PENDING -> FriendRequestStatus.REJECTED
                else -> request.status
            }
            val validKey = request.senderPublicKey.takeIf { isValidNostrKey(it) } ?: existing.senderPublicKey
            val validAvatar = request.senderAvatarPath?.takeIf { it.isNotBlank() } ?: existing.senderAvatarPath
            val validName = if (request.senderName.isNotBlank() && !request.senderName.startsWith("+")) {
                request.senderName
            } else existing.senderName

            val validFcmToken = request.peerFcmToken?.takeIf { it.isNotBlank() } ?: existing.peerFcmToken

            current[index] = existing.copy(
                status = targetStatus,
                senderName = validName,
                senderAvatarPath = validAvatar,
                senderPublicKey = validKey,
                groupKey = request.groupKey.ifBlank { existing.groupKey },
                peerFcmToken = validFcmToken,
                timestamp = maxOf(existing.timestamp, request.timestamp)
            )
        } else {
            val targetStatus = if (isFriend(request.senderPhone) && request.status == FriendRequestStatus.PENDING) {
                FriendRequestStatus.ACCEPTED
            } else {
                request.status
            }
            current.add(0, request.copy(status = targetStatus))
        }
        saveRequests(current)
    }

    @Synchronized
    fun acceptRequest(requestId: String): FriendRequest? {
        val current = loadRequests().toMutableList()
        val index = current.indexOfFirst { it.id == requestId }
        if (index >= 0) {
            val updated = current[index].copy(status = FriendRequestStatus.ACCEPTED)
            current[index] = updated
            saveRequests(current)
            return updated
        }
        return null
    }

    @Synchronized
    fun acceptRequestForPhone(phone: String): FriendRequest? {
        val current = loadRequests().toMutableList()
        var accepted: FriendRequest? = null
        for (i in current.indices) {
            if (isSamePhone(current[i].senderPhone, phone)) {
                val updated = current[i].copy(status = FriendRequestStatus.ACCEPTED)
                current[i] = updated
                accepted = updated
            }
        }
        if (accepted != null) {
            saveRequests(current)
        }
        return accepted
    }

    @Synchronized
    fun rejectRequest(requestId: String) {
        val current = loadRequests().toMutableList()
        val index = current.indexOfFirst { it.id == requestId }
        if (index >= 0) {
            current[index] = current[index].copy(status = FriendRequestStatus.REJECTED)
            saveRequests(current)
        }
    }

    @Synchronized
    fun rejectRequestForPhone(phone: String) {
        val current = loadRequests().toMutableList()
        var modified = false
        for (i in current.indices) {
            if (isSamePhone(current[i].senderPhone, phone)) {
                current[i] = current[i].copy(status = FriendRequestStatus.REJECTED)
                modified = true
            }
        }
        if (modified) {
            saveRequests(current)
        }
    }

    fun getPendingReceived(): List<FriendRequest> =
        loadRequests().filter { it.direction == RequestDirection.RECEIVED && it.status == FriendRequestStatus.PENDING }

    fun getPendingSent(): List<FriendRequest> =
        loadRequests().filter { it.direction == RequestDirection.SENT && it.status == FriendRequestStatus.PENDING }

    fun getPendingReceivedForPhone(phone: String): FriendRequest? =
        getPendingReceived().find { isSamePhone(it.senderPhone, phone) }

    fun getPendingSentForPhone(phone: String): FriendRequest? {
        if (isFriend(phone)) return null
        return getPendingSent().find { isSamePhone(it.senderPhone, phone) }
    }

    private fun isConnectedContact(contact: Contact): Boolean {
        val hasKey = contact.publicKey.isNotBlank() && (
            contact.publicKey.length > 30 ||
            contact.publicKey.equals("PUB_KEY_VERIFIED", ignoreCase = true) ||
            isValidNostrKey(contact.publicKey)
        )
        val hasConnectedStatus = contact.status.contains("Connecté", ignoreCase = true) ||
                contact.status.contains("Connected", ignoreCase = true)
        return hasConnectedStatus && hasKey
    }

    @Synchronized
    fun isConnectedContact(phone: String): Boolean {
        if (phone.isBlank()) return false
        return try {
            convRepo.loadContacts().any { contact ->
                isSamePhone(contact.phone, phone) && isConnectedContact(contact)
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Canonical phone stored locally for this peer (friend request or contact). */
    @Synchronized
    fun lookupStoredPhone(phone: String): String? {
        if (phone.isBlank()) return null
        loadRequests().firstOrNull { isSamePhone(it.senderPhone, phone) }?.senderPhone?.let { return it }
        convRepo.loadContacts()
            .firstOrNull { isSamePhone(it.phone, phone) }
            ?.phone
            ?.let { return it }
        return null
    }

    /** Peers that should receive or see P2P social content (accepted friends only). */
    @Synchronized
    fun getSocialPeerPhones(): List<String> {
        val seen = mutableListOf<String>()
        loadRequests()
            .filter { it.status == FriendRequestStatus.ACCEPTED && it.senderPhone.isNotBlank() && !blockedRepo.isBlocked(it.senderPhone) }
            .forEach { req ->
                val phone = req.senderPhone.trim()
                if (seen.none { isSamePhone(it, phone) }) {
                    seen.add(phone)
                }
            }
        return seen
    }

    @Synchronized
    fun removeFriend(phone: String): Boolean {
        if (phone.isBlank()) return false
        val current = loadRequests().toMutableList()
        val removed = current.removeAll { isSamePhone(it.senderPhone, phone) }
        if (removed) {
            saveRequests(current)
        }
        try {
            val contacts = convRepo.loadContacts().toMutableList()
            var modified = false
            contacts.forEachIndexed { idx, c ->
                if (isSamePhone(c.phone, phone)) {
                    contacts[idx] = c.copy(status = "Déconnecté")
                    modified = true
                }
            }
            if (modified) {
                convRepo.saveContacts(contacts)
            }
        } catch (_: Exception) {}
        return removed
    }

    /**
     * Annule une invitation envoyée en attente pour un numéro donné.
     * Supprime la requête SENT de friend_requests.json et réinitialise le statut du contact local.
     */
    @Synchronized
    fun cancelSentRequestForPhone(phone: String): Boolean {
        if (phone.isBlank()) return false
        val current = loadRequests().toMutableList()
        val removed = current.removeAll { isSamePhone(it.senderPhone, phone) && it.direction == RequestDirection.SENT }
        if (removed) {
            saveRequests(current)
        }
        try {
            val contacts = convRepo.loadContacts().toMutableList()
            var modified = false
            contacts.forEachIndexed { idx, c ->
                if (isSamePhone(c.phone, phone)) {
                    val newStatus = if (c.status.contains("attente", ignoreCase = true) || c.status.contains("pending", ignoreCase = true)) "" else c.status
                    contacts[idx] = c.copy(status = newStatus)
                    modified = true
                }
            }
            if (modified) {
                convRepo.saveContacts(contacts)
            }
        } catch (_: Exception) {}
        return removed
    }

    /**
     * Annule une invitation envoyée en attente par son ID de requête.
     */
    @Synchronized
    fun cancelSentRequest(requestId: String): Boolean {
        if (requestId.isBlank()) return false
        val current = loadRequests().toMutableList()
        val target = current.find { it.id == requestId }
        val phone = target?.senderPhone
        val removed = current.removeAll { it.id == requestId && it.direction == RequestDirection.SENT }
        if (removed) {
            saveRequests(current)
        }
        if (!phone.isNullOrBlank()) {
            try {
                val contacts = convRepo.loadContacts().toMutableList()
                var modified = false
                contacts.forEachIndexed { idx, c ->
                    if (isSamePhone(c.phone, phone)) {
                        val newStatus = if (c.status.contains("attente", ignoreCase = true) || c.status.contains("pending", ignoreCase = true)) "" else c.status
                        contacts[idx] = c.copy(status = newStatus)
                        modified = true
                    }
                }
                if (modified) {
                    convRepo.saveContacts(contacts)
                }
            } catch (_: Exception) {}
        }
        return removed
    }

    @Synchronized
    fun ensureAcceptedFriend(
        phone: String,
        name: String = "",
        publicKey: String = "",
        avatarPath: String? = null
    ) {
        if (phone.isBlank() && publicKey.isBlank()) return
        val current = loadRequests().toMutableList()
        val validKey = publicKey.takeIf { isValidNostrKey(it) }
        val resolvedHex = resolveNostrPubkeyHex(publicKey)

        var foundMatch = false
        for (i in current.indices) {
            val req = current[i]
            val phoneMatch = phone.isNotBlank() && isSamePhone(req.senderPhone, phone)
            val keyMatch = resolvedHex != null && resolveNostrPubkeyHex(req.senderPublicKey) == resolvedHex
            if (phoneMatch || keyMatch) {
                foundMatch = true
                current[i] = req.copy(
                    status = FriendRequestStatus.ACCEPTED,
                    senderPublicKey = validKey ?: req.senderPublicKey.takeIf { isValidNostrKey(it) } ?: "",
                    senderAvatarPath = if (!avatarPath.isNullOrBlank()) avatarPath else req.senderAvatarPath,
                    senderName = if (name.isNotBlank() && !name.startsWith("+")) name else req.senderName,
                    senderPhone = if (phone.any { it.isDigit() }) phone else req.senderPhone
                )
            }
        }
        if (foundMatch) {
            saveRequests(current)
        } else {
            val cleanDigits = phone.filter { it.isDigit() }.takeLast(8)
            val fallbackId = if (cleanDigits.isNotBlank()) cleanDigits else System.currentTimeMillis().toString()
            addOrUpdate(
                FriendRequest(
                    id = "req_$fallbackId",
                    senderPhone = phone,
                    senderName = name,
                    senderAvatarPath = avatarPath,
                    senderPublicKey = validKey ?: "",
                    status = FriendRequestStatus.ACCEPTED,
                    direction = RequestDirection.RECEIVED
                )
            )
        }
    }

    @Synchronized
    fun isFriend(phone: String): Boolean {
        if (phone.isBlank()) return false
        if (blockedRepo.isBlocked(phone)) return false
        val pubkeyHex = if (phone.startsWith("npub1")) {
            try { com.sha.orbis.nostr.crypto.Bech32.decodeToHex(phone).second.lowercase() } catch (_: Exception) { null }
        } else if (phone.length == 64 && phone.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            phone.lowercase()
        } else {
            null
        }

        fun matchesKey(stored: String): Boolean {
            if (pubkeyHex == null || stored.isBlank()) return false
            if (stored.equals(pubkeyHex, ignoreCase = true)) return true
            if (stored.startsWith("npub1")) {
                val decoded = try { com.sha.orbis.nostr.crypto.Bech32.decodeToHex(stored).second.lowercase() } catch (_: Exception) { null }
                if (decoded.equals(pubkeyHex, ignoreCase = true)) return true
            }
            return false
        }

        val matchesRequest = loadRequests().any { req ->
            req.status == FriendRequestStatus.ACCEPTED && (
                isSamePhone(req.senderPhone, phone) || matchesKey(req.senderPublicKey)
            )
        }
        return matchesRequest
    }

    @Synchronized
    fun getGroupKeyForPhone(phone: String): String? {
        if (phone.isBlank()) return null
        return loadRequests().find { isSamePhone(it.senderPhone, phone) && it.groupKey.isNotBlank() }?.groupKey
    }

    @Synchronized
    fun getFriendRequestForPhone(phone: String): FriendRequest? {
        if (phone.isBlank()) return null
        return loadRequests().find { isSamePhone(it.senderPhone, phone) }
    }

    /**
     * Rehausse immédiatement le niveau de confiance d'un ami au niveau physique vérifié en personne (Face-à-Face QR).
     */
    @Synchronized
    fun certifyFriendInPerson(phone: String) {
        if (phone.isBlank()) return
        val current = loadRequests().toMutableList()
        var modified = false
        for (i in current.indices) {
            if (isSamePhone(current[i].senderPhone, phone)) {
                current[i] = current[i].copy(trustLevel = com.sha.orbis.model.TrustLevel.LEVEL_3_IN_PERSON_CERTIFIED)
                modified = true
            }
        }
        if (modified) {
            saveRequests(current)
        }
    }

    @Synchronized
    fun resolveNostrPubkeyForPhone(phone: String): String? {
        if (phone.isBlank()) return null
        val reqKey = loadRequests().find { isSamePhone(it.senderPhone, phone) }?.senderPublicKey
        resolveNostrPubkeyHex(reqKey)?.let { return it }
        val contactKey = try {
            convRepo.loadContacts().find { isSamePhone(it.phone, phone) }?.publicKey
        } catch (_: Exception) { null }
        return resolveNostrPubkeyHex(contactKey)
    }

    /**
     * Récupère le jeton FCM enregistré pour un pair (par son numéro de téléphone ou sa clé Nostr).
     */
    @Synchronized
    fun getPeerFcmToken(peerPhoneOrKey: String): String? {
        if (peerPhoneOrKey.isBlank()) return null
        val requests = loadRequests()
        val byPhone = requests.firstOrNull { isSamePhone(it.senderPhone, peerPhoneOrKey) && !it.peerFcmToken.isNullOrBlank() }
        if (byPhone?.peerFcmToken != null) return byPhone.peerFcmToken

        val pubkeyHex = resolveNostrPubkeyHex(peerPhoneOrKey)
        if (pubkeyHex != null) {
            val byKey = requests.firstOrNull {
                resolveNostrPubkeyHex(it.senderPublicKey) == pubkeyHex && !it.peerFcmToken.isNullOrBlank()
            }
            if (byKey?.peerFcmToken != null) return byKey.peerFcmToken
        }
        return null
    }

    /**
     * Met à jour dynamiquement le jeton FCM d'un ami.
     */
    @Synchronized
    fun updatePeerFcmToken(peerPhone: String, fcmToken: String) {
        if (peerPhone.isBlank() || fcmToken.isBlank()) return
        val current = loadRequests().toMutableList()
        val index = current.indexOfFirst { isSamePhone(it.senderPhone, peerPhone) }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(peerFcmToken = fcmToken)
            saveRequests(current)
        }
    }
}
