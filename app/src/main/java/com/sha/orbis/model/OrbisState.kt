package com.sha.orbis.model

import org.json.JSONArray
import org.json.JSONObject

data class OrbisState(
    val contacts: List<Contact>,
    val conversations: List<Conversation>,
    val messagesByConversation: Map<String, List<Message>>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("contacts", JSONArray().apply {
            contacts.forEach { put(it.toJson()) }
        })
        put("conversations", JSONArray().apply {
            conversations.forEach { put(it.toJson()) }
        })
        val messagesObject = JSONObject()
        messagesByConversation.forEach { (conversationId, messages) ->
            messagesObject.put(conversationId, JSONArray().apply {
                messages.forEach { put(it.toJson()) }
            })
        }
        put("messagesByConversation", messagesObject)
    }

    companion object {
        fun fromJson(json: JSONObject): OrbisState {
            val contacts = json.optJSONArray("contacts")?.let { array ->
                List(array.length()) { index -> Contact.fromJson(array.getJSONObject(index)) }
            } ?: emptyList()

            val conversations = json.optJSONArray("conversations")?.let { array ->
                List(array.length()) { index -> Conversation.fromJson(array.getJSONObject(index)) }
            } ?: emptyList()

            val messagesMap = mutableMapOf<String, List<Message>>()
            val messagesObject = json.optJSONObject("messagesByConversation")
            if (messagesObject != null) {
                val keys = messagesObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val array = messagesObject.optJSONArray(key) ?: JSONArray()
                    val messages = List(array.length()) { index -> Message.fromJson(array.getJSONObject(index)) }
                    messagesMap[key] = messages
                }
            }

            return OrbisState(
                contacts = contacts,
                conversations = conversations,
                messagesByConversation = messagesMap
            )
        }
    }
}
