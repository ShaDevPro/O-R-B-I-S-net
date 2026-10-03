package com.sha.orbis.sms

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object BinarySmsCompressor {

    fun compress(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(input)
        deflater.finish()

        val outputStream = ByteArrayOutputStream(input.size)
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        deflater.end()
        return outputStream.toByteArray()
    }

    fun decompress(input: ByteArray): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(input)

        val outputStream = ByteArrayOutputStream(input.size * 2)
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && inflater.needsInput()) break
            outputStream.write(buffer, 0, count)
        }
        inflater.end()
        return outputStream.toByteArray()
    }

    fun compressToBase64(text: String): String {
        val compressed = compress(text.toByteArray(Charsets.UTF_8))
        return com.sha.orbis.security.Base64Compat.encodeToString(compressed)
    }

    fun decompressFromBase64(base64: String): String {
        val bytes = com.sha.orbis.security.Base64Compat.decode(base64)
        val decompressed = decompress(bytes)
        return String(decompressed, Charsets.UTF_8)
    }
}
