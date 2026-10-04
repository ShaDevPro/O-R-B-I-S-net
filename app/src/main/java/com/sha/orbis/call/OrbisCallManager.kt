package com.sha.orbis.call

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OrbisCallManager — Proprietary P2P call orchestrator for OrbisNet.
 *
 * Manages the full lifecycle of encrypted peer-to-peer audio/video calls over the
 * sovereign Nostr/WebRTC stack: outgoing calls, incoming call reception, ICE candidate
 * buffering, SAS safety number computation, and call state machine.
 *
 * ⚠️ STUB — Core implementation is proprietary and not published.
 * See: https://github.com/ShaDevPro/O-R-B-I-S-net
 *
 * @license Proprietary — All rights reserved. © ShaDevPro
 */
object OrbisCallManager {

    private val _callState = MutableStateFlow<CallSession?>(null)
    val callState: StateFlow<CallSession?> = _callState.asStateFlow()

    private val _callFeedback = MutableStateFlow<CallFeedbackInfo?>(null)
    val callFeedback: StateFlow<CallFeedbackInfo?> = _callFeedback.asStateFlow()

    fun init(context: Context) {
        // stub
    }

    fun markCallHandled(callId: String) {
        // stub
    }

    fun isCallAlreadyHandled(callId: String): Boolean = false

    fun clearCallFeedback() {
        // stub
    }

    fun startOutgoingCall(
        context: Context,
        peerPhone: String,
        peerName: String,
        peerAvatar: String?,
        myPhone: String,
        peerNostrKey: String? = null,
        isVideoCall: Boolean = false
    ) {
        // stub
    }

    fun onCallConnectedViaUdp(isGsmFallback: Boolean = false) {
        // stub
    }

    fun onPeerHandshakeModeChanged(isGsmFallback: Boolean) {
        // stub
    }

    fun onIncomingCallReceived(
        context: Context,
        callId: String,
        callerPhone: String,
        callerName: String,
        safetyNumber: String,
        senderAddress: String = "",
        peerIp: String = "",
        peerPort: Int = 0,
        peerLocalIp: String = "",
        peerLocalPort: Int = 0,
        peerNostrKey: String? = null,
        isVideo: Boolean = false,
        peerVideoIp: String = "",
        peerVideoPort: Int = 0,
        peerVideoLocalPort: Int = 0,
        peerSdpOffer: String? = null
    ) {
        // stub
    }

    fun acceptCall(withVideo: Boolean = true) {
        // stub
    }

    fun rejectCall() {
        // stub
    }

    fun rejectCallWithQuickReply(context: Context, replyText: String) {
        // stub
    }

    fun leaveVoiceMemoFallback() {
        // stub
    }

    fun onCallAnswerReceived(
        callId: String,
        action: String,
        ip: String = "",
        port: Int = 0,
        localIp: String = "",
        localPort: Int = 0,
        isVideoAccepted: Boolean = true,
        peerVideoIp: String = "",
        peerVideoPort: Int = 0,
        peerVideoLocalPort: Int = 0,
        peerSdpAnswer: String? = null
    ) {
        // stub
    }

    fun onCallEndedRemote(callId: String = "") {
        // stub
    }

    fun endCall(sendSignal: Boolean = true) {
        // stub
    }

    fun toggleMute() {
        // stub
    }

    fun toggleSpeaker() {
        // stub
    }

    fun toggleCamera(enabled: Boolean? = null) {
        // stub
    }

    fun switchCamera() {
        // stub
    }

    fun onIceCandidateReceived(
        candidateSdp: String,
        sdpMid: String,
        sdpMLineIndex: Int,
        callId: String
    ) {
        // stub
    }

    fun computeSasCode(peerPhone: String, myPhone: String): String = "0000"

    fun isBluetoothAudioConnected(context: Context): Boolean = false

    fun isDoNotDisturbActive(context: Context): Boolean = false
}
