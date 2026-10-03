package com.sha.orbis.call.diagnostic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.sha.orbis.permissions.PermissionGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

/**
 * Sonde de diagnostic et d'auto-réparation pour l'audio (Microphone, routage et haut-parleur).
 */
object AudioDiagnosticProbe {

    private const val TAG = "AudioDiagnosticProbe"
    private var activeTestTrack: AudioTrack? = null

    /**
     * Teste la disponibilité et l'initialisation du microphone.
     */
    suspend fun checkMicrophone(context: Context): DiagnosticStepResult = withContext(Dispatchers.IO) {
        if (!PermissionGate.hasAudioPermission(context)) {
            return@withContext DiagnosticStepResult(
                stepId = DiagnosticStepId.MICROPHONE,
                status = DiagnosticStatus.FAILED,
                detail = "Permission d'enregistrement audio non accordée.",
                canAutoRepair = true
            )
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return@withContext DiagnosticStepResult(
                stepId = DiagnosticStepId.MICROPHONE,
                status = DiagnosticStatus.FAILED,
                detail = "AudioManager indisponible sur cet appareil."
            )

        // 1. Vérifier si le micro est coupé au niveau système
        if (audioManager.isMicrophoneMute) {
            return@withContext DiagnosticStepResult(
                stepId = DiagnosticStepId.MICROPHONE,
                status = DiagnosticStatus.WARNING,
                detail = "Le microphone est actuellement muet au niveau du système.",
                canAutoRepair = true
            )
        }

        // 2. Tester l'initialisation d'un AudioRecord (VOICE_COMMUNICATION ou MIC)
        var record: AudioRecord? = null
        try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (bufferSize <= 0) {
                return@withContext DiagnosticStepResult(
                    stepId = DiagnosticStepId.MICROPHONE,
                    status = DiagnosticStatus.FAILED,
                    detail = "Configuration de buffer audio invalide (${bufferSize}).",
                    canAutoRepair = true
                )
            }

            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                // Essai fallback MIC standard
                record.release()
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    16000,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            }

            if (record.state == AudioRecord.STATE_INITIALIZED) {
                DiagnosticStepResult(
                    stepId = DiagnosticStepId.MICROPHONE,
                    status = DiagnosticStatus.SUCCESS,
                    detail = "Microphone opérationnel (échantillonnage ${record.sampleRate} Hz, canal mono)."
                )
            } else {
                DiagnosticStepResult(
                    stepId = DiagnosticStepId.MICROPHONE,
                    status = DiagnosticStatus.FAILED,
                    detail = "Impossible d'initialiser le pilote micro (HAL occupé par une autre application).",
                    canAutoRepair = true
                )
            }
        } catch (e: SecurityException) {
            DiagnosticStepResult(
                stepId = DiagnosticStepId.MICROPHONE,
                status = DiagnosticStatus.FAILED,
                detail = "Accès micro refusé par la politique de sécurité : ${e.message}",
                canAutoRepair = true
            )
        } catch (e: Throwable) {
            DiagnosticStepResult(
                stepId = DiagnosticStepId.MICROPHONE,
                status = DiagnosticStatus.FAILED,
                detail = "Erreur micro : ${e.message}",
                canAutoRepair = true
            )
        } finally {
            try {
                record?.release()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Répare le microphone (démute système et réinitialisation de priorité).
     */
    fun repairMicrophone(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            if (audioManager.isMicrophoneMute) {
                audioManager.isMicrophoneMute = false
                Log.i(TAG, "Microphone démute avec succès.")
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Erreur réparation micro: ${e.message}")
            false
        }
    }

    /**
     * Teste le routage audio (haut-parleur / écouteur / périphériques disponibles).
     */
    suspend fun checkAudioRouting(context: Context): DiagnosticStepResult = withContext(Dispatchers.IO) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return@withContext DiagnosticStepResult(
                stepId = DiagnosticStepId.AUDIO_ROUTING,
                status = DiagnosticStatus.FAILED,
                detail = "AudioManager indisponible."
            )

        val devices = mutableListOf<String>()
        var hasSpeaker = false
        var hasEarpiece = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val commDevices = audioManager.availableCommunicationDevices
            for (dev in commDevices) {
                when (dev.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                        hasSpeaker = true
                        devices.add("Haut-parleur")
                    }
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> {
                        hasEarpiece = true
                        devices.add("Écouteur")
                    }
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                        devices.add("Bluetooth (${dev.productName ?: "Casque"})")
                    }
                }
            }
        } else {
            hasSpeaker = true
            hasEarpiece = true
            devices.add("Haut-parleur standard")
            devices.add("Écouteur standard")
        }

        val modeStr = when (audioManager.mode) {
            AudioManager.MODE_NORMAL -> "Normal"
            AudioManager.MODE_IN_COMMUNICATION -> "Communication VoIP"
            AudioManager.MODE_IN_CALL -> "Appel GSM"
            AudioManager.MODE_RINGTONE -> "Sonnerie"
            else -> "Inconnu (${audioManager.mode})"
        }

        val currentCallVol = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        val maxCallVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        val volPercent = if (maxCallVol > 0) (currentCallVol * 100) / maxCallVol else 100

        if (devices.isEmpty()) {
            DiagnosticStepResult(
                stepId = DiagnosticStepId.AUDIO_ROUTING,
                status = DiagnosticStatus.WARNING,
                detail = "Aucun périphérique audio de communication détecté. Volume appel: $volPercent%.",
                canAutoRepair = true,
                isInteractiveTestAvailable = true
            )
        } else if (currentCallVol == 0) {
            DiagnosticStepResult(
                stepId = DiagnosticStepId.AUDIO_ROUTING,
                status = DiagnosticStatus.WARNING,
                detail = "Périphériques : ${devices.joinToString(", ")}. ALERTE : Le volume d'appel vocal système est à 0% (muet) !",
                canAutoRepair = true,
                isInteractiveTestAvailable = true
            )
        } else {
            DiagnosticStepResult(
                stepId = DiagnosticStepId.AUDIO_ROUTING,
                status = DiagnosticStatus.SUCCESS,
                detail = "Périphériques détectés : ${devices.joinToString(", ")}. Mode : $modeStr, Volume appel : $volPercent%.",
                isInteractiveTestAvailable = true
            )
        }
    }

    /**
     * Répare le routage audio (applique MODE_IN_COMMUNICATION et force le routage).
     */
    fun repairAudioRouting(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speakerDevice = audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speakerDevice != null) {
                    audioManager.setCommunicationDevice(speakerDevice)
                }
            }

            val maxCallVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            if (audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) == 0 && maxCallVol > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxCallVol * 0.7f).toInt().coerceAtLeast(1), 0)
                Log.i(TAG, "Volume d'appel relevé à 70%.")
            }

            Log.i(TAG, "Routage audio forcé avec succès en MODE_IN_COMMUNICATION.")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Erreur réparation routage audio: ${e.message}")
            false
        }
    }

    /**
     * Joue une tonalité de test synthétique de 1 seconde (440Hz / 880Hz) pour valider
     * que la sortie audio (haut-parleur) fonctionne sans distorsion.
     */
    fun playTestTone(context: Context, onComplete: () -> Unit) {
        stopTestTone()
        Thread {
            try {
                val sampleRate = 44100
                val durationMs = 1200
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)
                val freq = 440.0 // La 440Hz

                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i * freq / sampleRate
                    // Enveloppe d'amplitude pour éviter le clic de début/fin
                    val envelope = when {
                        i < 1000 -> i / 1000.0
                        i > numSamples - 1000 -> (numSamples - i) / 1000.0
                        else -> 1.0
                    }
                    buffer[i] = (sin(angle) * Short.MAX_VALUE * 0.4 * envelope).toInt().toShort()
                }

                val minBuffer = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                activeTestTrack = track
                track.write(buffer, 0, buffer.size)
                track.play()
                Thread.sleep(durationMs.toLong() + 100)
                track.stop()
                track.release()
                activeTestTrack = null
            } catch (e: Throwable) {
                Log.w(TAG, "Erreur lecture test tone: ${e.message}")
            } finally {
                onComplete()
            }
        }.start()
    }

    fun stopTestTone() {
        try {
            activeTestTrack?.stop()
            activeTestTrack?.release()
            activeTestTrack = null
        } catch (_: Throwable) {}
    }
}
