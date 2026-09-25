package com.sha.orbis.security

import org.json.JSONObject

data class InvitationPayload(
    val groupId: String,
    val senderPhone: String,
    val senderPublicKey: String,
    val nonce: String,
    val signature: String
) {
    fun toPayloadJson(): JSONObject = JSONObject().apply {
        put("groupId", groupId)
        put("senderPhone", senderPhone)
        put("senderPublicKey", senderPublicKey)
        put("nonce", nonce)
    }

    fun canonicalPayload(): String = toPayloadJson().toString()

    companion object {
        fun fromJson(json: JSONObject): InvitationPayload = InvitationPayload(
            groupId = json.optString("groupId"),
            senderPhone = json.optString("senderPhone"),
            senderPublicKey = json.optString("senderPublicKey"),
            nonce = json.optString("nonce"),
            signature = json.optString("signature")
        )
    }
}
