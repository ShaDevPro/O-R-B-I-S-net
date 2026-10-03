package com.sha.orbis.backup

import android.content.Context
import android.os.Environment
import com.sha.orbis.security.AesCipher
import com.sha.orbis.security.Base64Compat
import java.io.File

class SecureBackupManager(private val context: Context) {
    private val backupRoot = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "Orbis/Backups"
    )

    fun ensureDirectory(): File {
        if (!backupRoot.exists()) {
            backupRoot.mkdirs()
        }
        return backupRoot
    }

    fun createSecureBackup(payload: String, passphrase: String): File? {
        return try {
            val directory = ensureDirectory()
            val secretKey = AesCipher.stringToKey(passphrase)
            val encrypted = AesCipher.encrypt(payload, secretKey)
            val protectedPayload = Base64Compat.encodeToString(encrypted.payload.toByteArray(Charsets.UTF_8)) +
                ":" + encrypted.iv + ":" + Base64Compat.encodeToString(passphrase.take(4).toByteArray(Charsets.UTF_8))

            val file = File(directory, "orbis_secure_backup_${System.currentTimeMillis()}.txt")
            file.writeText(protectedPayload, Charsets.UTF_8)
            file
        } catch (_: Exception) {
            null
        }
    }

    fun restoreSecureBackup(file: File, passphrase: String): String {
        return try {
            if (!file.exists()) return ""
            val content = file.readText(Charsets.UTF_8)
            val parts = content.split(":")
            if (parts.size < 2) return ""

            val payload = String(Base64Compat.decode(parts[0]), Charsets.UTF_8)
            val iv = parts[1]
            val key = AesCipher.stringToKey(passphrase)
            AesCipher.decrypt(AesCipher.EncryptedPayload(payload = payload, iv = iv), key)
        } catch (_: Exception) {
            ""
        }
    }
}
