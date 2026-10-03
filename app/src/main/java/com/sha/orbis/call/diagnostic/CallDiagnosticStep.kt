package com.sha.orbis.call.diagnostic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.sha.orbis.R

/**
 * Identifiants uniques pour chaque étape du pipeline de diagnostic des appels.
 */
enum class DiagnosticStepId(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val isVideoSpecific: Boolean = false
) {
    PERMISSIONS(
        titleRes = R.string.call_diag_step_permissions_title,
        descRes = R.string.call_diag_step_permissions_desc,
        icon = Icons.Default.Security
    ),
    MICROPHONE(
        titleRes = R.string.call_diag_step_mic_title,
        descRes = R.string.call_diag_step_mic_desc,
        icon = Icons.Default.Mic
    ),
    AUDIO_ROUTING(
        titleRes = R.string.call_diag_step_audio_routing_title,
        descRes = R.string.call_diag_step_audio_routing_desc,
        icon = Icons.AutoMirrored.Filled.VolumeUp
    ),
    CAMERA(
        titleRes = R.string.call_diag_step_camera_title,
        descRes = R.string.call_diag_step_camera_desc,
        icon = Icons.Default.CameraAlt,
        isVideoSpecific = true
    ),
    WEBRTC_CODECS(
        titleRes = R.string.call_diag_step_webrtc_title,
        descRes = R.string.call_diag_step_webrtc_desc,
        icon = Icons.Default.VideoCameraFront
    ),
    NETWORK_ICE(
        titleRes = R.string.call_diag_step_network_title,
        descRes = R.string.call_diag_step_network_desc,
        icon = Icons.Default.NetworkCheck
    ),
    TELECOM(
        titleRes = R.string.call_diag_step_telecom_title,
        descRes = R.string.call_diag_step_telecom_desc,
        icon = Icons.Default.Phone
    ),
    BACKGROUND_DOZE(
        titleRes = R.string.call_diag_step_background_title,
        descRes = R.string.call_diag_step_background_desc,
        icon = Icons.Default.BatteryChargingFull
    )
}

/**
 * États possibles d'une étape de diagnostic.
 */
enum class DiagnosticStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    WARNING,
    FAILED,
    REPAIRED
}

/**
 * Résultat d'une étape de diagnostic.
 */
data class DiagnosticStepResult(
    val stepId: DiagnosticStepId,
    val status: DiagnosticStatus = DiagnosticStatus.IDLE,
    val detail: String = "",
    val canAutoRepair: Boolean = false,
    val repairActionLabelRes: Int? = null,
    val isInteractiveTestAvailable: Boolean = false
)

/**
 * État global de la session de diagnostic.
 */
data class CallDiagnosticState(
    val isRunning: Boolean = false,
    val isRepairing: Boolean = false,
    val currentRunningStep: DiagnosticStepId? = null,
    val stepResults: Map<DiagnosticStepId, DiagnosticStepResult> = emptyMap(),
    val overallStatus: DiagnosticStatus = DiagnosticStatus.IDLE,
    val totalSteps: Int = DiagnosticStepId.entries.size,
    val completedStepsCount: Int = 0,
    val isAudioTestPlaying: Boolean = false,
    val lastRunTimestamp: Long = 0L
) {
    val passedCount: Int
        get() = stepResults.values.count { it.status == DiagnosticStatus.SUCCESS }

    val repairedCount: Int
        get() = stepResults.values.count { it.status == DiagnosticStatus.REPAIRED }

    val warningCount: Int
        get() = stepResults.values.count { it.status == DiagnosticStatus.WARNING }

    val failedCount: Int
        get() = stepResults.values.count { it.status == DiagnosticStatus.FAILED }

    val hasIssues: Boolean
        get() = failedCount > 0 || warningCount > 0

    val isAllOk: Boolean
        get() = stepResults.isNotEmpty() && failedCount == 0 && warningCount == 0
}
