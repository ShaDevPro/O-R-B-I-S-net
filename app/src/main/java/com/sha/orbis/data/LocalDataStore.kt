package com.sha.orbis.data

import android.content.Context
import org.json.JSONObject
import java.io.File

class LocalDataStore(private val context: Context) {

    private val stateFile: File = File(context.filesDir, "orbis_state.json")

    fun readState(): JSONObject? {
        if (!stateFile.exists()) return null
        return JSONObject(stateFile.readText(Charsets.UTF_8))
    }

    fun writeState(snapshot: JSONObject) {
        stateFile.writeText(snapshot.toString(2), Charsets.UTF_8)
    }

    fun clear() {
        if (stateFile.exists()) {
            stateFile.delete()
        }
    }
}
