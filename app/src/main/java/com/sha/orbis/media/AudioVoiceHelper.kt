package com.sha.orbis.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

object AudioVoiceHelper {

    // Nostr Voice Payload Safe Limits (60s max AMR-NB @ 4750 bps fits within 64 KB Nostr frame)
    const val MAX_VOICE_DURATION_SECONDS = 60
    const val MAX_VOICE_BASE64_LENGTH = 45000

    private var recorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime: Long = 0L

    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingPath: String? = null

    fun isPayloadWithinNostrLimit(base64: String): Boolean {
        return base64.isNotBlank() && base64.length <= MAX_VOICE_BASE64_LENGTH
    }

    fun startRecording(context: Context): File? {
        stopPlaying()
        return try {
            val dir = File(context.filesDir, "voice_notes").apply { if (!exists()) mkdirs() }
            val file = File(dir, "voice_${System.currentTimeMillis()}.amr")
            currentRecordingFile = file
            recordingStartTime = System.currentTimeMillis()

            @Suppress("DEPRECATION")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setAudioEncodingBitRate(4750) // Ultra-compact AMR-NB mode 0
                setAudioSamplingRate(8000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            file
        } catch (_: Exception) {
            currentRecordingFile = null
            recorder = null
            null
        }
    }

    fun stopRecording(): Pair<File?, Int> {
        val file = currentRecordingFile
        val durationSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000L).toInt().coerceAtLeast(1)
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        recorder = null
        currentRecordingFile = null
        return Pair(file, durationSeconds)
    }

    fun cancelRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        currentRecordingFile?.delete()
        recorder = null
        currentRecordingFile = null
    }

    fun isRecording(): Boolean = recorder != null

    fun getMaxAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun fileToBase64(file: File): String {
        return try {
            val rawBytes = file.readBytes()
            // Strip the 6-byte AMR header (#!AMR\n) to save cellular bandwidth
            val dataBytes = if (rawBytes.size > 6 && rawBytes[0] == '#'.code.toByte() && rawBytes[1] == '!'.code.toByte()) {
                rawBytes.copyOfRange(6, rawBytes.size)
            } else {
                rawBytes
            }
            val compressed = com.sha.orbis.sms.BinarySmsCompressor.compress(dataBytes)
            Base64.encodeToString(compressed, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    fun base64ToFile(context: Context, base64: String, id: String): File? {
        return try {
            val clean = base64.trim()
            val decoded = Base64.decode(clean, Base64.DEFAULT)
            val rawData = try {
                com.sha.orbis.sms.BinarySmsCompressor.decompress(decoded)
            } catch (_: Exception) {
                decoded
            }
            val dir = File(context.filesDir, "voice_notes").apply { if (!exists()) mkdirs() }
            val cleanId = id.filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "voice_${System.currentTimeMillis()}" }
            val file = File(dir, "${cleanId}.amr")
            FileOutputStream(file).use { out ->
                // Ensure standard AMR header "#!AMR\n" is present for player compatibility
                if (rawData.isNotEmpty() && !(rawData[0] == '#'.code.toByte() && rawData.size > 1 && rawData[1] == '!'.code.toByte())) {
                    out.write("#!AMR\n".toByteArray(Charsets.US_ASCII))
                }
                out.write(rawData)
            }
            file
        } catch (_: Exception) {
            null
        }
    }

    private var currentSpeed: Float = 1.0f

    fun playAudio(file: File, speed: Float = currentSpeed, onFinished: () -> Unit) {
        stopPlaying()
        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    playbackParams = playbackParams.setSpeed(speed)
                }
                setOnCompletionListener {
                    stopPlaying()
                    onFinished()
                }
                start()
            }
            mediaPlayer = player
            currentPlayingPath = file.absolutePath
            currentSpeed = speed
        } catch (_: Exception) {
            onFinished()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.playbackParams = player.playbackParams.setSpeed(speed)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun getPlaybackSpeed(): Float = currentSpeed

    fun seekToFraction(fraction: Float) {
        try {
            mediaPlayer?.let { player ->
                val targetMs = (fraction.coerceIn(0f, 1f) * player.duration).toInt()
                player.seekTo(targetMs)
            }
        } catch (_: Exception) {}
    }

    fun stopPlaying() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        currentPlayingPath = null
    }

    fun isPlaying(filePath: String): Boolean {
        return mediaPlayer?.isPlaying == true && currentPlayingPath == filePath
    }

    fun getPlaybackProgress(): Float {
        return try {
            val mp = mediaPlayer ?: return 0f
            if (mp.isPlaying && mp.duration > 0) {
                mp.currentPosition.toFloat() / mp.duration.toFloat()
            } else {
                0f
            }
        } catch (_: Exception) {
            0f
        }
    }

    fun getCurrentPositionSeconds(): Int {
        return try {
            val mp = mediaPlayer ?: return 0
            if (mp.isPlaying) mp.currentPosition / 1000 else 0
        } catch (_: Exception) {
            0
        }
    }
}
