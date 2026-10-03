package com.sha.orbis.model

/**
 * CallGroup — Regroupe les appels consecutifs vers la meme personne.
 *
 * Regle de groupement (style iOS Phone / Signal) :
 *   - Appels tries par timestamp decroissant (plus recent en premier).
 *   - Nouveau groupe a chaque changement de peerPhone dans la liste triee.
 *   - Ex : A, A, B, A -> 3 groupes : [A,A], [B], [A]
 */
data class CallGroup(
    val peerPhone: String,
    val peerName: String,
    val peerAvatar: String?,

    /** Appels du groupe, tries du plus recent au plus ancien */
    val calls: List<CallRecord>,

    val missedCount: Int,
    val hasVideo: Boolean,
    val hasVoice: Boolean,
    val lastTimestamp: Long
) {
    val hasMissed: Boolean get() = missedCount > 0
    val lastDirection: CallDirection get() = calls.first().direction
    val lastIsVideo: Boolean get() = calls.first().isVideo
    val totalDurationSeconds: Int get() = calls.sumOf { it.durationSeconds }
    val totalCallsCount: Int get() = calls.size
    val latestCall: CallRecord get() = calls.first()
}
