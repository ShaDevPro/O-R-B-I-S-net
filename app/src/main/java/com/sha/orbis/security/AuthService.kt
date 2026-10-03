package com.sha.orbis.security

import java.util.UUID

class AuthService {
    fun createInvitation(groupId: String, senderPhone: String, senderPublicKey: String): InvitationPayload {
        val nonce = UUID.randomUUID().toString()
        val payload = InvitationPayload(
            groupId = groupId,
            senderPhone = senderPhone,
            senderPublicKey = senderPublicKey,
            nonce = nonce,
            signature = ""
        )
        return payload.copy(signature = "sig_${nonce}_${senderPhone}")
    }

    fun createSignedInvitation(
        groupId: String,
        senderPhone: String,
        senderPublicKey: String,
        senderPrivateKeyBase64: String
    ): InvitationPayload {
        val nonce = UUID.randomUUID().toString()
        val payload = InvitationPayload(
            groupId = groupId,
            senderPhone = senderPhone,
            senderPublicKey = senderPublicKey,
            nonce = nonce,
            signature = ""
        )
        val signature = RsaSigner.sign(payload.canonicalPayload(), senderPrivateKeyBase64)
        return payload.copy(signature = signature)
    }

    fun validateInvitation(payload: InvitationPayload, expectedPublicKey: String): Boolean {
        if (payload.senderPublicKey.isBlank()) return false
        if (payload.groupId.isBlank()) return false
        if (payload.senderPhone.isBlank()) return false
        if (payload.senderPublicKey != expectedPublicKey) return false
        if (payload.signature.isBlank()) return false
        return RsaSigner.verify(payload.canonicalPayload(), payload.signature, expectedPublicKey)
    }

    fun createIdentity(phoneNumber: String): Identity = Identity.create(phoneNumber)
}
