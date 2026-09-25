package com.sha.orbis.call

enum class CallStatus {
    IDLE,
    OUTGOING_CALLING,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTING,
    CONNECTED,
    ENDED
}

data class CallSession(
    val callId: String,
    val peerPhone: String,
    val peerName: String,
    val peerAvatar: String?,
    val status: CallStatus,
    val startTime: Long = 0L,
    val durationSeconds: Int = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val safetyNumber: String = "7842",
    val peerIp: String? = null,
    val peerPort: Int = OrbisAudioStreamer.AUDIO_PORT,
    val peerLocalIp: String? = null,
    val peerLocalPort: Int = OrbisAudioStreamer.AUDIO_PORT,
    val isGsmFallback: Boolean = false, // Obsolete legacy flag (100% WebRTC/Nostr)
    val hasSmsCredit: Boolean = true,   // Obsolete legacy flag (100% WebRTC/Nostr)
    val peerNostrKey: String? = null,
    val isVideoCall: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    // STUN-discovered video endpoints for NAT traversal (legacy UDP fallback)
    val peerVideoIp: String? = null,
    val peerVideoPort: Int = OrbisVideoStreamer.VIDEO_PORT,
    val peerVideoLocalPort: Int = OrbisVideoStreamer.VIDEO_PORT,
    // WebRTC SDP offer received — stored until acceptCall() uses it
    val peerSdpOffer: String? = null
)

enum class CallFeedbackReason {
    NO_ANSWER,
    REJECTED_BY_PEER,
    CALL_FAILED,
    @Deprecated("Legacy alias for NO_ANSWER")
    NO_ANSWER_OR_NO_CREDIT,
    @Deprecated("Legacy alias for CALL_FAILED")
    CALLER_NO_CREDIT
}

data class CallFeedbackInfo(
    val peerPhone: String,
    val peerName: String,
    val reason: CallFeedbackReason
)
