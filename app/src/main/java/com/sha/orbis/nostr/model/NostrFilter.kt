package com.sha.orbis.nostr.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Filtre de souscription Nostr standard (NIP-01) pour les requêtes REQ.
 */
data class NostrFilter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val eTags: List<String>? = null, // Tag #e (IDs d'événements référencés)
    val pTags: List<String>? = null, // Tag #p (Clés publiques référencées)
    val tTags: List<String>? = null, // Tag #t (Hashtags thématiques ex: 'orbisnet')
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        ids?.let { list ->
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            json.put("ids", arr)
        }
        authors?.let { list ->
            val arr = JSONArray()
            list.forEach { arr.put(it.lowercase()) }
            json.put("authors", arr)
        }
        kinds?.let { list ->
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            json.put("kinds", arr)
        }
        eTags?.let { list ->
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            json.put("#e", arr)
        }
        pTags?.let { list ->
            val arr = JSONArray()
            list.forEach { arr.put(it.lowercase()) }
            json.put("#p", arr)
        }
        tTags?.let { list ->
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            json.put("#t", arr)
        }
        since?.let { json.put("since", it) }
        until?.let { json.put("until", it) }
        limit?.let { json.put("limit", it) }
        return json
    }
}
