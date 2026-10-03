package com.sha.orbis.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BinarySmsCompressorTest {

    @Test
    fun compression_and_decompression_round_trip() {
        val largeMessage = "Orbis Sovereign Network - Message Décentralisé " + "🔒 GSM P2P ".repeat(40)

        val compressedBytes = BinarySmsCompressor.compress(largeMessage.toByteArray(Charsets.UTF_8))
        assertTrue(compressedBytes.size < largeMessage.toByteArray(Charsets.UTF_8).size)

        val decompressedBytes = BinarySmsCompressor.decompress(compressedBytes)
        val result = String(decompressedBytes, Charsets.UTF_8)

        assertEquals(largeMessage, result)
    }

    @Test
    fun base64_compression_round_trip() {
        val jsonPayload = """{"type":"social_post","id":"post_999","content":"Test de compression haute efficacité pour le transport GSM"}"""

        val compressedBase64 = BinarySmsCompressor.compressToBase64(jsonPayload)
        val decompressed = BinarySmsCompressor.decompressFromBase64(compressedBase64)

        assertEquals(jsonPayload, decompressed)
    }
}
