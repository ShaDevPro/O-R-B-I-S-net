package com.sha.orbis.storage

import android.content.Context
import com.sha.orbis.model.Conversation
import com.sha.orbis.model.PrivateConversationLock
import com.sha.orbis.security.SecureAtRestStorage
import org.json.JSONArray
import java.io.File

class PrivateConversationRepository(
    private val context: Context,
    private val accountId: String? = null
) {
    private val currentAccountId: String = accountId?.takeIf { it.isNotBlank() } ?: try {
        com.sha.orbis.data.SessionManager(context).activeAccountId
    } catch (_: Exception) { "" }

    private val storageFile: File by lazy {
        AccountStorageManager.getAccountFile(context, "private_conversations.enc", currentAccountId)
    }

    @Synchronized
    fun loadLocks(): List<PrivateConversationLock> {
        val raw = SecureAtRestStorage.readEncryptedFile(storageFile)?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()

        return try {
            val array = JSONArray(raw)
            List(array.length()) { index -> PrivateConversationLock.fromJson(array.getJSONObject(index)) }
                .filter { it.conversationId.isNotBlank() && it.password.isNotBlank() }
                .sortedByDescending { it.lockedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveLocks(locks: List<PrivateConversationLock>) {
        val array = JSONArray()
        locks
            .distinctBy { it.conversationId }
            .forEach { array.put(it.toJson()) }
        SecureAtRestStorage.writeEncryptedFile(storageFile, array.toString())
    }

    fun isLocked(conversationId: String): Boolean =
        getLock(conversationId) != null

    fun getLock(conversationId: String): PrivateConversationLock? =
        loadLocks().firstOrNull { it.conversationId == conversationId }

    @Synchronized
    fun lockConversation(
        conversation: Conversation,
        password: String,
        recoveryAnswers: List<String>
    ) {
        val answers = recoveryAnswers.map { normalizeAnswer(it) }
        val current = loadLocks().filterNot { it.conversationId == conversation.id }.toMutableList()
        current.add(
            0,
            PrivateConversationLock(
                conversationId = conversation.id,
                conversationTitle = conversation.title,
                password = password.trim(),
                recoveryAnswer1 = answers.getOrNull(0).orEmpty(),
                recoveryAnswer2 = answers.getOrNull(1).orEmpty(),
                recoveryAnswer3 = answers.getOrNull(2).orEmpty()
            )
        )
        saveLocks(current)
    }

    @Synchronized
    fun unlockConversation(conversationId: String): Boolean {
        val current = loadLocks().toMutableList()
        val removed = current.removeAll { it.conversationId == conversationId }
        if (removed) saveLocks(current)
        return removed
    }

    fun verifyPassword(conversationId: String, password: String): Boolean =
        getLock(conversationId)?.password == password.trim()

    fun verifyRecoveryAnswers(conversationId: String, answers: List<String>): Boolean {
        val lock = getLock(conversationId) ?: return false
        val normalized = answers.map { normalizeAnswer(it) }
        return lock.recoveryAnswer1 == normalized.getOrNull(0).orEmpty() &&
            lock.recoveryAnswer2 == normalized.getOrNull(1).orEmpty() &&
            lock.recoveryAnswer3 == normalized.getOrNull(2).orEmpty()
    }

    fun recoverPassword(conversationId: String, answers: List<String>): String? {
        val lock = getLock(conversationId) ?: return null
        return if (verifyRecoveryAnswers(conversationId, answers)) lock.password else null
    }

    @Synchronized
    fun changePassword(conversationId: String, newPassword: String, recoveryAnswers: List<String>): Boolean {
        val current = loadLocks().toMutableList()
        val index = current.indexOfFirst { it.conversationId == conversationId }
        if (index < 0) return false

        val answers = recoveryAnswers.map { normalizeAnswer(it) }
        val old = current[index]
        current[index] = old.copy(
            password = newPassword.trim(),
            recoveryAnswer1 = answers.getOrNull(0).orEmpty(),
            recoveryAnswer2 = answers.getOrNull(1).orEmpty(),
            recoveryAnswer3 = answers.getOrNull(2).orEmpty()
        )
        saveLocks(current)
        return true
    }

    private fun normalizeAnswer(answer: String): String =
        answer.trim().lowercase()
}
