package com.sha.orbis.security

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object RsaSigner {
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"

    fun sign(data: String, privateKeyBase64: String): String {
        val privateKey = privateKeyFromBase64(privateKeyBase64)
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey)
        signature.update(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    fun verify(data: String, signatureBase64: String, publicKeyBase64: String): Boolean {
        val publicKey = publicKeyFromBase64(publicKeyBase64)
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initVerify(publicKey)
        signature.update(data.toByteArray(Charsets.UTF_8))
        return signature.verify(Base64.getDecoder().decode(signatureBase64))
    }

    fun publicKeyFromBase64(base64: String): PublicKey {
        val key = Base64.getDecoder().decode(base64)
        val spec = X509EncodedKeySpec(key)
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePublic(spec)
    }

    fun privateKeyFromBase64(base64: String): PrivateKey {
        val key = Base64.getDecoder().decode(base64)
        val spec = PKCS8EncodedKeySpec(key)
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePrivate(spec)
    }
}
