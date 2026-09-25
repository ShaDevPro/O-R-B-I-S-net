package com.sha.orbis.data

import android.content.Context
import com.sha.orbis.model.OrbisState
import org.json.JSONObject

class OrbisRepository(
    private val context: Context,
    private val localDataStore: LocalDataStore = LocalDataStore(context),
    private val backupManager: BackupManager = BackupManager(context)
) {

    fun saveState(state: OrbisState) {
        localDataStore.writeState(state.toJson())
        if (backupManager.shouldAutoBackup()) {
            backupManager.createBackup(state.toJson())
        }
    }

    fun loadState(): OrbisState? {
        val stateJson = localDataStore.readState() ?: backupManager.restoreLatestBackup() ?: return null
        return OrbisState.fromJson(stateJson)
    }

    fun saveSnapshotToBackup(snapshot: JSONObject) {
        backupManager.createBackup(snapshot)
    }

    fun getBackupDirectoryPath(): String = backupManager.getBackupDirectory().absolutePath

    fun lastBackupTimestamp(): Long = backupManager.getLastBackupEpoch()
}
