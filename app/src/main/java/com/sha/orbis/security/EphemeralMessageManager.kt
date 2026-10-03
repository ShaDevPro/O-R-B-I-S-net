package com.sha.orbis.security

import android.content.Context
import com.sha.orbis.model.Message
import com.sha.orbis.storage.ConversationRepository
import org.json.JSONObject
import java.io.File

object EphemeralMessageManager {

    private const val PREFS_FILE = "orbis_ephemeral_timers.json"

    fun setTimerForConversation(context: Context, convId: String, durationMs: Long) {
        val prefs = loadTimers(context)
        prefs.put(convId, durationMs)
        saveTimers(context, prefs)
    }

    fun getTimerForConversation(context: Context, convId: String): Long {
        val prefs = loadTimers(context)
        return prefs.optLong(convId, 0L)
    }

    private fun loadTimers(context: Context): JSONObject {
        val file = File(context.filesDir, PREFS_FILE)
        if (!file.exists()) return JSONObject()
        return try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun saveTimers(context: Context, json: JSONObject) {
        try {
            val file = File(context.filesDir, PREFS_FILE)
            file.writeText(json.toString(2))
        } catch (_: Exception) {}
    }

    /**
     * Purges expired ephemeral messages in a given conversation.
     * Returns the updated list of active messages.
     */
    fun purgeExpiredMessages(context: Context, convId: String, messages: List<Message>): List<Message> {
        val timerMs = getTimerForConversation(context, convId)
        if (timerMs <= 0L) return messages

        val now = System.currentTimeMillis()
        val active = messages.filter { msg ->
            val age = now - msg.timestamp
            age < timerMs
        }

        if (active.size != messages.size) {
            val repo = ConversationRepository(context)
            repo.saveMessages(convId, active)
        }
        return active
    }
}
