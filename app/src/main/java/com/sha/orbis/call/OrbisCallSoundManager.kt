package com.sha.orbis.call

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object OrbisCallSoundManager {

    private var toneGenerator: ToneGenerator? = null
    private var ringbackJob: Job? = null
    private var incomingRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun playOutgoingRingback(isSpeaker: Boolean) {
        stopAll()
        ringbackJob = scope.launch(Dispatchers.IO) {
            try {
                val streamType = if (isSpeaker) AudioManager.STREAM_MUSIC else AudioManager.STREAM_VOICE_CALL
                val volume = if (isSpeaker) 70 else 50
                toneGenerator = ToneGenerator(streamType, volume)
                while (true) {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1400)
                    delay(4000)
                }
            } catch (_: Exception) {}
        }
    }

    fun playIncomingRingtone(context: Context) {
        stopAll()
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            incomingRingtone = RingtoneManager.getRingtone(context.applicationContext, alertUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 1000, 1000)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 1000, 1000)
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    fun playEndCallTone() {
        stopAll()
        scope.launch(Dispatchers.IO) {
            try {
                val tg = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
                tg.startTone(ToneGenerator.TONE_PROP_PROMPT, 300)
                delay(350)
                tg.release()
            } catch (_: Exception) {}
        }
    }

    fun stopAll() {
        ringbackJob?.cancel()
        ringbackJob = null
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null

        try {
            incomingRingtone?.stop()
        } catch (_: Exception) {}
        incomingRingtone = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
        vibrator = null
    }
}
