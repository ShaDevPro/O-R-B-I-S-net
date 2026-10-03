package com.sha.orbis.security

data class Identity(
    val phoneNumber: String,
    val publicKeyBase64: String,
    val privateKeyBase64: String,
    val keyPair: CryptoKeyPair
) {
    companion object {
        fun create(phoneNumber: String): Identity {
            val pair = CryptoKeyPair.generate()
            return Identity(
                phoneNumber = phoneNumber,
                publicKeyBase64 = pair.publicKeyBase64,
                privateKeyBase64 = pair.privateKeyBase64,
                keyPair = pair
            )
        }
    }
}
