package com.sha.orbis.security

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Base64

data class CryptoKeyPair(
    val publicKey: PublicKey,
    val privateKey: PrivateKey,
    val publicKeyBase64: String,
    val privateKeyBase64: String
) {
    companion object {
        fun generate(): CryptoKeyPair {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            val keyPair: KeyPair = generator.generateKeyPair()

            return CryptoKeyPair(
                publicKey = keyPair.public,
                privateKey = keyPair.private,
                publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded),
                privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
            )
        }
    }
}
