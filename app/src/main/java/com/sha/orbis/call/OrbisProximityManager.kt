package com.sha.orbis.call

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestionnaire modulaire du capteur de proximité pour les appels vocaux Orbis.
 * 
 * Combine :
 * 1. [PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK] matériel pour éteindre l'écran et la dalle tactile
 *    lorsque le téléphone est porté à l'oreille.
 * 2. [Sensor.TYPE_PROXIMITY] pour détecter la proximité et basculer l'état dans l'UI.
 * 3. Gestion intelligente du haut-parleur (désactive l'extinction si le haut-parleur est activé).
 */
object OrbisProximityManager : SensorEventListener {

    private const val TAG = "OrbisProximity"
    private const val WAKE_LOCK_TAG = "Orbis:VoiceCallProximityLock"

    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var isSpeakerOn: Boolean = false
    private var isRunning: Boolean = false

    private val _isNear = MutableStateFlow(false)
    val isNear: StateFlow<Boolean> = _isNear.asStateFlow()

    /**
     * Active le capteur de proximité au début d'un appel.
     */
    @Synchronized
    fun start(context: Context, speakerInitiallyOn: Boolean = false) {
        val appContext = context.applicationContext
        isRunning = true
        isSpeakerOn = speakerInitiallyOn
        _isNear.value = false

        // 1. Initialisation et acquisition immédiate du WakeLock matériel PROXIMITY_SCREEN_OFF_WAKE_LOCK
        // Ce WakeLock indique au système Android d'éteindre l'écran dès que le téléphone est porté à l'oreille
        // et de le rallumer dès qu'il est éloigné.
        try {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                val isSupported = powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)
                if (isSupported) {
                    if (wakeLock == null) {
                        wakeLock = powerManager.newWakeLock(
                            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                            WAKE_LOCK_TAG
                        ).apply {
                            setReferenceCounted(false)
                        }
                    }
                    if (!isSpeakerOn && wakeLock?.isHeld == false) {
                        wakeLock?.acquire()
                        Log.i(TAG, "Proximity WakeLock acquis (l'OS éteindra l'écran à l'oreille)")
                    }
                } else {
                    Log.w(TAG, "PROXIMITY_SCREEN_OFF_WAKE_LOCK non supporté sur ce périphérique, utilisation du fallback capteur")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation WakeLock de proximité: ${e.message}", e)
        }

        // 2. Initialisation de l'écouteur de capteur de proximité physique (pour l'UI Compose _isNear)
        try {
            sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            if (proximitySensor != null) {
                sensorManager?.unregisterListener(this)
                sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d(TAG, "Capteur de proximité physique enregistré")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur enregistrement capteur de proximité: ${e.message}", e)
        }
    }

    /**
     * Met à jour l'état du haut-parleur.
     * Si le haut-parleur est activé, l'écran ne doit pas s'éteindre à proximité.
     */
    @Synchronized
    fun setSpeakerOn(speakerOn: Boolean) {
        if (!isRunning) return
        isSpeakerOn = speakerOn
        try {
            if (speakerOn) {
                // Désactiver l'extinction : relâcher le WakeLock
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                    Log.d(TAG, "Haut-parleur activé : Proximity WakeLock relâché")
                }
                _isNear.value = false
            } else {
                // Réactiver l'extinction pour appel à l'oreille
                if (wakeLock != null && !wakeLock!!.isHeld) {
                    wakeLock?.acquire()
                    Log.d(TAG, "Écouteur oreille activé : Proximity WakeLock réacquis")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur setSpeakerOn: ${e.message}")
        }
    }

    /**
     * Arrête le capteur et libère toutes les ressources à la fin de l'appel.
     */
    @Synchronized
    fun stop() {
        if (!isRunning) return
        isRunning = false
        isSpeakerOn = false
        _isNear.value = false

        // Libérer le WakeLock
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Proximity WakeLock libéré à la fin de l'appel")
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Erreur libération WakeLock: ${e.message}", e)
        }

        // Désinscrire le capteur
        try {
            sensorManager?.unregisterListener(this)
            sensorManager = null
            proximitySensor = null
        } catch (e: Exception) {
            Log.e(TAG, "Erreur désinscription capteur de proximité: ${e.message}", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isRunning || isSpeakerOn) return
        val sensor = proximitySensor ?: return
        val distance = event.values.getOrNull(0) ?: return
        val maxRange = sensor.maximumRange

        // Supports binary sensors (0.0 = near, maxRange = far)
        val near = distance < maxRange
        if (_isNear.value == near) return  // No state change — skip

        _isNear.value = near
        Log.d(TAG, "Proximity sensor state changed: isNear=$near (distance=$distance, maxRange=$maxRange)")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
