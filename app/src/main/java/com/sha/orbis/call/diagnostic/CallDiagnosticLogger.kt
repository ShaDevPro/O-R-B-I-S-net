package com.sha.orbis.call.diagnostic

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.sha.orbis.call.OrbisCallNotificationHelper
import com.sha.orbis.call.telecom.OrbisConnectionService
import com.sha.orbis.permissions.PermissionGate
import com.sha.orbis.security.BatteryOptimizationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.SoftwareVideoDecoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CallDiagnosticLogger — Générateur de rapport de débogage ciblé et persistant.
 *
 * Écrit un fichier texte structuré dans le dossier de l'application sur le téléphone :
 *   `/Android/data/com.sha.orbis/files/diagnostics/orbis_call_debug_report.txt`
 * Ce fichier se met à jour à chaque diagnostic ou tentative de réparation et permet
 * à l'utilisateur de le transmettre directement au développeur pour corriger les bugs
 * matériels ou opérateurs impossibles à résoudre automatiquement.
 */
object CallDiagnosticLogger {

    private const val TAG = "CallDiagnosticLogger"
    private const val FILE_NAME = "orbis_call_debug_report.txt"
    private const val DIR_NAME = "diagnostics"

    /**
     * Retourne le fichier de rapport sur le stockage de l'appareil.
     */
    fun getReportFile(context: Context): File {
        val dir = context.getExternalFilesDir(DIR_NAME) ?: File(context.filesDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, FILE_NAME)
    }

    /**
     * Retourne le chemin absolu lisible pour l'utilisateur.
     */
    fun getReportAbsolutePath(context: Context): String =
        getReportFile(context).absolutePath

    /**
     * Génère et met à jour le rapport texte complet à chaque essai.
     */
    suspend fun generateAndSaveReport(
        context: Context,
        state: CallDiagnosticState
    ): File = withContext(Dispatchers.IO) {
        val file = getReportFile(context)
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val nowStr = dateFormat.format(Date())

        sb.appendLine("================================================================================")
        sb.appendLine("                 RAPPORT DE DÉBOGAGE APPEL AUDIO & VIDÉO ORBISNET              ")
        sb.appendLine("================================================================================")
        sb.appendLine("Date & Heure        : $nowStr")
        sb.appendLine("Fichier généré dans : ${file.absolutePath}")
        sb.appendLine()

        // 1. Informations Appareil & OS
        sb.appendLine("[1. IDENTIFICATION MATÉRIEL & SYSTÈME]")
        sb.appendLine("Constructeur (Build.MANUFACTURER) : ${Build.MANUFACTURER}")
        sb.appendLine("Marque (Build.BRAND)             : ${Build.BRAND}")
        sb.appendLine("Modèle (Build.MODEL)             : ${Build.MODEL}")
        sb.appendLine("Appareil (Build.DEVICE)          : ${Build.DEVICE}")
        sb.appendLine("Carte mère (Build.BOARD)         : ${Build.BOARD}")
        sb.appendLine("SoC / Hardware (Build.HARDWARE)  : ${Build.HARDWARE}")
        sb.appendLine("Version Android                  : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Empreinte (Build.FINGERPRINT)    : ${Build.FINGERPRINT}")
        sb.appendLine()

        // 2. Synthèse de l'état du diagnostic
        sb.appendLine("[2. SYNTHÈSE DU DERNIER TEST]")
        sb.appendLine("Statut global     : ${state.overallStatus.name}")
        sb.appendLine("Étapes réussies   : ${state.passedCount} / ${state.totalSteps}")
        sb.appendLine("Étapes réparées   : ${state.repairedCount}")
        sb.appendLine("Avertissements    : ${state.warningCount}")
        sb.appendLine("Échecs bloquants  : ${state.failedCount}")
        sb.appendLine()

        // 3. Préférences de secours enregistrées
        sb.appendLine("[3. RÉGLAGES DE COMPATIBILITÉ ACTIFS]")
        sb.appendLine("Force Camera1 Fallback    : ${CallDiagnosticSettings.isForceCamera1(context)}")
        sb.appendLine("Force Software Codecs     : ${CallDiagnosticSettings.isForceSoftwareCodecs(context)}")
        sb.appendLine("Force TURN Relay Priorité : ${CallDiagnosticSettings.isForceTurnRelay(context)}")
        sb.appendLine()

        // 4. Détail étape par étape avec sondes profondes
        sb.appendLine("[4. DÉTAIL DES ÉTAPES & DONNÉES TECHNIQUES CIBLÉES]")

        // Étape 1 : Permissions
        val permResult = state.stepResults[DiagnosticStepId.PERMISSIONS]
        sb.appendLine("--- Étape 1 : Permissions Système ---")
        sb.appendLine("Statut : ${permResult?.status ?: "NON TESTÉ"}")
        sb.appendLine("Détail : ${permResult?.detail ?: ""}")
        sb.appendLine("  * RECORD_AUDIO       : ${PermissionGate.hasAudioPermission(context)}")
        sb.appendLine("  * CAMERA             : ${PermissionGate.hasCameraPermission(context)}")
        sb.appendLine("  * POST_NOTIFICATIONS : ${PermissionGate.hasNotificationPermission(context)}")
        sb.appendLine("  * READ_PHONE_STATE   : ${PermissionGate.hasPhoneStatePermission(context)}")
        sb.appendLine()

        // Étape 2 : Microphone
        val micResult = state.stepResults[DiagnosticStepId.MICROPHONE]
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        sb.appendLine("--- Étape 2 : Microphone & HAL Audio ---")
        sb.appendLine("Statut : ${micResult?.status ?: "NON TESTÉ"}")
        sb.appendLine("Détail : ${micResult?.detail ?: ""}")
        sb.appendLine("  * Microphone mute système : ${audioManager?.isMicrophoneMute}")
        sb.appendLine("  * Audio Mode actuel       : ${audioManager?.mode}")
        try {
            val minBuf = AudioRecord.getMinBufferSize(44100, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
            sb.appendLine("  * AudioRecord MinBufferSize (44100Hz mono 16b) : $minBuf octets")
        } catch (e: Throwable) {
            sb.appendLine("  * AudioRecord test exception : ${e.message}")
        }
        sb.appendLine()

        // Étape 3 : Routage Audio
        val routingResult = state.stepResults[DiagnosticStepId.AUDIO_ROUTING]
        sb.appendLine("--- Étape 3 : Routage Audio & Haut-Parleur ---")
        sb.appendLine("Statut : ${routingResult?.status ?: "NON TESTÉ"}")
        sb.appendLine("Détail : ${routingResult?.detail ?: ""}")
        @Suppress("DEPRECATION")
        sb.appendLine("  * isSpeakerphoneOn : ${audioManager?.isSpeakerphoneOn}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audioManager != null) {
            val devList = audioManager.availableCommunicationDevices.map { "${it.type} (${it.productName})" }
            sb.appendLine("  * Available Communication Devices (API 31+) : $devList")
            sb.appendLine("  * Current Communication Device               : ${audioManager.communicationDevice?.type}")
        }
        sb.appendLine()

        // Étape 4 : Caméra
        val camResult = state.stepResults[DiagnosticStepId.CAMERA]
        sb.appendLine("--- Étape 4 : Capteurs Caméra & Orientation ---")
        sb.appendLine("Statut : ${camResult?.status ?: "NON TESTÉ"}")
        sb.appendLine("Détail : ${camResult?.detail ?: ""}")
        try {
            val camMgr = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val camIds = camMgr?.cameraIdList ?: emptyArray()
            sb.appendLine("  * CameraManager IDs détectés : [${camIds.joinToString(", ")}]")
            for (id in camIds) {
                try {
                    val chars = camMgr?.getCameraCharacteristics(id)
                    val facing = chars?.get(CameraCharacteristics.LENS_FACING)
                    val facingStr = when (facing) {
                        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                        else -> "EXTERNAL"
                    }
                    val orient = chars?.get(CameraCharacteristics.SENSOR_ORIENTATION)
                    sb.appendLine("    - Cam $id: facing=$facingStr, orientation=$orient°")
                } catch (e: Throwable) {
                    sb.appendLine("    - Cam $id: erreur lecture caracs : ${e.message}")
                }
            }
        } catch (e: Throwable) {
            sb.appendLine("  * CameraManager error : ${e.message}")
        }
        sb.appendLine()

        // Étape 5 : WebRTC & Codecs
        val webrtcResult = state.stepResults[DiagnosticStepId.WEBRTC_CODECS]
        sb.appendLine("--- Étape 5 : Moteur WebRTC & Codecs Vidéo ---")
        sb.appendLine("Statut : ${webrtcResult?.status ?: "NON TESTÉ"}")
        sb.appendLine("Détail : ${webrtcResult?.detail ?: ""}")
        var egl: EglBase? = null
        try {
            egl = EglBase.create()
            val hwEnc = DefaultVideoEncoderFactory(egl.eglBaseContext, true, false)
            val hwCodecs = hwEnc.supportedCodecs.map { it.name }
            sb.appendLine("  * HW Video Encoders détectés : [${hwCodecs.joinToString(", ")}]")
            val swEnc = SoftwareVideoEncoderFactory()
            val swCodecs = swEnc.supportedCodecs.map { it.name }
            sb.appendLine("  * SW Video Encoders supportés : [${swCodecs.joinToString(", ")}]")
        } catch (e: Throwable) {
            sb.appendLine("  * Codecs diagnostic error : ${e.message}")
        } finally {
            try { egl?.release() } catch (_: Throwable) {}
        }
        sb.appendLine()

        // Étape 6 : Réseau & STUN
        val netResult = state.stepResults[DiagnosticStepId.NETWORK_ICE]
        sb.appendLine("--- Étape 6 : Réseau UDP & STUN ---")
        sb.appendLine("Statut : ${netResult?.status ?: "NON TESTÉ"}")
        sb.appendLine("Détail : ${netResult?.detail ?: ""}")
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        sb.appendLine("  * Type réseau actif : ${if (isWifi) "Wi-Fi" else if (isCellular) "Cellulaire (4G/5G)" else "Autre/Inconnu"}")
        sb.appendLine()

        // Étape 7 : Telecom
        val telecomResult = state.stepResults[DiagnosticStepId.TELECOM]
        sb.appendLine("--- Étape 7 : Intégration Android Telecom ---")
        sb.appendLine("Statut : ${telecomResult?.status ?: "NON TESTÉ"}")
        sb.appendLine("Détail : ${telecomResult?.detail ?: ""}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val handle = PhoneAccountHandle(android.content.ComponentName(context, OrbisConnectionService::class.java), "OrbisVoIPAccount")
            val acc = try { tm?.getPhoneAccount(handle) } catch (_: Throwable) { null }
            sb.appendLine("  * PhoneAccount registered : ${acc != null}")
            sb.appendLine("  * PhoneAccount isEnabled  : ${acc?.isEnabled}")
            sb.appendLine("  * isIncomingCallPermitted : ${try { tm?.isIncomingCallPermitted(handle) } catch (_: Throwable) { "error" }}")
            sb.appendLine("  * isOutgoingCallPermitted : ${try { tm?.isOutgoingCallPermitted(handle) } catch (_: Throwable) { "error" }}")
        }
        sb.appendLine()

        // Étape 8 : Arrière-plan & Doze
        val bgResult = state.stepResults[DiagnosticStepId.BACKGROUND_DOZE]
        sb.appendLine("--- Étape 8 : Arrière-Plan & Doze Mode ---")
        sb.appendLine("Statut : ${bgResult?.status ?: "NON TESTÉ"}")
        sb.appendLine("Détail : ${bgResult?.detail ?: ""}")
        sb.appendLine("  * Exemption batterie (Doze) : ${BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = nm?.getNotificationChannel(OrbisCallNotificationHelper.CHANNEL_CALLS)
            sb.appendLine("  * Canal appel existe        : ${channel != null}")
            sb.appendLine("  * Canal importance          : ${channel?.importance} (requis: ${NotificationManager.IMPORTANCE_HIGH})")
            sb.appendLine("  * Canal bypass DND          : ${channel?.canBypassDnd()}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            sb.appendLine("  * canUseFullScreenIntent    : ${NotificationManagerCompat.from(context).canUseFullScreenIntent()}")
        }
        sb.appendLine()

        // 5. Journal détaillé du dernier appel réel (Nostr / WebRTC / Audio / Vidéo)
        sb.append(LastCallDebugTracker.buildReportSection())
        sb.appendLine()

        // 6. Flux en direct des événements d'appel (Logs WebRTC & Nostr horodatés)
        sb.append(LastCallDebugTracker.buildCallEventsSection())
        sb.appendLine()

        // 7. Section d'aide pour le développeur
        val failingSteps = state.stepResults.filter { it.value.status == DiagnosticStatus.FAILED || it.value.status == DiagnosticStatus.WARNING }
        if (failingSteps.isNotEmpty()) {
            sb.appendLine("[7. ANOMALIES NON RÉSOLUES & RECOMMANDATIONS]")
            for ((step, res) in failingSteps) {
                sb.appendLine("- ${step.name} [${res.status.name}]: ${res.detail}")
            }
            sb.appendLine()
            sb.appendLine("Veuillez envoyer ce fichier texte au développeur pour implémenter un correctif spécifique pour ce modèle d'appareil.")
        } else {
            sb.appendLine("[7. CONCLUSION]")
            sb.appendLine("Tous les tests du moteur sont au vert.")
        }

        sb.appendLine("================================================================================")
        sb.appendLine("                              FIN DU RAPPORT                                    ")
        sb.appendLine("================================================================================")

        try {
            file.writeText(sb.toString())
            Log.i(TAG, "Rapport de diagnostic enregistré avec succès dans : ${file.absolutePath}")
        } catch (e: Throwable) {
            Log.e(TAG, "Erreur écriture rapport : ${e.message}")
        }

        file
    }

    /**
     * Génère et enregistre immédiatement le rapport de manière synchrone.
     */
    fun generateReportSync(context: Context, state: CallDiagnosticState): File {
        val file = getReportFile(context)
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val nowStr = dateFormat.format(Date())

        sb.appendLine("================================================================================")
        sb.appendLine("                 RAPPORT DE DÉBOGAGE APPEL AUDIO & VIDÉO ORBISNET              ")
        sb.appendLine("================================================================================")
        sb.appendLine("Date & Heure        : $nowStr")
        sb.appendLine("Fichier généré dans : ${file.absolutePath}")
        sb.appendLine()

        // 1. Informations Appareil & OS
        sb.appendLine("[1. IDENTIFICATION MATÉRIEL & SYSTÈME]")
        sb.appendLine("Constructeur (Build.MANUFACTURER) : ${Build.MANUFACTURER}")
        sb.appendLine("Marque (Build.BRAND)             : ${Build.BRAND}")
        sb.appendLine("Modèle (Build.MODEL)             : ${Build.MODEL}")
        sb.appendLine("Appareil (Build.DEVICE)          : ${Build.DEVICE}")
        sb.appendLine("Carte mère (Build.BOARD)         : ${Build.BOARD}")
        sb.appendLine("SoC / Hardware (Build.HARDWARE)  : ${Build.HARDWARE}")
        sb.appendLine("Version Android                  : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Empreinte (Build.FINGERPRINT)    : ${Build.FINGERPRINT}")
        sb.appendLine()

        // 2. Synthèse de l'état du diagnostic
        sb.appendLine("[2. SYNTHÈSE DU DERNIER TEST]")
        sb.appendLine("Statut global     : ${state.overallStatus.name}")
        sb.appendLine("Étapes réussies   : ${state.passedCount} / ${state.totalSteps}")
        sb.appendLine("Étapes réparées   : ${state.repairedCount}")
        sb.appendLine("Avertissements    : ${state.warningCount}")
        sb.appendLine("Échecs bloquants  : ${state.failedCount}")
        sb.appendLine()

        // 3. Réglages de secours actifs
        sb.appendLine("[3. RÉGLAGES DE COMPATIBILITÉ ACTIFS]")
        sb.appendLine("Force Camera1 Fallback    : ${CallDiagnosticSettings.isForceCamera1(context)}")
        sb.appendLine("Force Software Codecs     : ${CallDiagnosticSettings.isForceSoftwareCodecs(context)}")
        sb.appendLine("Force TURN Relay Priorité : ${CallDiagnosticSettings.isForceTurnRelay(context)}")
        sb.appendLine()

        // 4. Détail de chaque étape
        sb.appendLine("[4. DÉTAIL DES ÉTAPES & DONNÉES TECHNIQUES CIBLÉES]")
        for (step in DiagnosticStepId.entries) {
            val res = state.stepResults[step]
            sb.appendLine("--- Étape ${step.ordinal + 1} : ${step.name} ---")
            sb.appendLine("Statut : ${res?.status?.name ?: "NON EXÉCUTÉ"}")
            sb.appendLine("Détail : ${res?.detail ?: "Aucune donnée."}")
            sb.appendLine()
        }

        // 5. Journal détaillé du dernier appel réel
        sb.append(LastCallDebugTracker.buildReportSection())
        sb.appendLine()

        // 6. Flux en direct des événements d'appel (Logs WebRTC & Nostr horodatés)
        sb.append(LastCallDebugTracker.buildCallEventsSection())
        sb.appendLine()

        // 7. Section d'aide pour le développeur
        val failingSteps = state.stepResults.filter { it.value.status == DiagnosticStatus.FAILED || it.value.status == DiagnosticStatus.WARNING }
        if (failingSteps.isNotEmpty()) {
            sb.appendLine("[7. ANOMALIES NON RÉSOLUES & RECOMMANDATIONS]")
            for ((step, res) in failingSteps) {
                sb.appendLine("- ${step.name} [${res.status.name}]: ${res.detail}")
            }
            sb.appendLine()
            sb.appendLine("Veuillez envoyer ce fichier texte au développeur pour implémenter un correctif spécifique pour ce modèle d'appareil.")
        } else {
            sb.appendLine("[7. CONCLUSION]")
            sb.appendLine("Tous les tests du moteur sont au vert.")
        }

        sb.appendLine("================================================================================")
        sb.appendLine("                              FIN DU RAPPORT                                    ")
        sb.appendLine("================================================================================")

        try {
            file.writeText(sb.toString())
            Log.i(TAG, "Rapport de diagnostic enregistré avec succès dans : ${file.absolutePath}")
        } catch (e: Throwable) {
            Log.e(TAG, "Erreur écriture rapport : ${e.message}")
        }
        return file
    }

    /**
     * Ouvre la boîte de dialogue système de partage pour envoyer le fichier .txt
     */
    fun shareReport(context: Context) {
        try {
            // Toujours régénérer pour inclure les dernières métriques d'appel
            val file = generateReportSync(context, CallDiagnosticEngine.state.value)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Rapport de Débogage Appel OrbisNet - ${Build.MANUFACTURER} ${Build.MODEL}")
                putExtra(Intent.EXTRA_TEXT, "Voici le rapport de diagnostic d'appel pour mon appareil ${Build.BRAND} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}).")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(Intent.createChooser(intent, "Partager le rapport de débogage").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Erreur partage rapport : ${e.message}")
            Toast.makeText(context, "Erreur lors du partage : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Copie l'intégralité du texte du rapport dans le presse-papiers
     */
    fun copyReportToClipboard(context: Context) {
        try {
            // Toujours régénérer pour inclure les dernières métriques d'appel
            val file = generateReportSync(context, CallDiagnosticEngine.state.value)
            val content = file.readText()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Rapport Diagnostic OrbisNet", content)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Rapport copié dans le presse-papiers !", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(context, "Erreur copie : ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
