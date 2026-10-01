package com.sha.orbis.sync.protocol

import android.util.Log
import com.sha.orbis.sync.model.SovereignSyncPacket
import org.json.JSONObject

/**
 * Protocole de transport souverain chiffré de bout en bout (NIP-04 AES-GCM).
 * Encode et décode les paquets d'échange inter-contacts OrbisNet.
 */
object SovereignSyncProtocol {

    private const val TAG = "SovereignSyncProtocol"
    const val SYNC_HEADER = "[ORBIS_PEER_SYNC_V1]"

    fun isSyncPacket(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        return trimmed.startsWith(SYNC_HEADER) ||
            trimmed.contains(SYNC_HEADER) ||
            trimmed.contains("\"action\":\"EXCHANGE_REQUEST\"") ||
            trimmed.contains("\"action\":\"EXCHANGE_RESPONSE\"") ||
            trimmed.contains("\"action\":\"DELTA_EXCHANGE\"") ||
            trimmed.contains("\"action\":\"HEARTBEAT\"") ||
            (trimmed.startsWith("{") && trimmed.contains("\"bundle\":{"))
    }

    fun pack(packet: SovereignSyncPacket): String {
        return "$SYNC_HEADER${packet.toJson()}"
    }

    fun unpack(text: String): SovereignSyncPacket? {
        if (!isSyncPacket(text)) return null
        return try {
            val trimmed = text.trim()
            val jsonStr = if (trimmed.contains(SYNC_HEADER)) {
                val idx = trimmed.indexOf(SYNC_HEADER)
                trimmed.substring(idx + SYNC_HEADER.length).trim()
            } else {
                trimmed
            }
            val obj = JSONObject(jsonStr)
            SovereignSyncPacket.fromJson(obj)
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de décoder le paquet de synchro souverain: ${e.message}")
            null
        }
    }
}
