package com.sha.orbis.call.diagnostic

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.sha.orbis.permissions.PermissionGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * CallDiagnosticEngine — Moteur central d'orchestration pour le diagnostic
 * et l'auto-réparation séquentielle des appels audio et vidéo dans OrbisNet.
 */
object CallDiagnosticEngine {

    private const val TAG = "CallDiagnosticEngine"
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var diagnosticJob: Job? = null

    private val _state = MutableStateFlow(CallDiagnosticState())
    val state: StateFlow<CallDiagnosticState> = _state.asStateFlow()

    /**
     * Lance le diagnostic complet des 8 étapes avec tentative d'auto-réparation immédiate
     * dès qu'une étape échoue ou présente un risque d'interruption.
     */
    fun runFullDiagnostic(context: Context, autoRepairOnFailure: Boolean = true) {
        if (_state.value.isRunning) return

        diagnosticJob?.cancel()
        diagnosticJob = scope.launch {
            _state.update {
                it.copy(
                    isRunning = true,
                    overallStatus = DiagnosticStatus.RUNNING,
                    stepResults = emptyMap(),
                    completedStepsCount = 0
                )
            }

            val results = mutableMapOf<DiagnosticStepId, DiagnosticStepResult>()
            val steps = DiagnosticStepId.entries

            for (step in steps) {
                _state.update { it.copy(currentRunningStep = step) }

                // Exécution du test de l'étape
                var result = executeStep(context, step)

                // Tentative d'auto-réparation si échec ou avertissement réparable
                if (autoRepairOnFailure && (result.status == DiagnosticStatus.FAILED || result.status == DiagnosticStatus.WARNING) && result.canAutoRepair) {
                    val repaired = attemptRepair(context, step)
                    if (repaired) {
                        // Re-tester immédiatement pour confirmer la correction
                        val recheckResult = executeStep(context, step)
                        result = if (recheckResult.status == DiagnosticStatus.SUCCESS || recheckResult.status == DiagnosticStatus.REPAIRED) {
                            recheckResult.copy(
                                status = DiagnosticStatus.REPAIRED,
                                detail = "${recheckResult.detail} (Auto-corrigé avec succès)"
                            )
                        } else {
                            recheckResult
                        }
                    }
                }

                results[step] = result
                _state.update {
                    it.copy(
                        stepResults = results.toMap(),
                        completedStepsCount = results.size
                    )
                }
            }

            val hasFailed = results.values.any { it.status == DiagnosticStatus.FAILED }
            val hasWarning = results.values.any { it.status == DiagnosticStatus.WARNING }
            val overall = when {
                hasFailed -> DiagnosticStatus.FAILED
                hasWarning -> DiagnosticStatus.WARNING
                else -> DiagnosticStatus.SUCCESS
            }

            val allPassed = overall == DiagnosticStatus.SUCCESS
            CallDiagnosticSettings.setLastDiagnosticResult(context, allPassed)

            _state.update {
                it.copy(
                    isRunning = false,
                    currentRunningStep = null,
                    overallStatus = overall,
                    lastRunTimestamp = System.currentTimeMillis()
                )
            }
            CallDiagnosticLogger.generateAndSaveReport(context, _state.value)
            Log.i(TAG, "Diagnostic terminé avec succès : global=$overall, étapes passées=${_state.value.passedCount}, réparées=${_state.value.repairedCount}")
        }
    }

    /**
     * Exécute une étape spécifique.
     */
    private suspend fun executeStep(context: Context, step: DiagnosticStepId): DiagnosticStepResult {
        return when (step) {
            DiagnosticStepId.PERMISSIONS -> {
                val hasAudio = PermissionGate.hasAudioPermission(context)
                val hasCamera = PermissionGate.hasCameraPermission(context)
                val hasNotif = PermissionGate.hasNotificationPermission(context)
                val hasPhone = PermissionGate.hasPhoneStatePermission(context)

                val missing = mutableListOf<String>()
                if (!hasAudio) missing.add("Microphone")
                if (!hasCamera) missing.add("Caméra")
                if (!hasNotif) missing.add("Notifications")
                if (!hasPhone) missing.add("État téléphone")

                if (missing.isEmpty()) {
                    DiagnosticStepResult(
                        stepId = step,
                        status = DiagnosticStatus.SUCCESS,
                        detail = "Toutes les autorisations requises sont accordées (Micro, Caméra, Notifications, Téléphonie)."
                    )
                } else {
                    DiagnosticStepResult(
                        stepId = step,
                        status = DiagnosticStatus.FAILED,
                        detail = "Autorisations manquantes : ${missing.joinToString(", ")}.",
                        canAutoRepair = true
                    )
                }
            }

            DiagnosticStepId.MICROPHONE -> AudioDiagnosticProbe.checkMicrophone(context)

            DiagnosticStepId.AUDIO_ROUTING -> AudioDiagnosticProbe.checkAudioRouting(context)

            DiagnosticStepId.CAMERA -> CameraDiagnosticProbe.checkCamera(context)

            DiagnosticStepId.WEBRTC_CODECS -> WebRtcDiagnosticProbe.checkWebRtcAndCodecs(context)

            DiagnosticStepId.NETWORK_ICE -> NetworkDiagnosticProbe.checkNetworkAndStun(context)

            DiagnosticStepId.TELECOM -> TelecomDiagnosticProbe.checkTelecom(context)

            DiagnosticStepId.BACKGROUND_DOZE -> BackgroundDozeProbe.checkBackgroundAndDoze(context)
        }
    }

    /**
     * Tente l'auto-réparation pour une étape donnée.
     */
    fun attemptRepair(context: Context, step: DiagnosticStepId): Boolean {
        return try {
            when (step) {
                DiagnosticStepId.PERMISSIONS -> {
                    // Les permissions nécessitent une interaction utilisateur
                    false
                }
                DiagnosticStepId.MICROPHONE -> {
                    AudioDiagnosticProbe.repairMicrophone(context)
                }
                DiagnosticStepId.AUDIO_ROUTING -> {
                    AudioDiagnosticProbe.repairAudioRouting(context)
                }
                DiagnosticStepId.CAMERA -> {
                    CameraDiagnosticProbe.repairCamera(context)
                }
                DiagnosticStepId.WEBRTC_CODECS -> {
                    WebRtcDiagnosticProbe.repairCodecs(context)
                }
                DiagnosticStepId.NETWORK_ICE -> {
                    NetworkDiagnosticProbe.repairNetworkIce(context)
                }
                DiagnosticStepId.TELECOM -> {
                    TelecomDiagnosticProbe.repairTelecom(context)
                }
                DiagnosticStepId.BACKGROUND_DOZE -> {
                    BackgroundDozeProbe.repairNotificationChannel(context)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        !NotificationManagerCompat.from(context).canUseFullScreenIntent()) {
                        BackgroundDozeProbe.openFullScreenIntentSettings(context)
                    }
                    true
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Échec tentative auto-réparation pour $step: ${e.message}")
            false
        }
    }

    /**
     * Déclenche la réparation manuelle ciblée d'une étape par l'utilisateur depuis l'UI.
     */
    fun repairAndRecheckSingleStep(context: Context, step: DiagnosticStepId) {
        scope.launch {
            _state.update { it.copy(isRepairing = true) }
            attemptRepair(context, step)
            val updated = executeStep(context, step)
            val finalResult = if (updated.status == DiagnosticStatus.SUCCESS || updated.status == DiagnosticStatus.REPAIRED) {
                updated.copy(status = DiagnosticStatus.REPAIRED, detail = "${updated.detail} (Réparé)")
            } else {
                updated
            }
            val currentMap = _state.value.stepResults.toMutableMap()
            currentMap[step] = finalResult
            _state.update {
                it.copy(
                    isRepairing = false,
                    stepResults = currentMap.toMap()
                )
            }
            CallDiagnosticLogger.generateAndSaveReport(context, _state.value)
        }
    }

    /**
     * Joue une tonalité de test audio interactive (haut-parleur).
     */
    fun playAudioTestTone(context: Context) {
        if (_state.value.isAudioTestPlaying) {
            AudioDiagnosticProbe.stopTestTone()
            _state.update { it.copy(isAudioTestPlaying = false) }
        } else {
            _state.update { it.copy(isAudioTestPlaying = true) }
            AudioDiagnosticProbe.playTestTone(context) {
                _state.update { it.copy(isAudioTestPlaying = false) }
            }
        }
    }

    fun stopAudioTestTone() {
        AudioDiagnosticProbe.stopTestTone()
        _state.update { it.copy(isAudioTestPlaying = false) }
    }
}
