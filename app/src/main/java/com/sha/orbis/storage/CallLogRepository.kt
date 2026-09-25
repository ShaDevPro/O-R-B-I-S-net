package com.sha.orbis.storage

import android.content.Context
import com.sha.orbis.model.CallDirection
import com.sha.orbis.model.CallGroup
import com.sha.orbis.model.CallRecord
import org.json.JSONArray
import java.io.File
import java.util.Calendar

class CallLogRepository(
    private val context: Context,
    private val accountId: String? = null
) {
    private val currentAccountId: String = accountId?.takeIf { it.isNotBlank() } ?: try {
        com.sha.orbis.data.SessionManager(context).activeAccountId
    } catch (_: Exception) { "" }

    private val storageFile: File by lazy {
        AccountStorageManager.getAccountFile(context, "call_history.json", currentAccountId)
    }

    @Synchronized
    fun loadCalls(): List<CallRecord> {
        if (!storageFile.exists()) {
            return emptyList()
        }
        return try {
            val jsonStr = storageFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<CallRecord>()
            for (i in 0 until array.length()) {
                list.add(CallRecord.fromJson(array.getJSONObject(i)))
            }
            list.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveCalls(calls: List<CallRecord>) {
        try {
            val array = JSONArray()
            calls.forEach { array.put(it.toJson()) }
            storageFile.writeText(array.toString())
        } catch (e: Exception) {
            android.util.Log.e("CallLogRepository", "Failed to save call logs: ${e.message}")
        }
    }

    @Synchronized
    fun addCall(call: CallRecord) {
        val current = loadCalls().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == call.id }
        if (existingIndex >= 0) {
            current[existingIndex] = call
        } else {
            current.add(0, call)
        }
        saveCalls(current)
    }

    @Synchronized
    fun updateCallDuration(id: String, durationSeconds: Int) {
        val current = loadCalls().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = current[index]
            current[index] = old.copy(durationSeconds = durationSeconds)
            saveCalls(current)
        }
    }

    @Synchronized
    fun deleteCall(id: String) {
        val current = loadCalls().filterNot { it.id == id }
        saveCalls(current)
    }

    @Synchronized
    fun clearAll() {
        try {
            if (storageFile.exists()) {
                storageFile.delete()
            }
        } catch (_: Exception) {}
    }

    fun getMissedCount(): Int {
        return loadCalls().count { it.direction == CallDirection.MISSED }
    }

    fun getUnreadMissedCount(): Int {
        return loadCalls().count { it.direction == CallDirection.MISSED && !it.isRead }
    }

    @Synchronized
    fun markAllMissedAsRead() {
        val calls = loadCalls().toMutableList()
        var modified = false
        for (i in calls.indices) {
            if (calls[i].direction == CallDirection.MISSED && !calls[i].isRead) {
                calls[i] = calls[i].copy(isRead = true)
                modified = true
            }
        }
        if (modified) {
            saveCalls(calls)
        }
    }

    @Synchronized
    fun markMissedAsReadForPhone(peerPhone: String) {
        if (peerPhone.isBlank()) return
        val calls = loadCalls().toMutableList()
        var modified = false
        for (i in calls.indices) {
            if (calls[i].direction == CallDirection.MISSED && !calls[i].isRead &&
                (calls[i].peerPhone == peerPhone || FriendRequestRepository.isSamePhone(calls[i].peerPhone, peerPhone))) {
                calls[i] = calls[i].copy(isRead = true)
                modified = true
            }
        }
        if (modified) {
            saveCalls(calls)
        }
    }

    /**
     * Regroupe les appels consécutifs vers la même personne (style iOS Phone).
     * Règle : un nouveau groupe est créé à chaque changement de peerPhone dans
     * la liste triée par timestamp décroissant.
     *
     * Ex : [A, A, B, A] → [[A,A], [B], [A]]
     */
    fun loadCallGroups(): List<CallGroup> {
        val sorted = loadCalls() // déjà trié timestamp DESC
        if (sorted.isEmpty()) return emptyList()

        val groups = mutableListOf<CallGroup>()
        var currentPhone = sorted.first().peerPhone
        var currentBatch = mutableListOf<CallRecord>()

        for (call in sorted) {
            if (call.peerPhone == currentPhone) {
                currentBatch.add(call)
            } else {
                groups.add(buildGroup(currentBatch))
                currentPhone = call.peerPhone
                currentBatch = mutableListOf(call)
            }
        }
        if (currentBatch.isNotEmpty()) groups.add(buildGroup(currentBatch))
        return groups
    }

    /**
     * Retourne les groupes organisés par sections de date :
     * clés : "TODAY", "YESTERDAY", "MON" … "SUN" (this week), "DD MMM" (older)
     * L'ordre des clés reflète l'ordre chronologique décroissant.
     */
    fun loadCallsGroupedByDate(locale: java.util.Locale = java.util.Locale.getDefault()): Map<String, List<CallGroup>> {
        val groups = loadCallGroups()
        val now = Calendar.getInstance()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterdayStart = todayStart - 86_400_000L
        val weekStart = todayStart - 6 * 86_400_000L

        val result = linkedMapOf<String, MutableList<CallGroup>>()
        for (group in groups) {
            val key = when {
                group.lastTimestamp >= todayStart      -> "TODAY"
                group.lastTimestamp >= yesterdayStart  -> "YESTERDAY"
                group.lastTimestamp >= weekStart       -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = group.lastTimestamp }
                    java.text.SimpleDateFormat("EEEE", locale).format(cal.time).uppercase(locale)
                }
                else -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = group.lastTimestamp }
                    java.text.SimpleDateFormat("d MMM", locale).format(cal.time)
                }
            }
            result.getOrPut(key) { mutableListOf() }.add(group)
        }
        return result
    }

    private fun buildGroup(batch: List<CallRecord>): CallGroup {
        val first = batch.first()
        return CallGroup(
            peerPhone     = first.peerPhone,
            peerName      = first.peerName,
            peerAvatar    = first.peerAvatar,
            calls         = batch,
            missedCount   = batch.count { it.direction == CallDirection.MISSED },
            hasVideo      = batch.any { it.isVideo },
            hasVoice      = batch.any { !it.isVideo },
            lastTimestamp = first.timestamp
        )
    }

    /** Supprime tous les appels appartenant à un groupe (par liste d'ids). */
    fun deleteGroup(callIds: List<String>) {
        val current = loadCalls().filterNot { it.id in callIds }
        saveCalls(current)
    }

    /**
     * Regroupe TOUS les appels d'un même utilisateur / contact dans un unique CallGroup.
     * Les appels à l'intérieur du groupe sont triés du plus récent au plus ancien.
     * Les groupes sont classés selon l'interaction la plus récente.
     */
    fun loadCallsGroupedByUser(): List<CallGroup> {
        val sorted = loadCalls() // Déjà trié par timestamp DESC
        if (sorted.isEmpty()) return emptyList()

        val map = linkedMapOf<String, MutableList<CallRecord>>()
        for (call in sorted) {
            val key = call.peerPhone.ifBlank { call.peerName }
            map.getOrPut(key) { mutableListOf() }.add(call)
        }

        return map.values.map { buildGroup(it) }
    }

    /** Supprime tous les appels enregistrés pour un contact donné. */
    fun deleteCallsForUser(peerPhone: String) {
        val current = loadCalls().filterNot {
            it.peerPhone.equals(peerPhone, ignoreCase = true) ||
            (it.peerPhone.isBlank() && it.peerName.equals(peerPhone, ignoreCase = true))
        }
        saveCalls(current)
    }
}
