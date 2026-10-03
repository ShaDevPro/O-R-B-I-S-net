package com.sha.orbis.ai.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages persistent on-device continual learning and weight adaptation.
 * Allows ORBIS AI models to improve directly from user interactions without internet access.
 */
class OrbisAiStorage(private val context: Context? = null) {

    private val storageFile: File by lazy {
        val baseDir = try {
            context?.filesDir
        } catch (_: Exception) {
            null
        } ?: File(System.getProperty("java.io.tmpdir"), "orbis_ai_storage")
        baseDir.mkdirs()
        File(baseDir, "orbis_ai_learning_store.json")
    }

    data class GuardDelta(
        val threatTypeName: String,
        val vectorDelta: FloatArray,
        val sampleCount: Int
    )

    data class ReplyPreference(
        val intentName: String,
        val replyText: String,
        val frequency: Int,
        val lastUsedTimestamp: Long
    )

    @Synchronized
    fun loadGuardDeltas(): Map<String, GuardDelta> {
        if (!storageFile.exists()) return emptyMap()
        return try {
            val json = JSONObject(storageFile.readText())
            val guardObj = json.optJSONObject("guard_deltas") ?: return emptyMap()
            val result = mutableMapOf<String, GuardDelta>()

            for (key in guardObj.keys()) {
                val item = guardObj.getJSONObject(key)
                val count = item.optInt("count", 1)
                val arr = item.getJSONArray("weights")
                val vec = FloatArray(arr.length())
                for (i in 0 until arr.length()) {
                    vec[i] = arr.getDouble(i).toFloat()
                }
                result[key] = GuardDelta(key, vec, count)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    @Synchronized
    fun saveGuardDelta(threatType: String, delta: FloatArray) {
        try {
            val root = if (storageFile.exists()) {
                try { JSONObject(storageFile.readText()) } catch (_: Exception) { JSONObject() }
            } else {
                JSONObject()
            }

            val guardObj = root.optJSONObject("guard_deltas") ?: JSONObject()
            val current = guardObj.optJSONObject(threatType)

            val newCount: Int
            val combinedVec: FloatArray

            if (current != null) {
                val oldCount = current.optInt("count", 1)
                newCount = oldCount + 1
                val arr = current.getJSONArray("weights")
                combinedVec = FloatArray(delta.size)
                for (i in delta.indices) {
                    val oldVal = if (i < arr.length()) arr.getDouble(i).toFloat() else 0.0f
                    // Exponential Moving Average (EMA) adaptation
                    combinedVec[i] = oldVal * 0.85f + delta[i] * 0.15f
                }
            } else {
                newCount = 1
                combinedVec = delta.copyOf()
            }

            val jsonArr = JSONArray()
            for (v in combinedVec) {
                jsonArr.put(v.toDouble())
            }

            val itemObj = JSONObject().apply {
                put("count", newCount)
                put("weights", jsonArr)
            }

            guardObj.put(threatType, itemObj)
            root.put("guard_deltas", guardObj)
            storageFile.writeText(root.toString(2))
        } catch (_: Exception) {}
    }

    @Synchronized
    fun loadReplyPreferences(): List<ReplyPreference> {
        if (!storageFile.exists()) return emptyList()
        return try {
            val json = JSONObject(storageFile.readText())
            val arr = json.optJSONArray("reply_preferences") ?: return emptyList()
            val list = mutableListOf<ReplyPreference>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ReplyPreference(
                        intentName = obj.getString("intent"),
                        replyText = obj.getString("text"),
                        frequency = obj.optInt("freq", 1),
                        lastUsedTimestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list.sortedByDescending { it.frequency }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun recordReplyPreference(intent: String, replyText: String) {
        try {
            val root = if (storageFile.exists()) {
                try { JSONObject(storageFile.readText()) } catch (_: Exception) { JSONObject() }
            } else {
                JSONObject()
            }

            val current = loadReplyPreferences().toMutableList()
            val existingIdx = current.indexOfFirst {
                it.intentName == intent && it.replyText.equals(replyText.trim(), ignoreCase = true)
            }

            if (existingIdx >= 0) {
                val old = current[existingIdx]
                current[existingIdx] = old.copy(
                    frequency = old.frequency + 1,
                    lastUsedTimestamp = System.currentTimeMillis()
                )
            } else {
                current.add(
                    ReplyPreference(
                        intentName = intent,
                        replyText = replyText.trim(),
                        frequency = 1,
                        lastUsedTimestamp = System.currentTimeMillis()
                    )
                )
            }

            // Cap list to top 150 customized responses to save memory
            val sorted = current.sortedByDescending { it.frequency }.take(150)
            val jsonArr = JSONArray()
            for (pref in sorted) {
                jsonArr.put(JSONObject().apply {
                    put("intent", pref.intentName)
                    put("text", pref.replyText)
                    put("freq", pref.frequency)
                    put("timestamp", pref.lastUsedTimestamp)
                })
            }

            root.put("reply_preferences", jsonArr)
            storageFile.writeText(root.toString(2))
        } catch (_: Exception) {}
    }

    /**
     * Retrieves the persistent language affinity recorded for a specific contact or peer.
     * Returns "dz", "fr", "ar", "en", or null if not yet learned.
     */
    @Synchronized
    fun getContactLanguage(peerId: String): String? {
        if (peerId.isBlank() || !storageFile.exists()) return null
        return try {
            val json = JSONObject(storageFile.readText())
            val affObj = json.optJSONObject("contact_language_affinity") ?: return null
            val cleanKey = peerId.trim().lowercase()
            val item = affObj.optJSONObject(cleanKey) ?: return null
            val lang = item.optString("lang")
            if (lang.isNullOrBlank()) null else lang
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Updates the persistent language affinity for a contact or peer based on interactions.
     */
    @Synchronized
    fun recordContactLanguage(peerId: String, langCode: String, forceManual: Boolean = false) {
        if (peerId.isBlank() || langCode.isBlank()) return
        try {
            val root = if (storageFile.exists()) {
                try { JSONObject(storageFile.readText()) } catch (_: Exception) { JSONObject() }
            } else {
                JSONObject()
            }

            val affObj = root.optJSONObject("contact_language_affinity") ?: JSONObject()
            val cleanKey = peerId.trim().lowercase()
            val existing = affObj.optJSONObject(cleanKey)

            val currentLang = existing?.optString("lang") ?: langCode
            val currentCount = existing?.optInt("count", 0) ?: 0

            val (newLang, newCount) = if (forceManual) {
                langCode to 20
            } else if (currentLang == langCode) {
                langCode to (currentCount + 1)
            } else {
                if (currentCount <= 1) {
                    langCode to 1
                } else {
                    currentLang to (currentCount - 1)
                }
            }

            val item = JSONObject().apply {
                put("lang", newLang)
                put("count", newCount)
                put("timestamp", System.currentTimeMillis())
            }

            affObj.put(cleanKey, item)
            root.put("contact_language_affinity", affObj)
            storageFile.writeText(root.toString(2))
        } catch (_: Exception) {}
    }
}
