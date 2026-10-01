package com.sha.orbis.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.sha.orbis.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class UpdateStatus {
    object UpToDate : UpdateStatus()
    data class OptionalUpdateAvailable(val config: RemoteAppConfig) : UpdateStatus()
    data class ForceUpdateRequired(val config: RemoteAppConfig) : UpdateStatus()
}

class AppUpdateManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateManager"
        private const val PREFS_NAME = "orbis_update_prefs"
        private const val KEY_CACHED_CONFIG = "cached_config_json"

        @Volatile
        private var INSTANCE: AppUpdateManager? = null

        fun getInstance(context: Context): AppUpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppUpdateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.UpToDate)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private var _cachedConfig: RemoteAppConfig = loadCachedConfig()
    val currentConfig: RemoteAppConfig get() = _cachedConfig

    init {
        evaluateStatus(_cachedConfig)
    }

    private fun loadCachedConfig(): RemoteAppConfig {
        val raw = prefs.getString(KEY_CACHED_CONFIG, null) ?: return RemoteAppConfig.DEFAULT
        return try {
            val cfg = RemoteAppConfig.fromJson(JSONObject(raw))
            if (!cfg.forceUpdate) {
                cfg.copy(minRequiredVersionCode = 100, minRequiredVersionName = "1.0.0")
            } else {
                cfg
            }
        } catch (_: Exception) {
            RemoteAppConfig.DEFAULT
        }
    }

    private fun saveCachedConfig(config: RemoteAppConfig) {
        val safeConfig = if (!config.forceUpdate) {
            config.copy(minRequiredVersionCode = 100, minRequiredVersionName = "1.0.0")
        } else {
            config
        }
        _cachedConfig = safeConfig
        prefs.edit().putString(KEY_CACHED_CONFIG, RemoteAppConfig.toJson(safeConfig).toString()).apply()
        evaluateStatus(safeConfig)
    }

    fun evaluateStatus(config: RemoteAppConfig) {
        val myVersionCode = BuildConfig.VERSION_CODE
        val isForced = config.forceUpdate && (myVersionCode < config.minRequiredVersionCode || myVersionCode < config.latestVersionCode)

        _updateStatus.value = when {
            isForced -> UpdateStatus.ForceUpdateRequired(config)
            myVersionCode < config.latestVersionCode -> UpdateStatus.OptionalUpdateAvailable(config)
            else -> UpdateStatus.UpToDate
        }

        Log.d(TAG, "Évaluation statut mise à jour : myVersionCode=$myVersionCode, minRequired=${config.minRequiredVersionCode}, latest=${config.latestVersionCode}, isForced=$isForced, status=${_updateStatus.value::class.java.simpleName}")
    }

    fun onConfigReceived(config: RemoteAppConfig) {
        saveCachedConfig(config)
    }

    /**
     * Vérifie la présence d'une nouvelle version auprès du serveur de configuration.
     */
    fun checkRemoteConfig(backendUrl: String) {
        scope.launch {
            try {
                val cleanUrl = backendUrl.trim().removeSuffix("/")
                val requestUrl = "$cleanUrl/api/config"
                val request = Request.Builder()
                    .url(requestUrl)
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val config = RemoteAppConfig.fromJson(json)
                        onConfigReceived(config)
                        Log.i(TAG, "Configuration distante mise à jour avec succès : minCode=${config.minRequiredVersionCode}")
                    }
                } else {
                    Log.w(TAG, "Réponse HTTP non concluante lors de la vérification mise à jour : ${response.code}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Serveur distant non joignable pour mise à jour : ${e.message}")
            }
        }
    }

    /**
     * Ouvre l'URL de téléchargement dans le navigateur ou gestionnaire externe.
     */
    fun openDownloadUrl(url: String) {
        try {
            val targetUrl = url.ifBlank { _cachedConfig.downloadUrl }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Impossible d'ouvrir l'URL de téléchargement : ${e.message}")
        }
    }

    /**
     * Pour test/débogage dans la console admin : permet de simuler un forçage de mise à jour.
     */
    fun simulateForceUpdate(enable: Boolean) {
        if (enable) {
            _updateStatus.value = UpdateStatus.ForceUpdateRequired(
                _cachedConfig.copy(
                    minRequiredVersionCode = BuildConfig.VERSION_CODE + 1,
                    forceUpdate = true
                )
            )
        } else {
            evaluateStatus(_cachedConfig)
        }
    }
}
