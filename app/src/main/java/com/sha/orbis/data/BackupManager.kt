package com.sha.orbis.data

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "orbis_backup_state"
        private const val LAST_BACKUP_KEY = "last_backup_epoch"
        private const val BACKUP_FILE_PREFIX = "orbis_backup_"
    }

    fun getBackupDirectory(): File {
        val publicDocumentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val preferredDirectory = File(publicDocumentsDirectory, "Orbis/Backups")

        return if (canWriteToPublicDirectory()) {
            preferredDirectory.apply { mkdirs() }
        } else {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Backups").apply { mkdirs() }
        }
    }

    fun createBackup(snapshot: JSONObject): File {
        val directory = getBackupDirectory()
        directory.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val backupFile = File(directory, "$BACKUP_FILE_PREFIX$timestamp.json")

        FileOutputStream(backupFile).use { stream ->
            stream.write(snapshot.toString(2).toByteArray(Charsets.UTF_8))
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putLong(LAST_BACKUP_KEY, System.currentTimeMillis())
        }

        return backupFile
    }

    fun recordBackup(timestamp: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putLong(LAST_BACKUP_KEY, timestamp)
        }
    }

    fun restoreLatestBackup(): JSONObject? {
        val directory = getBackupDirectory()
        if (!directory.exists()) return null

        val latestBackup = directory.listFiles { file ->
            file.isFile && file.name.startsWith(BACKUP_FILE_PREFIX) && file.extension == "json"
        }?.maxByOrNull { it.lastModified() }

        if (latestBackup == null || !latestBackup.exists()) return null

        return JSONObject(latestBackup.readText(Charsets.UTF_8))
    }

    fun getLastBackupEpoch(): Long = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getLong(LAST_BACKUP_KEY, 0L)

    fun shouldAutoBackup(): Boolean {
        val lastBackupEpoch = getLastBackupEpoch()
        if (lastBackupEpoch == 0L) return true
        val twentyFourHoursInMillis = 24L * 60L * 60L * 1000L
        return System.currentTimeMillis() - lastBackupEpoch >= twentyFourHoursInMillis
    }

    private fun canWriteToPublicDirectory(): Boolean {
        val publicDocumentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val preferredDirectory = File(publicDocumentsDirectory, "Orbis/Backups")
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED && preferredDirectory.parentFile?.exists() != false
    }
}
