package com.sha.orbis.storage

import android.content.Context
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Contact
import com.sha.orbis.model.Conversation
import com.sha.orbis.model.Message
import com.sha.orbis.model.MessageRevocationTarget
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ConversationRepository(
    private val context: Context,
    private val accountId: String? = null
) {
    private val currentAccountId: String = accountId ?: SessionManager(context).activeAccountId

    private val storageFile: File = AccountStorageManager.getAccountFile(context, "conversations.json", currentAccountId)
    private val contactsFile: File = AccountStorageManager.getAccountFile(context, "contacts.json", currentAccountId)

    private val messageStore: LocalMessageStore get() = LocalMessageStore(context, currentAccountId)

    private fun migrateConversationMessages(sourceIds: List<String>, targetId: String) {
        val distinctSources = sourceIds.filter { it.isNotBlank() }.distinct()
        if (distinctSources.isEmpty() || targetId.isBlank()) return

        try {
            val store = messageStore
            val merged = mutableListOf<Message>()

            distinctSources.forEach { sourceId ->
                store.loadConversationMessages(sourceId).forEach { message ->
                    if (merged.none { it.id == message.id }) {
                        merged.add(message.copy(conversationId = targetId))
                    }
                }
            }

            if (merged.isNotEmpty()) {
                merged.sortBy { it.timestamp }
                store.saveConversationMessages(targetId, merged)
            }

            distinctSources
                .filter { it != targetId }
                .forEach { store.clearConversation(it) }
        } catch (_: Exception) {}
    }

    init {
        // Automatic migration of legacy single-account data ONLY for the primary account
        AccountStorageManager.migrateLegacyDataForPrimaryAccountIfNeeded(context, currentAccountId)
        // Automatic purge of ghost contacts / invalid shortcodes (e.g. 111)
        purgeInvalidGhostContacts()
    }

    fun saveConversations(conversations: List<Conversation>) {
        val sanitized = conversations.map { conv ->
            if (conv.lastMessage.contains("[ORBIS_PEER_SYNC_V1]") ||
                conv.lastMessage.contains("\"action\":\"EXCHANGE_") ||
                (conv.lastMessage.startsWith("{") && conv.lastMessage.contains("\"bundle\":{"))
            ) {
                conv.copy(lastMessage = "")
            } else conv
        }
        val array = JSONArray()
        sanitized.forEach { array.put(it.toJson()) }
        storageFile.writeText(JSONObject().put("conversations", array).toString(2), Charsets.UTF_8)
    }

    fun loadConversations(): List<Conversation> {
        if (!storageFile.exists()) return emptyList()
        val rawList = try {
            val obj = JSONObject(storageFile.readText(Charsets.UTF_8))
            val array = obj.optJSONArray("conversations") ?: JSONArray()
            List(array.length()) { index ->
                val conv = Conversation.fromJson(array.getJSONObject(index))
                if (conv.lastMessage.contains("[ORBIS_PEER_SYNC_V1]") ||
                    conv.lastMessage.contains("\"action\":\"EXCHANGE_") ||
                    (conv.lastMessage.startsWith("{") && conv.lastMessage.contains("\"bundle\":{"))
                ) {
                    conv.copy(lastMessage = "")
                } else conv
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (rawList.isEmpty()) return emptyList()

        val contacts = loadContacts()
        val deduplicated = mutableListOf<Conversation>()
        var modified = false

        for (conv in rawList) {
            val matchIdx = deduplicated.indexOfFirst { existing ->
                if (existing.id == conv.id) return@indexOfFirst true
                if (existing.isGroup || conv.isGroup) return@indexOfFirst false

                // Check participants
                val hasCommonParticipant = existing.participants.any { p1 ->
                    if (p1 == "me") false else conv.participants.any { p2 ->
                        p2 != "me" && (FriendRequestRepository.isSamePhone(p1, p2) || p1.equals(p2, ignoreCase = true))
                    }
                }
                if (hasCommonParticipant) return@indexOfFirst true

                // Check cross-match participant to ID (e.g. one conv has conv_<digits> and the other has <digits> in participants)
                val crossParticipantMatch = conv.participants.any { p2 ->
                    val d = p2.filter { it.isDigit() }
                    p2 != "me" && (FriendRequestRepository.isSamePhone(existing.id.removePrefix("conv_"), p2) || (d.length >= 8 && existing.id.contains(d.takeLast(8))))
                } || existing.participants.any { p1 ->
                    val d = p1.filter { it.isDigit() }
                    p1 != "me" && (FriendRequestRepository.isSamePhone(conv.id.removePrefix("conv_"), p1) || (d.length >= 8 && conv.id.contains(d.takeLast(8))))
                }
                if (crossParticipantMatch) return@indexOfFirst true

                // Check against contacts: e.g. one conv is conv_0612345678 and another is conv_<pubkey16>
                val matchedContact = contacts.find { c ->
                    val cleanDigits = c.phone.filter { it.isDigit() }
                    val pubkeyPart = c.publicKey.take(16)
                    val existingMatches = (cleanDigits.length >= 6 && existing.id.contains(cleanDigits)) || (pubkeyPart.isNotBlank() && existing.id.contains(pubkeyPart))
                    val convMatches = (cleanDigits.length >= 6 && conv.id.contains(cleanDigits)) || (pubkeyPart.isNotBlank() && conv.id.contains(pubkeyPart))
                    existingMatches && convMatches
                }
                if (matchedContact != null) return@indexOfFirst true

                // Check if both 1-to-1 conversations have the exact same contact title (e.g. duplicate created by external share)
                if (existing.title.isNotBlank() && !existing.title.startsWith("+") && existing.title != "O R B I S net" &&
                    existing.title.equals(conv.title, ignoreCase = true)) {
                    return@indexOfFirst true
                }

                // Check titles if one is "O R B I S net" and the other has real contact title
                if ((existing.title == "O R B I S net" && conv.title != "O R B I S net") ||
                    (conv.title == "O R B I S net" && existing.title != "O R B I S net")) {
                    val otherTitle = if (existing.title == "O R B I S net") conv.title else existing.title
                    val c = contacts.find { it.name.equals(otherTitle, ignoreCase = true) }
                    if (c != null) {
                        val isSame = existing.participants.any { p -> FriendRequestRepository.isSamePhone(p, c.phone) || p.equals(c.publicKey, ignoreCase = true) } ||
                                     conv.participants.any { p -> FriendRequestRepository.isSamePhone(p, c.phone) || p.equals(c.publicKey, ignoreCase = true) }
                        if (isSame) return@indexOfFirst true
                    }
                }

                false
            }

            if (matchIdx >= 0) {
                modified = true
                val existing = deduplicated[matchIdx]

                // Choose better title: real name over "O R B I S net"
                val finalTitle = when {
                    existing.title.isNotBlank() && !existing.title.startsWith("+") && existing.title != "O R B I S net" -> existing.title
                    conv.title.isNotBlank() && !conv.title.startsWith("+") && conv.title != "O R B I S net" -> conv.title
                    else -> existing.title.ifBlank { conv.title }
                }

                // Prefer convId with phone digits over npub/pubkey
                val existingHasDigits = existing.id.filter { it.isDigit() }.length >= 6
                val convHasDigits = conv.id.filter { it.isDigit() }.length >= 6
                val finalId = if (existingHasDigits) existing.id else if (convHasDigits) conv.id else existing.id

                val finalParticipants = (existing.participants + conv.participants).distinct()
                val finalLastMsg = if (conv.updatedAt > existing.updatedAt && conv.lastMessage.isNotBlank()) conv.lastMessage else existing.lastMessage
                val finalUpdatedAt = maxOf(existing.updatedAt, conv.updatedAt)
                val finalUnread = existing.unreadCount + conv.unreadCount

                if (existing.id != finalId || conv.id != finalId) {
                    migrateConversationMessages(listOf(existing.id, conv.id), finalId)
                }

                deduplicated[matchIdx] = existing.copy(
                    id = finalId,
                    title = finalTitle,
                    participants = finalParticipants,
                    lastMessage = finalLastMsg,
                    updatedAt = finalUpdatedAt,
                    unreadCount = finalUnread
                )
            } else {
                deduplicated.add(conv)
            }
        }

        if (modified || deduplicated.size != rawList.size) {
            try {
                saveConversations(deduplicated)
            } catch (_: Exception) {}
        }

        return deduplicated
    }

    fun addConversation(conversation: Conversation) {
        val list = loadConversations().toMutableList()
        val existingIdx = list.indexOfFirst { it.id == conversation.id }
        if (existingIdx >= 0) {
            list[existingIdx] = conversation
        } else {
            list.add(0, conversation)
        }
        saveConversations(list)
    }

    /**
     * Toggle the pin state of a conversation.
     * @return the new isPinned value after toggle.
     */
    fun togglePinConversation(conversationId: String): Boolean {
        val list = loadConversations().toMutableList()
        val idx = list.indexOfFirst { it.id == conversationId }
        if (idx < 0) return false
        val newPinned = !list[idx].isPinned
        list[idx] = list[idx].copy(isPinned = newPinned)
        saveConversations(list)
        return newPinned
    }

    fun ensureConversationForContact(
        phone: String,
        name: String,
        publicKey: String? = null,
        initialMessage: String = ""
    ): Conversation {
        val cleanDigits = phone.filter { it.isDigit() }
        val canonicalConvId = when {
            cleanDigits.length >= 6 -> "conv_$cleanDigits"
            FriendRequestRepository.isValidNostrKey(publicKey) -> "conv_${publicKey!!.trim().take(16)}"
            else -> "conv_${phone.filter { it.isLetterOrDigit() }.ifBlank { "chat" }.take(16)}"
        }
        val list = loadConversations().toMutableList()
        val idx = list.indexOfFirst { conv ->
            if (conv.isGroup) return@indexOfFirst false
            if (conv.id == canonicalConvId || conv.id == phone || conv.id == "conv_$phone") return@indexOfFirst true
            if (FriendRequestRepository.isSamePhone(conv.id.removePrefix("conv_"), phone) ||
                FriendRequestRepository.isSamePhone(conv.id, phone)) return@indexOfFirst true
            if (conv.participants.any { p -> p != "me" && FriendRequestRepository.isSamePhone(p, phone) }) return@indexOfFirst true
            if (FriendRequestRepository.isValidNostrKey(publicKey)) {
                val pubTrimmed = publicKey!!.trim()
                if (conv.participants.any { p -> p.equals(pubTrimmed, ignoreCase = true) } ||
                    conv.id.contains(pubTrimmed.take(16))) return@indexOfFirst true
            }
            if (name.isNotBlank() && !name.startsWith("+") && conv.title.equals(name, ignoreCase = true)) return@indexOfFirst true
            false
        }
        val conv = if (idx >= 0) {
            val existing = list[idx]
            val targetId = canonicalConvId
            val participantsWithMe = ((existing.participants.filter { it.isNotBlank() }) + listOf("me", phone.ifBlank { publicKey ?: "" })).distinct()
            val updated = existing.copy(
                id = targetId,
                title = if (existing.title.startsWith("+") || existing.title.isBlank() || existing.title == "O R B I S net") name.ifBlank { existing.title } else existing.title,
                participants = participantsWithMe,
                lastMessage = if (existing.lastMessage.isBlank()) initialMessage else existing.lastMessage,
                updatedAt = System.currentTimeMillis()
            )
            if (existing.id != targetId) {
                migrateConversationMessages(listOf(existing.id, targetId), targetId)
            }
            val normalizedList = list
                .filterIndexed { index, item -> index == idx || item.id != targetId }
                .toMutableList()
            normalizedList[normalizedList.indexOfFirst { it.id == existing.id || it.id == targetId }] = updated
            saveConversations(normalizedList)
            updated
        } else {
            val newConv = Conversation(
                id = canonicalConvId,
                title = name.ifBlank { phone.ifBlank { "Discussion" } },
                participants = listOf("me", phone.ifBlank { publicKey ?: "" }).filter { it.isNotBlank() },
                lastMessage = initialMessage,
                updatedAt = System.currentTimeMillis(),
                unreadCount = 0
            )
            list.add(0, newConv)
            saveConversations(list)
            newConv
        }

        if (initialMessage.isNotBlank()) {
            val store = messageStore
            val existingMsgs = store.loadConversationMessages(conv.id)
            if (existingMsgs.isEmpty()) {
                store.addMessage(
                    conv.id,
                    Message(
                        id = "sys_${System.currentTimeMillis()}",
                        conversationId = conv.id,
                        senderId = "system",
                        text = initialMessage,
                        timestamp = System.currentTimeMillis(),
                        encrypted = false,
                        status = com.sha.orbis.model.MessageDeliveryStatus.DELIVERED
                    )
                )
            }
        }
        return conv
    }

    fun ensureConversationForFriend(phone: String, name: String, initialMessage: String = ""): Conversation =
        ensureConversationForContact(phone, name, null, initialMessage)

    fun syncConnectedFriendsToConversations(defaultMessage: String = ""): List<Conversation> {
        val currentConvs = loadConversations().toMutableList()
        val contactsList = loadContacts().toMutableList()
        var contactsModified = false
        var convsModified = false

        // 1. Sync accepted friend requests into contacts
        try {
            val friendRepo = FriendRequestRepository(context, currentAccountId)
            val acceptedRequests = friendRepo.loadRequests().filter { it.status == com.sha.orbis.model.FriendRequestStatus.ACCEPTED }
            acceptedRequests.forEach { req ->
                val idx = contactsList.indexOfFirst {
                    FriendRequestRepository.isSamePhone(it.phone, req.senderPhone) ||
                    (req.senderPublicKey.isNotBlank() && it.publicKey.equals(req.senderPublicKey, ignoreCase = true))
                }
                if (idx >= 0) {
                    val finalPub = when {
                        FriendRequestRepository.isValidNostrKey(req.senderPublicKey) -> req.senderPublicKey
                        FriendRequestRepository.isValidNostrKey(contactsList[idx].publicKey) -> contactsList[idx].publicKey
                        else -> req.senderPublicKey.ifBlank { contactsList[idx].publicKey }
                    }
                    val needsStatusUpdate = !contactsList[idx].status.contains("Connecté", ignoreCase = true)
                    val needsKeyUpdate = contactsList[idx].publicKey != finalPub && FriendRequestRepository.isValidNostrKey(finalPub)
                    if (needsStatusUpdate || needsKeyUpdate) {
                        contactsList[idx] = contactsList[idx].copy(
                            status = "Connecté 🛡️",
                            publicKey = finalPub,
                            avatarPath = req.senderAvatarPath ?: contactsList[idx].avatarPath
                        )
                        contactsModified = true
                    }
                } else {
                    val clean = req.senderPhone.filter { it.isDigit() }.ifBlank { req.senderPublicKey.take(8) }
                    contactsList.add(0, Contact(
                        id = "c_$clean",
                        name = req.senderName,
                        phone = req.senderPhone,
                        publicKey = req.senderPublicKey,
                        status = "Connecté 🛡️",
                        avatarPath = req.senderAvatarPath
                    ))
                    contactsModified = true
                }
            }
            if (contactsModified) {
                saveContacts(contactsList)
            }
        } catch (_: Exception) {}

        // 2. Ensure every connected friend has an active conversation thread
        contactsList.filter {
            val digits = it.phone.filter { ch -> ch.isDigit() }
            val hasValidKey = FriendRequestRepository.isValidNostrKey(it.publicKey)
            val isShortCodeOrGhost = digits == "111" || it.name.trim() == "111" || it.phone.trim() == "111" || it.id == "c_111" || (!hasValidKey && digits.length < 6)

            !isShortCodeOrGhost && (
                it.status.contains("Connecté", ignoreCase = true) ||
                it.status.contains("Connected", ignoreCase = true) ||
                it.status.contains("Actif", ignoreCase = true)
            )
        }.forEach { friend ->
            val cleanDigits = friend.phone.filter { it.isDigit() }
            val convId = if (cleanDigits.length >= 6) "conv_$cleanDigits" else "conv_${friend.publicKey.take(16).ifBlank { friend.phone.take(16) }}"
            val exists = currentConvs.any {
                it.id == convId ||
                FriendRequestRepository.isSamePhone(it.id, friend.phone) ||
                it.participants.any { p -> FriendRequestRepository.isSamePhone(p, friend.phone) }
            }
            if (!exists) {
                currentConvs.add(0, Conversation(
                    id = convId,
                    title = friend.name.ifBlank { friend.phone },
                    participants = listOf("me", friend.phone),
                    lastMessage = defaultMessage,
                    updatedAt = System.currentTimeMillis(),
                    unreadCount = 0
                ))
                convsModified = true
            }
        }
        if (convsModified) {
            saveConversations(currentConvs)
        }
        return currentConvs
    }

    fun saveContacts(contacts: List<Contact>) {
        val array = JSONArray()
        contacts.forEach { array.put(it.toJson()) }
        contactsFile.writeText(JSONObject().put("contacts", array).toString(2), Charsets.UTF_8)
    }

    fun loadContacts(): List<Contact> {
        if (!contactsFile.exists()) return emptyList()
        val rawList = try {
            val obj = JSONObject(contactsFile.readText(Charsets.UTF_8))
            val array = obj.optJSONArray("contacts") ?: JSONArray()
            List(array.length()) { index -> Contact.fromJson(array.getJSONObject(index)) }
        } catch (_: Exception) {
            emptyList()
        }
        if (rawList.isEmpty()) return emptyList()

        val deduplicated = mutableListOf<Contact>()
        var modified = false

        for (item in rawList) {
            val matchIdx = deduplicated.indexOfFirst { existing ->
                (existing.id == item.id) ||
                (existing.phone.isNotBlank() && item.phone.isNotBlank() && FriendRequestRepository.isSamePhone(existing.phone, item.phone)) ||
                (existing.publicKey.isNotBlank() && item.publicKey.isNotBlank() && existing.publicKey.equals(item.publicKey, ignoreCase = true)) ||
                (existing.publicKey.isNotBlank() && existing.publicKey.equals(item.phone, ignoreCase = true)) ||
                (item.publicKey.isNotBlank() && item.publicKey.equals(existing.phone, ignoreCase = true))
            }

            if (matchIdx >= 0) {
                modified = true
                val existing = deduplicated[matchIdx]

                val preferExistingName = existing.name.isNotBlank() &&
                        !existing.name.startsWith("+") &&
                        existing.name != "O R B I S net" &&
                        existing.name != "Ami OrbisNet"
                val preferItemName = item.name.isNotBlank() &&
                        !item.name.startsWith("+") &&
                        item.name != "O R B I S net" &&
                        item.name != "Ami OrbisNet"

                val finalName = when {
                    preferExistingName -> existing.name
                    preferItemName -> item.name
                    existing.name.isNotBlank() && !existing.name.startsWith("+") -> existing.name
                    item.name.isNotBlank() && !item.name.startsWith("+") -> item.name
                    else -> existing.name.ifBlank { item.name }
                }

                val existingHasDigits = existing.phone.filter { it.isDigit() }.length >= 6
                val itemHasDigits = item.phone.filter { it.isDigit() }.length >= 6
                val finalPhone = when {
                    existingHasDigits -> existing.phone
                    itemHasDigits -> item.phone
                    else -> existing.phone.ifBlank { item.phone }
                }

                val finalPubkey = when {
                    FriendRequestRepository.isValidNostrKey(item.publicKey) && !FriendRequestRepository.isValidNostrKey(existing.publicKey) -> item.publicKey
                    FriendRequestRepository.isValidNostrKey(existing.publicKey) -> existing.publicKey
                    FriendRequestRepository.isValidNostrKey(item.publicKey) -> item.publicKey
                    existing.publicKey.isNotBlank() && !existing.publicKey.contains("PENDING", ignoreCase = true) -> existing.publicKey
                    item.publicKey.isNotBlank() && !item.publicKey.contains("PENDING", ignoreCase = true) -> item.publicKey
                    existing.publicKey.isNotBlank() -> existing.publicKey
                    else -> item.publicKey
                }
                val finalAvatar = existing.avatarPath ?: item.avatarPath
                val finalStatus = if (existing.status.contains("Connecté", ignoreCase = true) || item.status.contains("Connecté", ignoreCase = true)) {
                    "Connecté 🛡️"
                } else existing.status.ifBlank { item.status }

                deduplicated[matchIdx] = existing.copy(
                    name = finalName,
                    phone = finalPhone,
                    publicKey = finalPubkey,
                    avatarPath = finalAvatar,
                    status = finalStatus
                )
            } else {
                deduplicated.add(item)
            }
        }

        if (modified || deduplicated.size != rawList.size) {
            try {
                saveContacts(deduplicated)
            } catch (_: Exception) {}
        }

        return deduplicated
    }

    fun insertOrUpdateContact(contact: Contact) {
        val list = loadContacts().toMutableList()
        val index = list.indexOfFirst { existing ->
            existing.id == contact.id ||
            (existing.phone.isNotBlank() && contact.phone.isNotBlank() && FriendRequestRepository.isSamePhone(existing.phone, contact.phone)) ||
            (FriendRequestRepository.isValidNostrKey(existing.publicKey) && FriendRequestRepository.isValidNostrKey(contact.publicKey) && existing.publicKey.equals(contact.publicKey, ignoreCase = true))
        }
        if (index >= 0) {
            val existing = list[index]
            val finalPubkey = when {
                FriendRequestRepository.isValidNostrKey(contact.publicKey) -> contact.publicKey
                FriendRequestRepository.isValidNostrKey(existing.publicKey) -> existing.publicKey
                contact.publicKey.isNotBlank() && !contact.publicKey.contains("PENDING", ignoreCase = true) -> contact.publicKey
                existing.publicKey.isNotBlank() && !existing.publicKey.contains("PENDING", ignoreCase = true) -> existing.publicKey
                contact.publicKey.isNotBlank() -> contact.publicKey
                else -> existing.publicKey
            }
            val finalStatus = when {
                contact.status.contains("Connecté", ignoreCase = true) -> contact.status
                existing.status.contains("Connecté", ignoreCase = true) -> existing.status
                contact.status.isNotBlank() -> contact.status
                else -> existing.status
            }
            list[index] = existing.copy(
                name = if (contact.name.isNotBlank() && !contact.name.startsWith("+") && contact.name != "O R B I S net") contact.name else existing.name,
                phone = if (contact.phone.filter { it.isDigit() }.length >= 6) contact.phone else existing.phone,
                publicKey = finalPubkey,
                avatarPath = contact.avatarPath ?: existing.avatarPath,
                status = finalStatus
            )
        } else {
            list.add(contact)
        }
        saveContacts(list)
    }

    fun addMessage(conversationId: String, message: Message) {
        val store = messageStore
        store.addMessage(conversationId, message)
        if (message.senderId != "me" && message.text.isNotBlank()) {
            val peerId = message.senderId.ifBlank { conversationId }
            try {
                com.sha.orbis.ai.affinity.OrbisPeerLanguageEngine.onIncomingMessageReceived(context, peerId, message.text)
            } catch (_: Exception) {}
        }
    }

    fun saveMessages(conversationId: String, messages: List<Message>) {
        val store = messageStore
        store.saveConversationMessages(conversationId, messages)
    }

    fun updateMessageStatus(conversationId: String, messageId: String, status: com.sha.orbis.model.MessageDeliveryStatus) {
        val store = messageStore
        store.updateMessageStatus(conversationId, messageId, status)
    }

    fun updateMessageNetworkEventId(conversationId: String, messageId: String, networkEventId: String) {
        val store = messageStore
        store.updateMessageNetworkEventId(conversationId, messageId, networkEventId)
    }

    fun updateDisappearingTimer(conversationId: String, seconds: Long) {
        val list = loadConversations().toMutableList()
        val index = list.indexOfFirst { it.id == conversationId }
        if (index >= 0) {
            list[index] = list[index].copy(disappearingTimerSeconds = seconds)
            saveConversations(list)
        }
    }

    fun deleteMessageForMe(conversationId: String, messageId: String) {
        val store = messageStore
        store.deleteMessageForMe(conversationId, messageId)
    }

    fun deleteMessageForEveryone(conversationId: String?, messageId: String): String? {
        return deleteMessageForEveryone(conversationId, MessageRevocationTarget(messageId = messageId))
    }

    fun deleteMessageForEveryone(conversationId: String?, target: MessageRevocationTarget): String? {
        val store = messageStore
        val matchedConvId = store.deleteMessageForEveryone(conversationId, target)

        // Update preview in conversations list if matched
        if (matchedConvId != null) {
            val list = loadConversations().toMutableList()
            val index = list.indexOfFirst { it.id == matchedConvId }
            if (index >= 0) {
                val msgs = store.loadConversationMessages(matchedConvId)
                val lastMsg = msgs.lastOrNull()
                val newPreview = when {
                    lastMsg == null -> ""
                    lastMsg.isDeletedForEveryone -> context.getString(com.sha.orbis.R.string.chat_msg_deleted_by_sender)
                    else -> lastMsg.text
                }
                list[index] = list[index].copy(lastMessage = newPreview)
                saveConversations(list)
            }
        }
        return matchedConvId
    }

    fun clearConversation(conversationId: String) {
        val store = messageStore
        store.clearConversation(conversationId)
    }

    fun deleteConversation(conversationId: String) {
        clearConversation(conversationId)
        val list = loadConversations().toMutableList()
        val toRemove = list.filter { it.id == conversationId }
        list.removeAll { it.id == conversationId }
        saveConversations(list)

        // Also clean up any orphan/invalid contact or friend request tied to this deleted conversation
        try {
            toRemove.forEach { conv ->
                val otherPhone = conv.participants.firstOrNull { it != "me" } ?: ""
                val digits = otherPhone.filter { it.isDigit() }
                if (digits == "111" || otherPhone.trim() == "111" || conv.id == "conv_111" || (digits.length < 6 && !FriendRequestRepository.isValidNostrKey(otherPhone))) {
                    val contacts = loadContacts().toMutableList()
                    if (contacts.removeAll { it.id == conv.id || it.phone == otherPhone || it.name == conv.title || it.phone.filter { c -> c.isDigit() } == "111" }) {
                        saveContacts(contacts)
                    }
                    val friendRepo = FriendRequestRepository(context, currentAccountId)
                    val reqs = friendRepo.loadRequests().toMutableList()
                    if (reqs.removeAll { FriendRequestRepository.isSamePhone(it.senderPhone, otherPhone) || it.senderPhone == otherPhone || it.senderPhone.filter { c -> c.isDigit() } == "111" }) {
                        friendRepo.saveRequests(reqs)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun purgeInvalidGhostContacts() {
        try {
            if (contactsFile.exists()) {
                val contacts = loadContacts().toMutableList()
                val removed = contacts.removeAll { c ->
                    val digits = c.phone.filter { it.isDigit() }
                    (digits == "111" || c.name.trim() == "111" || c.phone.trim() == "111" || c.id == "c_111") ||
                    (digits.length < 6 && !FriendRequestRepository.isValidNostrKey(c.publicKey))
                }
                if (removed) {
                    saveContacts(contacts)
                }
            }
            if (storageFile.exists()) {
                val rawConvs = try {
                    val obj = JSONObject(storageFile.readText(Charsets.UTF_8))
                    val array = obj.optJSONArray("conversations") ?: JSONArray()
                    List(array.length()) { index -> Conversation.fromJson(array.getJSONObject(index)) }
                } catch (_: Exception) { emptyList() }

                val filteredConvs = rawConvs.filterNot { conv ->
                    val other = conv.participants.firstOrNull { it != "me" } ?: ""
                    val digits = other.filter { it.isDigit() }
                    conv.id == "conv_111" ||
                    conv.title.trim() == "111" ||
                    digits == "111" ||
                    other.trim() == "111" ||
                    (digits.length < 6 && !FriendRequestRepository.isValidNostrKey(other) && !conv.isGroup && digits.isNotBlank())
                }
                if (filteredConvs.size != rawConvs.size) {
                    saveConversations(filteredConvs)
                }
            }
            // Also clean from FriendRequestRepository
            val friendRepo = FriendRequestRepository(context, currentAccountId)
            val requests = friendRepo.loadRequests().toMutableList()
            val removedReq = requests.removeAll { req ->
                val digits = req.senderPhone.filter { it.isDigit() }
                digits == "111" || req.senderName.trim() == "111" || req.senderPhone.trim() == "111" || req.id == "req_111" || req.id == "sent_111" ||
                (digits.length < 6 && !FriendRequestRepository.isValidNostrKey(req.senderPublicKey) && digits.isNotBlank())
            }
            if (removedReq) {
                friendRepo.saveRequests(requests)
            }
        } catch (_: Exception) {}
    }

    fun addReactionToMessage(conversationId: String?, messageId: String, senderPhone: String, emoji: String): String? {
        val store = messageStore
        return store.addReactionToMessage(conversationId, messageId, senderPhone, emoji)
    }

    fun loadMessages(conversationId: String): List<Message> = messageStore.loadConversationMessages(conversationId)
}
