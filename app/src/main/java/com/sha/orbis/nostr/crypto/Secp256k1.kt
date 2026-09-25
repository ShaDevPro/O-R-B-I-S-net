package com.sha.orbis.nostr.crypto

import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Moteur cryptographique Secp256k1 & BIP-340 (Schnorr) pour Nostr.
 * Gère la génération de paires de clés, les signatures Schnorr x-only,
 * la vérification et l'échange de clés Diffie-Hellman (ECDH).
 */
object Secp256k1 {

    private val ecParams: X9ECParameters = CustomNamedCurves.getByName("secp256k1")
    private val curve = ecParams.curve
    private val G: ECPoint = ecParams.g
    private val n: BigInteger = ecParams.n
    private val p: BigInteger = curve.field.characteristic

    private val secureRandom = SecureRandom()

    data class KeyPair(
        val privateKey: ByteArray, // 32 bytes
        val publicKey: ByteArray   // 32 bytes x-only
    ) {
        val privateKeyHex: String get() = Bech32.bytesToHex(privateKey)
        val publicKeyHex: String get() = Bech32.bytesToHex(publicKey)
        val npub: String get() = Bech32.npubEncode(publicKeyHex)
        val nsec: String get() = Bech32.nsecEncode(privateKeyHex)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is KeyPair) return false
            return privateKey.contentEquals(other.privateKey) && publicKey.contentEquals(other.publicKey)
        }

        override fun hashCode(): Int {
            var result = privateKey.contentHashCode()
            result = 31 * result + publicKey.contentHashCode()
            return result
        }
    }

    /**
     * Génère une nouvelle paire de clés souveraine secp256k1 conforme BIP-340.
     */
    fun generateKeyPair(): KeyPair {
        var privBigInt: BigInteger
        do {
            val privBytes = ByteArray(32)
            secureRandom.nextBytes(privBytes)
            privBigInt = BigInteger(1, privBytes)
        } while (privBigInt <= BigInteger.ZERO || privBigInt >= n)

        val point = G.multiply(privBigInt).normalize()
        val normalizedPriv = if (point.affineYCoord.toBigInteger().testBit(0)) {
            n.subtract(privBigInt)
        } else {
            privBigInt
        }

        val privBytes = to32ByteArray(normalizedPriv)
        val pubPoint = G.multiply(normalizedPriv).normalize()
        val pubBytes = to32ByteArray(pubPoint.affineXCoord.toBigInteger())

        return KeyPair(privBytes, pubBytes)
    }

    /**
     * Dérive la clé publique x-only (32 octets) à partir d'une clé privée hexadécimale.
     */
    fun getPublicKey(privateKeyHex: String): String {
        val privBigInt = BigInteger(1, Bech32.hexToBytes(privateKeyHex))
        val point = G.multiply(privBigInt).normalize()
        val normalizedPriv = if (point.affineYCoord.toBigInteger().testBit(0)) {
            n.subtract(privBigInt)
        } else {
            privBigInt
        }
        val pubPoint = G.multiply(normalizedPriv).normalize()
        return Bech32.bytesToHex(to32ByteArray(pubPoint.affineXCoord.toBigInteger()))
    }

    /**
     * Signature Schnorr BIP-340 d'un hash de message de 32 octets.
     * Retourne une signature de 64 octets sous forme de chaîne hexadécimale.
     */
    fun sign(messageHash32: ByteArray, privateKey32: ByteArray): String {
        var d = BigInteger(1, privateKey32)
        require(d > BigInteger.ZERO && d < n) { "Clé privée invalide" }

        val P = G.multiply(d).normalize()
        if (P.affineYCoord.toBigInteger().testBit(0)) {
            d = n.subtract(d)
        }

        val px = to32ByteArray(P.affineXCoord.toBigInteger())

        val aux = ByteArray(32)
        secureRandom.nextBytes(aux)

        val t = xorBytes(to32ByteArray(d), taggedHash("BIP0340/aux", aux))
        val nonceInput = t + px + messageHash32
        val k0 = BigInteger(1, taggedHash("BIP0340/nonce", nonceInput)).mod(n)
        require(k0 != BigInteger.ZERO) { "Échec génération nonce" }

        val R = G.multiply(k0).normalize()
        val k = if (R.affineYCoord.toBigInteger().testBit(0)) {
            n.subtract(k0)
        } else {
            k0
        }

        val rx = to32ByteArray(R.affineXCoord.toBigInteger())
        val challengeInput = rx + px + messageHash32
        val e = BigInteger(1, taggedHash("BIP0340/challenge", challengeInput)).mod(n)

        val s = k.add(e.multiply(d)).mod(n)
        val sigBytes = rx + to32ByteArray(s)

        return Bech32.bytesToHex(sigBytes)
    }

    /**
     * Vérifie une signature Schnorr BIP-340 de 64 octets.
     */
    fun verify(messageHash32: ByteArray, publicKey32: ByteArray, signature64: ByteArray): Boolean {
        if (publicKey32.size != 32 || signature64.size != 64) return false

        val px = BigInteger(1, publicKey32)
        if (px >= p) return false

        val rx = BigInteger(1, signature64.copyOfRange(0, 32))
        val s = BigInteger(1, signature64.copyOfRange(32, 64))
        if (rx >= p || s >= n) return false

        val P = liftX(px) ?: return false

        val challengeInput = to32ByteArray(rx) + to32ByteArray(px) + messageHash32
        val e = BigInteger(1, taggedHash("BIP0340/challenge", challengeInput)).mod(n)

        val minusE = n.subtract(e)
        val R = org.bouncycastle.math.ec.ECAlgorithms.sumOfTwoMultiplies(G, s, P, minusE).normalize()

        if (R.isInfinity || R.affineYCoord.toBigInteger().testBit(0)) return false
        return R.affineXCoord.toBigInteger() == rx
    }

    /**
     * Dérive un secret partagé ECDH (32 octets) pour le chiffrement NIP-04 / NIP-44.
     */
    fun computeSharedSecret(myPrivateKey32: ByteArray, theirPublicKey32: ByteArray): ByteArray {
        val privBigInt = BigInteger(1, myPrivateKey32)
        val theirPoint = liftX(BigInteger(1, theirPublicKey32))
            ?: throw IllegalArgumentException("Clé publique distante invalide pour ECDH")
        val sharedPoint = theirPoint.multiply(privBigInt).normalize()
        val sharedX = to32ByteArray(sharedPoint.affineXCoord.toBigInteger())
        return sha256(sharedX)
    }

    fun sha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }

    private fun taggedHash(tag: String, msg: ByteArray): ByteArray {
        val tagHash = sha256(tag.toByteArray(Charsets.UTF_8))
        val combined = tagHash + tagHash + msg
        return sha256(combined)
    }

    private fun liftX(x: BigInteger): ECPoint? {
        if (x >= p) return null
        val ySq = x.modPow(BigInteger.valueOf(3), p).add(BigInteger.valueOf(7)).mod(p)
        val y = ySq.modPow(p.add(BigInteger.ONE).divide(BigInteger.valueOf(4)), p)
        if (y.modPow(BigInteger.valueOf(2), p) != ySq) return null
        val finalY = if (y.testBit(0)) p.subtract(y) else y
        return curve.createPoint(x, finalY)
    }

    private fun to32ByteArray(num: BigInteger): ByteArray {
        val bytes = num.toByteArray()
        if (bytes.size == 32) return bytes
        val result = ByteArray(32)
        if (bytes.size > 32) {
            System.arraycopy(bytes, bytes.size - 32, result, 0, 32)
        } else {
            System.arraycopy(bytes, 0, result, 32 - bytes.size, bytes.size)
        }
        return result
    }

    private fun xorBytes(a: ByteArray, b: ByteArray): ByteArray {
        val out = ByteArray(minOf(a.size, b.size))
        for (i in out.indices) {
            out[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        }
        return out
    }
}
