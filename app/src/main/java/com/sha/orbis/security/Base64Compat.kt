package com.sha.orbis.security

/**
 * Universal Base64 compatibility helper.
 * Uses java.util.Base64 on Android 8.0+ (API 26+) and standard JVM test runners,
 * and android.util.Base64 on older Android devices (API 24-25).
 */
object Base64Compat {

    fun encodeToString(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            try {
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (_: Throwable) {
                ""
            }
        }
    }

    fun decode(str: String): ByteArray {
        val clean = str.trim().replace(" ", "").replace("\n", "").replace("\r", "")
        return try {
            java.util.Base64.getDecoder().decode(clean)
        } catch (_: Throwable) {
            try {
                java.util.Base64.getUrlDecoder().decode(clean)
            } catch (_: Throwable) {
                try {
                    android.util.Base64.decode(clean, android.util.Base64.NO_WRAP)
                } catch (_: Throwable) {
                    android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                }
            }
        }
    }
}
