package com.sha.orbis.call.diagnostic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.SoftwareVideoDecoderFactory
import org.webrtc.SoftwareVideoEncoderFactory

/**
 * Sonde de diagnostic pour le moteur WebRTC et les codecs vidéo (H.264 matériel vs logiciel).
 */
object WebRtcDiagnosticProbe {

    private const val TAG = "WebRtcDiagnosticProbe"

    suspend fun checkWebRtcAndCodecs(context: Context): DiagnosticStepResult = withContext(Dispatchers.IO) {
        var eglBase: EglBase? = null
        try {
            // 1. Tester l'environnement EGL OpenGL
            eglBase = EglBase.create()
            if (eglBase == null) {
                return@withContext DiagnosticStepResult(
                    stepId = DiagnosticStepId.WEBRTC_CODECS,
                    status = DiagnosticStatus.FAILED,
                    detail = "Échec d'initialisation du contexte OpenGL EglBase.",
                    canAutoRepair = true
                )
            }

            // 2. Initialiser la bibliothèque native PeerConnectionFactory si nécessaire
            try {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
            } catch (e: Throwable) {
                Log.w(TAG, "PeerConnectionFactory déjà initialisé ou avertissement: ${e.message}")
            }

            val isForcedSoftware = CallDiagnosticSettings.isForceSoftwareCodecs(context)

            // 3. Tester les encodeurs / décodeurs vidéo matériels
            val hwEncoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, false)
            val hwSupportedCodecs = hwEncoderFactory.supportedCodecs.map { it.name }
            val hasH264Hw = hwSupportedCodecs.any { it.equals("H264", ignoreCase = true) }
            val hasVp8Hw = hwSupportedCodecs.any { it.equals("VP8", ignoreCase = true) }

            // 4. Tester le fallback logiciel
            val swEncoderFactory = SoftwareVideoEncoderFactory()
            val swSupportedCodecs = swEncoderFactory.supportedCodecs.map { it.name }

            when {
                isForcedSoftware -> {
                    DiagnosticStepResult(
                        stepId = DiagnosticStepId.WEBRTC_CODECS,
                        status = DiagnosticStatus.REPAIRED,
                        detail = "Codecs logiciels sécurisés actifs (${swSupportedCodecs.joinToString(", ")}). Contournement des instabilités GPU matérielles OEM.",
                        canAutoRepair = false
                    )
                }
                hasH264Hw -> {
                    DiagnosticStepResult(
                        stepId = DiagnosticStepId.WEBRTC_CODECS,
                        status = DiagnosticStatus.SUCCESS,
                        detail = "Accélération matérielle active : Codecs GPU détectés : ${hwSupportedCodecs.joinToString(", ")}."
                    )
                }
                hasVp8Hw -> {
                    DiagnosticStepResult(
                        stepId = DiagnosticStepId.WEBRTC_CODECS,
                        status = DiagnosticStatus.WARNING,
                        detail = "H.264 matériel non supporté par le SoC. Codec VP8 matériel disponible (${hwSupportedCodecs.joinToString(", ")}).",
                        canAutoRepair = true
                    )
                }
                else -> {
                    DiagnosticStepResult(
                        stepId = DiagnosticStepId.WEBRTC_CODECS,
                        status = DiagnosticStatus.WARNING,
                        detail = "Aucun encodeur matériel compatible (H.264/VP8). Il est fortement recommandé d'activer le mode logiciel de secours.",
                        canAutoRepair = true
                    )
                }
            }
        } catch (e: Throwable) {
            DiagnosticStepResult(
                stepId = DiagnosticStepId.WEBRTC_CODECS,
                status = DiagnosticStatus.FAILED,
                detail = "Erreur d'initialisation WebRTC : ${e.message}",
                canAutoRepair = true
            )
        } finally {
            try {
                eglBase?.release()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Auto-réparation : force l'utilisation des encodeurs/décodeurs logiciels.
     */
    fun repairCodecs(context: Context): Boolean {
        return try {
            CallDiagnosticSettings.setForceSoftwareCodecs(context, true)
            Log.i(TAG, "Fallback Software Codecs activé dans CallDiagnosticSettings.")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Erreur réparation codecs: ${e.message}")
            false
        }
    }
}
