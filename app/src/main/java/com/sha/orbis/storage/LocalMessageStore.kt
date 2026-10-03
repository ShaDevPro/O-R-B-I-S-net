package com.sha.orbis.storage

import android.content.Context
import com.sha.orbis.model.Message
import com.sha.orbis.model.MessageDeliveryStatus
import com.sha.orbis.model.MessageRevocation
import com.sha.orbis.model.MessageRevocationTarget
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LocalMessageStore(
    private val context: Context,
    private val accountId: String? = null
) {
    private val currentAccountId: String = accountId?.takeIf { it.isNotBlank() } ?: try {
        com.sha.orbis.data.SessionManager(context).activeAccountId
    } catch (_: Exception) { "" }

    private val dir: File = AccountStorageManager.getAccountSubdir(context, "messages", currentAccountId)

    companion object {
        fun isSpamOrSyncText(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            val trimmed = text.trim()
            if (com.sha.orbis.sync.protocol.SovereignSyncProtocol.isSyncPacket(trimmed) ||
                trimmed.startsWith("[ORBIS_PEER_SYNC") ||
                trimmed.contains("[ORBIS_PEER_SYNC") ||
                trimmed.contains("\"action\":\"EXCHANGE_REQUEST\"") ||
                trimmed.contains("\"action\":\"EXCHANGE_RESPONSE\"") ||
                trimmed.contains("\"action\":\"DELTA_EXCHANGE\"") ||
                trimmed.contains("\"action\":\"HEARTBEAT\"") ||
                trimmed.contains("\"action\":\"SOVEREIGN_HELLO\"") ||
                trimmed.contains("\"targetPeerPhone\":") ||
                (trimmed.contains("\"senderPubkey\":") && trimmed.contains("\"bundle\":")) ||
                (trimmed.startsWith("{") && trimmed.contains("\"bundle\":{")) ||
                (trimmed.startsWith("{") && trimmed.contains("\"action\":") && trimmed.contains("\"version\":"))
            ) {
                return true
            }
            return trimmed.startsWith("🔒 Demande d'invitation") ||
                   trimmed.startsWith("✅ Invitation acceptée") ||
                   trimmed.startsWith("🔒 Invitation reçue") ||
                   trimmed.contains("Acceptez pour activer le canal chiffré") ||
                   trimmed.contains("Clés de sécurité synchronisées") ||
                   trimmed.contains("Clés synchronisées")
        }

        fun isSpamOrSyncMessage(msg: Message): Boolean {
            if (msg.senderId == "system") return true
            return isSpamOrSyncText(msg.text)
        }
    }

    private fun isSpamSystemMessage(msg: Message): Boolean = isSpamOrSyncMessage(msg)

    fun saveConversationMessages(conversationId: String, messages: List<Message>) {
        val file = File(dir, "$conversationId.json")
        val array = JSONArray()
        // Always filter out system spam & deduplicate by ID before persisting
        val cleanMessages = messages.filterNot { isSpamSystemMessage(it) }.distinctBy { it.id }
        cleanMessages.forEach { array.put(it.toJson()) }
        file.writeText(JSONObject().put("messages", array).toString(2), Charsets.UTF_8)
    }

    fun loadConversationMessages(conversationId: String): List<Message> {
        val file = File(dir, "$conversationId.json")
        if (!file.exists()) return emptyList()

        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val array = json.optJSONArray("messages") ?: JSONArray()
            val list = List(array.length()) { index -> Message.fromJson(array.getJSONObject(index)) }

            // Automatic local purging of expired disappearing messages and system spam filtering
            val now = System.currentTimeMillis()
            val valid = list.filter { msg ->
                (msg.expiresAt == null || msg.expiresAt > now) && !isSpamSystemMessage(msg)
            }.distinctBy { it.id }

            if (valid.size != list.size) {
                saveConversationMessages(conversationId, valid)
            }
            valid
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addMessage(conversationId: String, message: Message) {
        if (isSpamSystemMessage(message)) return

        val current = loadConversationMessages(conversationId).toMutableList()
        val existingIndex = current.indexOfFirst { it.id == message.id }
        if (existingIndex >= 0) {
            current[existingIndex] = message
        } else {
            current.add(message)
        }
        saveConversationMessages(conversationId, current)
    }

    fun updateMessageStatus(conversationId: String, messageId: String, status: MessageDeliveryStatus) {
        val current = loadConversationMessages(conversationId).toMutableList()
        val index = current.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            current[index] = current[index].copy(status = status)
            saveConversationMessages(conversationId, current)
            return
        }

        // Recherche résiliente si conversationId est formaté différemment (ex: digits vs nom)
        try {
            dir.listFiles { f -> f.extension == "json" }?.forEach { f ->
                val otherConvId = f.nameWithoutExtension
                if (otherConvId != conversationId) {
                    val otherList = loadConversationMessages(otherConvId).toMutableList()
                    val idx = otherList.indexOfFirst { it.id == messageId }
                    if (idx >= 0) {
                        otherList[idx] = otherList[idx].copy(status = status)
                        saveConversationMessages(otherConvId, otherList)
                        return
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun updateMessageNetworkEventId(conversationId: String, messageId: String, networkEventId: String) {
        if (networkEventId.isBlank()) return

        val current = loadConversationMessages(conversationId).toMutableList()
        val index = current.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            current[index] = current[index].copy(networkEventId = networkEventId)
            saveConversationMessages(conversationId, current)
            return
        }

        try {
            dir.listFiles { f -> f.extension == "json" }?.forEach { f ->
                val otherConvId = f.nameWithoutExtension
                if (otherConvId != conversationId) {
                    val otherList = loadConversationMessages(otherConvId).toMutableList()
                    val idx = otherList.indexOfFirst { it.id == messageId }
                    if (idx >= 0) {
                        otherList[idx] = otherList[idx].copy(networkEventId = networkEventId)
                        saveConversationMessages(otherConvId, otherList)
                        return
                    }
                }
            }
        } catch (_: Exception) {}
    }


    private fun cleanupMessageMedia(messageId: String, text: String = "") {
        try {
            val voiceDir = File(context.filesDir, "voice_notes")
            if (voiceDir.exists()) {
                val voiceFile = File(voiceDir, "voice_${messageId}.amr")
                if (voiceFile.exists()) voiceFile.delete()
            }
        } catch (_: Exception) {}

        // Purge physique de vidéo si présente
        try {
            if (com.sha.orbis.media.VideoMediaHelper.isVideoPayload(text)) {
                val vid = com.sha.orbis.media.VideoMediaHelper.parseVideoPayload(text)
                if (vid != null) {
                    com.sha.orbis.media.VideoMediaHelper.deleteVideoPhysical(context, vid.id)
                }
            } else {
                com.sha.orbis.media.VideoMediaHelper.deleteVideoPhysical(context, messageId)
            }
        } catch (_: Exception) {}
    }

    fun deleteMessageForMe(conversationId: String, messageId: String) {
        val current = loadConversationMessages(conversationId).toMutableList()
        val targetMsg = current.find { it.id == messageId }
        current.removeAll { it.id == messageId }
        saveConversationMessages(conversationId, current)
        cleanupMessageMedia(messageId, targetMsg?.text.orEmpty())
    }

    fun deleteMessageForEveryone(conversationId: String?, messageId: String): String? {
        return deleteMessageForEveryone(
            conversationId = conversationId,
            target = MessageRevocationTarget(messageId = messageId)
        )
    }

    fun deleteMessageForEveryone(conversationId: String?, target: MessageRevocationTarget): String? {
        // 1. Try specified conversationId first
        if (!conversationId.isNullOrBlank()) {
            val current = loadConversationMessages(conversationId).toMutableList()
            val index = MessageRevocation.findMatchIndex(current, target)
            if (index >= 0) {
                val old = current[index]
                cleanupMessageMedia(old.id, old.text)
                current[index] = old.copy(isDeletedForEveryone = true, text = "")
                saveConversationMessages(conversationId, current)
                android.util.Log.i("LocalMessageStore", "deleteMessageForEveryone: message ${target.messageId ?: target.mediaId ?: target.networkEventId} supprimé dans $conversationId")
                return conversationId
            }
        }

        // 2. Fallback: Search across all conversation files in messages directory
        val jsonFiles = dir.listFiles { file -> file.isFile && file.extension == "json" } ?: emptyArray()
        for (file in jsonFiles) {
            val cId = file.nameWithoutExtension
            val current = loadConversationMessages(cId).toMutableList()
            val index = MessageRevocation.findMatchIndex(current, target)
            if (index >= 0) {
                val old = current[index]
                cleanupMessageMedia(old.id, old.text)
                current[index] = old.copy(isDeletedForEveryone = true, text = "")
                saveConversationMessages(cId, current)
                android.util.Log.i("LocalMessageStore", "deleteMessageForEveryone: message ${target.messageId ?: target.mediaId ?: target.networkEventId} trouvé et supprimé dans $cId (omni-scan)")
                return cId
            }
        }
        android.util.Log.w("LocalMessageStore", "deleteMessageForEveryone: cible non trouvée ${target.messageId ?: target.mediaId ?: target.networkEventId}")
        return null
    }

    fun addReactionToMessage(conversationId: String?, messageId: String, senderPhone: String, emoji: String): String? {
        if (!conversationId.isNullOrBlank()) {
            val current = loadConversationMessages(conversationId).toMutableList()
            val index = current.indexOfFirst { it.id == messageId }
            if (index >= 0) {
                val old = current[index]
                val currentReactions = old.reactions.toMutableMap()
                val existingEntry = currentReactions.entries.find { FriendRequestRepository.isSamePhone(it.key, senderPhone) }
                if (existingEntry != null) {
                    if (existingEntry.value == emoji) {
                        currentReactions.remove(existingEntry.key) // Toggle off
                    } else {
                        currentReactions[existingEntry.key] = emoji // Switch emoji
                    }
                } else {
                    currentReactions[senderPhone] = emoji
                }
                current[index] = old.copy(reactions = currentReactions)
                saveConversationMessages(conversationId, current)
                return conversationId
            }
        }

        val jsonFiles = dir.listFiles { file -> file.isFile && file.extension == "json" } ?: emptyArray()
        for (file in jsonFiles) {
            val cId = file.nameWithoutExtension
            val current = loadConversationMessages(cId).toMutableList()
            val index = current.indexOfFirst { it.id == messageId }
            if (index >= 0) {
                val old = current[index]
                val currentReactions = old.reactions.toMutableMap()
                val existingEntry = currentReactions.entries.find { FriendRequestRepository.isSamePhone(it.key, senderPhone) }
                if (existingEntry != null) {
                    if (existingEntry.value == emoji) {
                        currentReactions.remove(existingEntry.key)
                    } else {
                        currentReactions[existingEntry.key] = emoji
                    }
                } else {
                    currentReactions[senderPhone] = emoji
                }
                current[index] = old.copy(reactions = currentReactions)
                saveConversationMessages(cId, current)
                return cId
            }
        }
        return null
    }

    fun clearConversation(conversationId: String) {
        try {
            val msgs = loadConversationMessages(conversationId)
            msgs.forEach { cleanupMessageMedia(it.id, it.text) }
        } catch (_: Exception) {}
        val file = File(dir, "$conversationId.json")
        if (file.exists()) file.delete()
    }

    fun getTotalMessagesCount(): Int {
        val jsonFiles = dir.listFiles { file -> file.isFile && file.extension == "json" } ?: return 0
        var total = 0
        for (file in jsonFiles) {
            try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                total += json.optJSONArray("messages")?.length() ?: 0
            } catch (_: Exception) {}
        }
        return total
    }
}
