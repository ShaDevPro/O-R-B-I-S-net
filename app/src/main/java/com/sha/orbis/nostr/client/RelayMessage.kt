package com.sha.orbis.nostr.client

import com.sha.orbis.nostr.model.NostrEvent
import com.sha.orbis.nostr.model.NostrFilter
import org.json.JSONArray

/**
 * Messages du protocole Nostr NIP-01 échangés sur WebSockets.
 */
sealed class RelayMessage {

    // === Messages reçus du Relais ===
    data class EventMsg(val subscriptionId: String, val event: NostrEvent) : RelayMessage()
    data class OkMsg(val eventId: String, val accepted: Boolean, val message: String) : RelayMessage()
    data class EoseMsg(val subscriptionId: String) : RelayMessage()
    data class ClosedMsg(val subscriptionId: String, val message: String) : RelayMessage()
    data class NoticeMsg(val message: String) : RelayMessage()
    data class UnknownMsg(val raw: String) : RelayMessage()

    companion object {

        // === Génération des messages Client -> Relais ===

        fun sendEvent(event: NostrEvent): String {
            return "[\"EVENT\",${event.toCanonicalJson()}]"
        }

        fun sendReq(subscriptionId: String, filters: List<NostrFilter>): String {
            val arr = JSONArray()
            arr.put("REQ")
            arr.put(subscriptionId)
            for (filter in filters) {
                arr.put(filter.toJson())
            }
            return arr.toString()
        }

        fun sendClose(subscriptionId: String): String {
            val arr = JSONArray()
            arr.put("CLOSE")
            arr.put(subscriptionId)
            return arr.toString()
        }

        /**
         * Parse une trame WebSocket entrante depuis un relais.
         */
        fun parse(text: String): RelayMessage {
            return try {
                val arr = JSONArray(text)
                when (val type = arr.getString(0)) {
                    "EVENT" -> {
                        val subId = arr.getString(1)
                        val eventObj = arr.getJSONObject(2)
                        val event = NostrEvent.fromJson(eventObj.toString())
                        EventMsg(subId, event)
                    }
                    "OK" -> {
                        val eventId = arr.getString(1)
                        val accepted = arr.getBoolean(2)
                        val msg = if (arr.length() > 3) arr.getString(3) else ""
                        OkMsg(eventId, accepted, msg)
                    }
                    "EOSE" -> {
                        val subId = arr.getString(1)
                        EoseMsg(subId)
                    }
                    "CLOSED" -> {
                        val subId = arr.getString(1)
                        val msg = if (arr.length() > 2) arr.getString(2) else ""
                        ClosedMsg(subId, msg)
                    }
                    "NOTICE" -> {
                        val msg = arr.getString(1)
                        NoticeMsg(msg)
                    }
                    else -> UnknownMsg(text)
                }
            } catch (e: Exception) {
                UnknownMsg(text)
            }
        }
    }
}
