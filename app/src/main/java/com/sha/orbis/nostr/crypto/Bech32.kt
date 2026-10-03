package com.sha.orbis.nostr.crypto

import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Implémentation conforme BIP-173 / Nostr (NIP-19) pour l'encodage et décodage Bech32.
 * Utilisé pour convertir les clés hexadécimales en adresses souveraines 'npub1...' et 'nsec1...'.
 */
object Bech32 {

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val CHARSET_REV = ByteArray(128) { -1 }.apply {
        for (i in CHARSET.indices) {
            this[CHARSET[i].code] = i.toByte()
        }
    }

    fun encode(hrp: String, data: ByteArray): String {
        val fiveBitData = convertBits(data, 8, 5, true)
        val checksum = createChecksum(hrp, fiveBitData)
        val combined = ByteArray(fiveBitData.size + checksum.size)
        System.arraycopy(fiveBitData, 0, combined, 0, fiveBitData.size)
        System.arraycopy(checksum, 0, combined, fiveBitData.size, checksum.size)

        val sb = StringBuilder(hrp.length + 1 + combined.size)
        sb.append(hrp.lowercase(Locale.ROOT))
        sb.append('1')
        for (b in combined) {
            sb.append(CHARSET[b.toInt() and 0x1F])
        }
        return sb.toString()
    }

    fun decode(bech32String: String): Pair<String, ByteArray> {
        val lower = bech32String.lowercase(Locale.ROOT)
        val pos = lower.lastIndexOf('1')
        if (pos < 1 || pos + 7 > lower.length || lower.length > 200) {
            throw IllegalArgumentException("Chaîne Bech32 invalide: $bech32String")
        }

        val hrp = lower.substring(0, pos)
        val dataLen = lower.length - 1 - pos
        val values = ByteArray(dataLen)
        for (i in 0 until dataLen) {
            val c = lower[pos + 1 + i]
            val v = if (c.code < 128) CHARSET_REV[c.code] else -1
            if (v.toInt() == -1) {
                throw IllegalArgumentException("Caractère Bech32 invalide: $c")
            }
            values[i] = v
        }

        if (!verifyChecksum(hrp, values)) {
            throw IllegalArgumentException("Somme de contrôle Bech32 invalide pour: $bech32String")
        }

        val fiveBitData = values.copyOfRange(0, values.size - 6)
        val eightBitData = convertBits(fiveBitData, 5, 8, false)
        return Pair(hrp, eightBitData)
    }

    fun npubEncode(pubkeyHex: String): String {
        return encode("npub", hexToBytes(pubkeyHex))
    }

    fun nsecEncode(privkeyHex: String): String {
        return encode("nsec", hexToBytes(privkeyHex))
    }

    fun decodeToHex(bech32String: String): Pair<String, String> {
        val (hrp, bytes) = decode(bech32String)
        return Pair(hrp, bytesToHex(bytes))
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun polymod(values: ByteArray): Int {
        var chk = 1
        for (b in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1FFFFFF) shl 5) xor (b.toInt() and 0xFF)
            if ((top and 1) != 0) chk = chk xor 0x3b6a57b2
            if ((top and 2) != 0) chk = chk xor 0x26508e6d
            if ((top and 4) != 0) chk = chk xor 0x1ea119fa
            if ((top and 8) != 0) chk = chk xor 0x3d4233dd
            if ((top and 16) != 0) chk = chk xor 0x2a1462b3
        }
        return chk
    }

    private fun hrpExpand(hrp: String): ByteArray {
        val v = ByteArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            v[i] = (hrp[i].code ushr 5).toByte()
            v[i + hrp.length + 1] = (hrp[i].code and 31).toByte()
        }
        v[hrp.length] = 0
        return v
    }

    private fun verifyChecksum(hrp: String, values: ByteArray): Boolean {
        val exp = hrpExpand(hrp)
        val combined = ByteArray(exp.size + values.size)
        System.arraycopy(exp, 0, combined, 0, exp.size)
        System.arraycopy(values, 0, combined, exp.size, values.size)
        return polymod(combined) == 1
    }

    private fun createChecksum(hrp: String, values: ByteArray): ByteArray {
        val exp = hrpExpand(hrp)
        val enc = ByteArray(exp.size + values.size + 6)
        System.arraycopy(exp, 0, enc, 0, exp.size)
        System.arraycopy(values, 0, enc, exp.size, values.size)
        val mod = polymod(enc) xor 1
        val ret = ByteArray(6)
        for (i in 0..5) {
            ret[i] = ((mod ushr (5 * (5 - i))) and 31).toByte()
        }
        return ret
    }

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        var acc = 0
        var bits = 0
        val out = ByteArrayOutputStream()
        val maxv = (1 shl toBits) - 1
        val maxAcc = (1 shl (fromBits + toBits - 1)) - 1
        for (d in data) {
            val value = d.toInt() and 0xFF
            acc = ((acc shl fromBits) or value) and maxAcc
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                out.write((acc ushr bits) and maxv)
            }
        }
        if (pad) {
            if (bits > 0) {
                out.write((acc shl (toBits - bits)) and maxv)
            }
        } else if (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0) {
            throw IllegalArgumentException("Données Bech32 invalides lors de la conversion de bits")
        }
        return out.toByteArray()
    }
}
