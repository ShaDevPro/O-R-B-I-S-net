package com.sha.orbis.storage

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.util.Collections
import java.util.LinkedHashSet

/**
 * Gestionnaire persistant de déduplication des événements Nostr.
 * Mémorise sur disque les identifiants d'événements (64 hex) déjà traités
 * afin d'éviter tout rejeu intempestif lors des réinstallations / reconnexions.
 */
class ProcessedEventsRepository(private val context: Context) {

    companion object {
        private const val TAG = "ProcessedEventsRepo"
        private const val MAX_STORED_EVENTS = 50_000
        private const val FILENAME = "orbis_processed_events.json"

        @Volatile
        private var INSTANCE: ProcessedEventsRepository? = null

        fun getInstance(context: Context): ProcessedEventsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProcessedEventsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val storageFile: File by lazy {
        File(context.filesDir, FILENAME)
    }

    private val inMemoryCache: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet<String>())
    private var isLoaded = false

    @Synchronized
    private fun ensureLoaded() {
        if (isLoaded) return
        isLoaded = true
        if (!storageFile.exists()) return

        try {
            val jsonStr = storageFile.readText()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val id = array.optString(i)
                if (!id.isNullOrBlank()) {
                    inMemoryCache.add(id)
                }
            }
            Log.d(TAG, "Chargé ${inMemoryCache.size} identifiants d'événements traités.")
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lecture du cache d'événements traités: ${e.message}")
        }
    }

    @Synchronized
    fun isProcessed(eventId: String?): Boolean {
        if (eventId.isNullOrBlank()) return false
        ensureLoaded()
        return inMemoryCache.contains(eventId)
    }

    @Synchronized
    fun markProcessed(eventId: String?) {
        if (eventId.isNullOrBlank()) return
        ensureLoaded()

        if (inMemoryCache.add(eventId)) {
            // Trim oldest if over capacity
            while (inMemoryCache.size > MAX_STORED_EVENTS) {
                val iterator = inMemoryCache.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                } else {
                    break
                }
            }
            persistToDisk()
        }
    }

    @Synchronized
    fun markProcessedBatch(eventIds: Collection<String>) {
        if (eventIds.isEmpty()) return
        ensureLoaded()

        var modified = false
        for (id in eventIds) {
            if (id.isNotBlank() && inMemoryCache.add(id)) {
                modified = true
            }
        }

        if (modified) {
            while (inMemoryCache.size > MAX_STORED_EVENTS) {
                val iterator = inMemoryCache.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                } else {
                    break
                }
            }
            persistToDisk()
        }
    }

    @Synchronized
    private fun persistToDisk() {
        try {
            val array = JSONArray()
            val snapshot = synchronized(inMemoryCache) { inMemoryCache.toList() }
            for (id in snapshot) {
                array.put(id)
            }
            storageFile.writeText(array.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Erreur sauvegarde des événements traités: ${e.message}")
        }
    }
}
