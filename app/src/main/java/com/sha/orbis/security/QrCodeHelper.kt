package com.sha.orbis.security

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

object QrCodeHelper {

    data class QrContactPayload(
        val name: String,
        val phone: String,
        val publicKey: String,
        val avatarBase64: String? = null
    ) {
        fun encodeToString(): String {
            val json = JSONObject().apply {
                put("app", "orbis")
                put("n", name)
                put("p", phone)
                put("k", publicKey)
                if (!avatarBase64.isNullOrBlank()) {
                    put("a", avatarBase64)
                }
            }
            return json.toString()
        }

        companion object {
            fun decodeFromString(raw: String): QrContactPayload? {
                return try {
                    val json = JSONObject(raw)
                    if (json.optString("app") != "orbis") return null
                    QrContactPayload(
                        name = json.getString("n"),
                        phone = json.getString("p"),
                        publicKey = json.getString("k"),
                        avatarBase64 = json.optString("a").ifBlank { null }
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }
}
