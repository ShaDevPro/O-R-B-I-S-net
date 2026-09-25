package com.sha.orbis.call.telecom

import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.PhoneAccountHandle
import android.telecom.ConnectionService
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * OrbisConnectionService — Service système Android Telecom pour OrbisNet.
 *
 * Déclaré avec permission BIND_TELECOM_CONNECTION_SERVICE.
 * Permet à Android de gérer le cycle de vie Telecom de l'appel VoIP.
 */
@RequiresApi(Build.VERSION_CODES.O)
class OrbisConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "OrbisConnectionService"
        
        // Connexion active pour synchronisation bidirectionnelle
        private var activeConnection: OrbisConnection? = null

        fun getActiveConnection(): OrbisConnection? = activeConnection

        fun clearActiveConnection(connection: OrbisConnection) {
            if (activeConnection == connection) {
                activeConnection = null
            }
        }
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        Log.i(TAG, "onCreateOutgoingConnection: request=$request")
        if (request == null) return null

        // Terminer toute connexion précédente encore vivante
        activeConnection?.let {
            it.notifyTerminated(android.telecom.DisconnectCause.LOCAL)
            activeConnection = null
        }

        val effectiveExtras = request.extras?.getBundle(android.telecom.TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)
            ?: request.extras

        val callId = effectiveExtras?.getString("com.sha.orbis.CALL_ID")
            ?: request.extras?.getString("com.sha.orbis.CALL_ID")
            ?: "out_${System.currentTimeMillis()}"
        val peerPhone = effectiveExtras?.getString("com.sha.orbis.PEER_PHONE")
            ?: request.extras?.getString("com.sha.orbis.PEER_PHONE") ?: ""
        val peerName = effectiveExtras?.getString("com.sha.orbis.PEER_NAME")
            ?: request.extras?.getString("com.sha.orbis.PEER_NAME") ?: peerPhone
        val isVideo = effectiveExtras?.getBoolean("com.sha.orbis.IS_VIDEO", false)
            ?: request.extras?.getBoolean("com.sha.orbis.IS_VIDEO", false) ?: false

        val connection = OrbisConnection(
            callId = callId,
            peerPhone = peerPhone,
            peerName = peerName,
            isVideo = isVideo,
            isIncoming = false
        )

        // Si l'appel a déjà été annulé/raccroché par l'utilisateur avant la fin de l'init Telecom
        val currentCall = com.sha.orbis.call.OrbisCallManager.callState.value
        if (currentCall == null || currentCall.status == com.sha.orbis.call.CallStatus.ENDED) {
            Log.w(TAG, "onCreateOutgoingConnection: appel déjà terminé localement, déconnexion immédiate Telecom")
            connection.notifyTerminated(android.telecom.DisconnectCause.LOCAL)
            return connection
        }

        connection.setDialing()
        activeConnection = connection
        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        Log.i(TAG, "onCreateIncomingConnection: request=$request")
        if (request == null) return null

        // Terminer toute connexion précédente encore vivante
        activeConnection?.let {
            it.notifyTerminated(android.telecom.DisconnectCause.LOCAL)
            activeConnection = null
        }

        val effectiveExtras = request.extras?.getBundle(android.telecom.TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)
            ?: request.extras

        val callId = effectiveExtras?.getString("com.sha.orbis.CALL_ID")
            ?: request.extras?.getString("com.sha.orbis.CALL_ID")
            ?: "in_${System.currentTimeMillis()}"
        val peerPhone = effectiveExtras?.getString("com.sha.orbis.PEER_PHONE")
            ?: request.extras?.getString("com.sha.orbis.PEER_PHONE") ?: ""
        val peerName = effectiveExtras?.getString("com.sha.orbis.PEER_NAME")
            ?: request.extras?.getString("com.sha.orbis.PEER_NAME") ?: peerPhone
        val isVideo = effectiveExtras?.getBoolean("com.sha.orbis.IS_VIDEO", false)
            ?: request.extras?.getBoolean("com.sha.orbis.IS_VIDEO", false) ?: false

        val connection = OrbisConnection(
            callId = callId,
            peerPhone = peerPhone,
            peerName = peerName,
            isVideo = isVideo,
            isIncoming = true
        )

        // Si l'appel n'est plus actif dans OrbisCallManager
        val currentCall = com.sha.orbis.call.OrbisCallManager.callState.value
        if (currentCall == null || currentCall.status == com.sha.orbis.call.CallStatus.ENDED) {
            Log.w(TAG, "onCreateIncomingConnection: appel déjà terminé localement, déconnexion immédiate Telecom")
            connection.notifyTerminated(android.telecom.DisconnectCause.CANCELED)
            return connection
        }

        connection.setRinging()
        activeConnection = connection
        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "onCreateOutgoingConnectionFailed: request=$request")
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        try { activeConnection?.notifyTerminated(android.telecom.DisconnectCause.ERROR) } catch (_: Throwable) {}
        activeConnection = null
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "onCreateIncomingConnectionFailed: request=$request")
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        try { activeConnection?.notifyTerminated(android.telecom.DisconnectCause.ERROR) } catch (_: Throwable) {}
        activeConnection = null
    }

    override fun onDestroy() {
        super.onDestroy()
        activeConnection = null
    }
}
