package com.sha.orbis.call.diagnostic

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import com.sha.orbis.permissions.PermissionGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator

/**
 * Sonde de diagnostic et d'auto-réparation pour la caméra (HAL Camera2 / Camera1 et capteurs).
 */
object CameraDiagnosticProbe {

    private const val TAG = "CameraDiagnosticProbe"

    /**
     * Vérifie les capteurs de caméra avant/arrière et la compatibilité du HAL.
     */
    suspend fun checkCamera(context: Context): DiagnosticStepResult = withContext(Dispatchers.IO) {
        if (!PermissionGate.hasCameraPermission(context)) {
            return@withContext DiagnosticStepResult(
                stepId = DiagnosticStepId.CAMERA,
                status = DiagnosticStatus.FAILED,
                detail = "Permission caméra non accordée.",
                canAutoRepair = true
            )
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return@withContext DiagnosticStepResult(
                stepId = DiagnosticStepId.CAMERA,
                status = DiagnosticStatus.FAILED,
                detail = "CameraManager Android indisponible."
            )

        var camera2Ok = false
        var camera1Ok = false
        var frontId: String? = null
        var backId: String? = null
        var frontOrientation: Int? = null

        // 1. Tester Camera2
        try {
            val camera2 = Camera2Enumerator(context)
            val front = camera2.deviceNames.firstOrNull { camera2.isFrontFacing(it) }
            val back = camera2.deviceNames.firstOrNull { camera2.isBackFacing(it) }

            if (front != null || back != null) {
                camera2Ok = true
                frontId = front
                backId = back
            }

            // Vérifier les caractéristiques du capteur avant
            if (front != null) {
                val chars = cameraManager.getCameraCharacteristics(front)
                frontOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Camera2Enumerator a levé une exception: ${e.message}")
        }

        // 2. Tester Camera1 (Fallback universel)
        try {
            val camera1 = Camera1Enumerator(true)
            val front1 = camera1.deviceNames.firstOrNull { camera1.isFrontFacing(it) }
            val back1 = camera1.deviceNames.firstOrNull { camera1.isBackFacing(it) }
            if (front1 != null || back1 != null) {
                camera1Ok = true
                if (frontId == null) frontId = front1
                if (backId == null) backId = back1
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Camera1Enumerator a échoué: ${e.message}")
        }

        val isForcedCamera1 = CallDiagnosticSettings.isForceCamera1(context)

        return@withContext when {
            camera2Ok && !isForcedCamera1 -> {
                DiagnosticStepResult(
                    stepId = DiagnosticStepId.CAMERA,
                    status = DiagnosticStatus.SUCCESS,
                    detail = "Moteur Camera2 actif. Capteur avant : ${frontId ?: "Non disponible"}, Capteur arrière : ${backId ?: "Non disponible"} (Orientation: ${frontOrientation ?: 90}°)."
                )
            }
            camera1Ok -> {
                val status = if (isForcedCamera1) DiagnosticStatus.REPAIRED else DiagnosticStatus.WARNING
                DiagnosticStepResult(
                    stepId = DiagnosticStepId.CAMERA,
                    status = status,
                    detail = "Mode de secours Camera1 actif (Camera2 contourné pour stabilité OEM). Capteurs détectés : avant=${frontId != null}, arrière=${backId != null}.",
                    canAutoRepair = !isForcedCamera1
                )
            }
            else -> {
                DiagnosticStepResult(
                    stepId = DiagnosticStepId.CAMERA,
                    status = DiagnosticStatus.FAILED,
                    detail = "Aucun capteur caméra exploitable n'a pu être initialisé par le système.",
                    canAutoRepair = true
                )
            }
        }
    }

    /**
     * Répare la caméra en activant le mode de secours Camera1Enumerator.
     */
    fun repairCamera(context: Context): Boolean {
        return try {
            CallDiagnosticSettings.setForceCamera1(context, true)
            Log.i(TAG, "Fallback Camera1 activé dans CallDiagnosticSettings.")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Erreur réparation caméra: ${e.message}")
            false
        }
    }
}
