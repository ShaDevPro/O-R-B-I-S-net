package com.sha.orbis.telemetry

import android.content.Context
import android.os.Build
import android.util.Log
import com.sha.orbis.BuildConfig
import com.sha.orbis.update.AppUpdateManager
import com.sha.orbis.update.RemoteAppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TelemetryManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TelemetryManager"
        private const val PREFS_NAME = "orbis_telemetry_prefs"
        private const val KEY_INSTALL_ID_HASH = "install_id_hash"
        private const val KEY_BACKEND_URL = "custom_backend_url"
        private const val KEY_ADMIN_SECRET = "custom_admin_secret"

        // URL par défaut vers le backend Vercel
        const val DEFAULT_BACKEND_URL = "https://orbis-net.vercel.app"

        @Volatile
        private var INSTANCE: TelemetryManager? = null

        fun getInstance(context: Context): TelemetryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TelemetryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var _sessionSecret: String? = null

    // Mot de passe superviseur : conservé en session ou mémorisé localement si autorisé
    var sessionAdminSecret: String?
        get() = _sessionSecret ?: prefs.getString(KEY_ADMIN_SECRET, null)
        set(value) {
            _sessionSecret = value
        }

    fun rememberAdminSecret(secret: String?, persistOnDevice: Boolean) {
        _sessionSecret = secret
        if (persistOnDevice && !secret.isNullOrBlank()) {
            prefs.edit().putString(KEY_ADMIN_SECRET, secret).apply()
        } else {
            prefs.edit().remove(KEY_ADMIN_SECRET).apply()
        }
    }

    val isSecretSavedLocally: Boolean
        get() = !prefs.getString(KEY_ADMIN_SECRET, null).isNullOrBlank()

    private fun getOrCreateInstallIdHash(): String {
        val existing = prefs.getString(KEY_INSTALL_ID_HASH, null)
        if (!existing.isNullOrBlank()) return existing

        val rawUuid = UUID.randomUUID().toString() + ":" + System.currentTimeMillis()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(rawUuid.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it) }

        prefs.edit().putString(KEY_INSTALL_ID_HASH, hash).apply()
        return hash
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    // Buffer d'événements et d'erreurs en mémoire : thread-safe et coût CPU nul
    private val eventCounters = ConcurrentHashMap<String, AtomicInteger>()
    private val errorCounters = ConcurrentHashMap<String, AtomicInteger>()

    val installIdHash: String by lazy { getOrCreateInstallIdHash() }

    var backendUrl: String
        get() {
            val saved = prefs.getString(KEY_BACKEND_URL, null)
            if (saved.isNullOrBlank() || saved == "https://orbis-backend.vercel.app") {
                return DEFAULT_BACKEND_URL
            }
            return saved
        }
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value.trim().removeSuffix("/")).apply()

    /**
     * Lance la synchronisation de télémétrie de manière asynchrone non-bloquante.
     */
    fun syncTelemetryAsync() {
        scope.launch {
            syncTelemetry()
        }
    }

    /**
     * Enregistre l'usage d'une fonctionnalité dans le tampon local en mémoire.
     */
    fun recordEvent(feature: FeatureType, count: Int = 1) {
        eventCounters.computeIfAbsent(feature.key) { AtomicInteger(0) }.addAndGet(count)
    }

    /**
     * Enregistre un incident technique anonymisé par catégorie (webrtc, relay, storage, media, network).
     */
    fun recordError(category: String, count: Int = 1) {
        val safeCat = category.lowercase().replace(Regex("[^a-z0-9_]"), "").take(32).ifBlank { "system" }
        errorCounters.computeIfAbsent(safeCat) { AtomicInteger(0) }.addAndGet(count)
    }

    private fun detectNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return "UNKNOWN"
            val net = cm.activeNetwork ?: return "OFFLINE"
            val caps = cm.getNetworkCapabilities(net) ?: return "OFFLINE"
            when {
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "OTHER"
            }
        } catch (_: Exception) {
            "UNKNOWN"
        }
    }

    /**
     * Transmet le lot d'événements anonymisés vers le serveur Vercel.
     * En retour, extrait la configuration de version et met à jour AppUpdateManager.
     */
    suspend fun syncTelemetry(): Boolean = withContext(Dispatchers.IO) {
        val eventsSnapshot = mutableListOf<FeatureCount>()
        val errorsSnapshot = mutableListOf<TechnicalErrorCount>()
        try {
            for ((key, atomicCount) in eventCounters) {
                val c = atomicCount.getAndSet(0)
                if (c > 0) {
                    eventsSnapshot.add(FeatureCount(key, c))
                }
            }

            for ((key, atomicCount) in errorCounters) {
                val c = atomicCount.getAndSet(0)
                if (c > 0) {
                    errorsSnapshot.add(TechnicalErrorCount(key, c))
                }
            }

            val isBatteryExempt = com.sha.orbis.security.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            val poolHealth = com.sha.orbis.nostr.client.RelayPoolManager.getInstance(context).poolHealth.value

            val payload = JSONObject().apply {
                put("installIdHash", installIdHash)
                put("appVersionCode", BuildConfig.VERSION_CODE)
                put("appVersionName", BuildConfig.VERSION_NAME)
                put("osVersion", Build.VERSION.SDK_INT)
                put("androidRelease", Build.VERSION.RELEASE ?: "")
                put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                put("locale", Locale.getDefault().toString())
                put("networkType", detectNetworkType())
                put("isBatteryExempt", isBatteryExempt)
                put("nostrRelays", JSONObject().apply {
                    put("total", poolHealth.totalRelays)
                    put("connected", poolHealth.connectedCount)
                })

                val eventsArray = JSONArray()
                for (ev in eventsSnapshot) {
                    eventsArray.put(JSONObject().apply {
                        put("feature", ev.feature)
                        put("count", ev.count)
                    })
                }
                put("events", eventsArray)

                val errorsArray = JSONArray()
                for (err in errorsSnapshot) {
                    errorsArray.put(JSONObject().apply {
                        put("category", err.category)
                        put("count", err.count)
                    })
                }
                put("errors", errorsArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = payload.toString().toRequestBody(mediaType)
            val url = "$backendUrl/api/telemetry"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string()
                if (!bodyString.isNullOrBlank()) {
                    val respJson = JSONObject(bodyString)
                    val configJson = respJson.optJSONObject("config")
                    if (configJson != null) {
                        val remoteConfig = RemoteAppConfig.fromJson(configJson)
                        AppUpdateManager.getInstance(context).onConfigReceived(remoteConfig)
                    }
                    val detectedCountry = respJson.optString("detectedCountry", "")
                    Log.i(TAG, "Télémétrie transmise avec succès (Pays détecté: $detectedCountry)")
                }
                return@withContext true
            } else {
                // Remettre les compteurs en mémoire en cas d'échec réseau
                for (ev in eventsSnapshot) {
                    recordEvent(FeatureType.values().firstOrNull { it.key == ev.feature } ?: continue, ev.count)
                }
                for (err in errorsSnapshot) {
                    errorCounters.computeIfAbsent(err.category) { AtomicInteger(0) }.addAndGet(err.count)
                }
                Log.w(TAG, "Échec de l'envoi de la télémétrie HTTP ${response.code}")
                return@withContext false
            }
        } catch (e: Exception) {
            for (ev in eventsSnapshot) {
                recordEvent(FeatureType.values().firstOrNull { it.key == ev.feature } ?: continue, ev.count)
            }
            for (err in errorsSnapshot) {
                errorCounters.computeIfAbsent(err.category) { AtomicInteger(0) }.addAndGet(err.count)
            }
            Log.d(TAG, "Serveur de télémétrie non joignable (${e.message})")
            return@withContext false
        }
    }

    /**
     * Résultat de la requête d'administration vérifiée par le serveur.
     */
    data class AdminStatsResult(
        val isSuccess: Boolean,
        val statusCode: Int,
        val data: JSONObject? = null,
        val errorMessage: String? = null
    )

    /**
     * Récupère les métriques consolidées depuis le backend Vercel pour la console admin.
     * L'accès est validé à chaque appel par le serveur via la clé d'administration.
     */
    suspend fun fetchAdminStats(secretOverride: String? = null): AdminStatsResult = withContext(Dispatchers.IO) {
        val keyToUse = secretOverride ?: sessionAdminSecret ?: ""
        try {
            val url = "$backendUrl/api/admin/stats"
            val request = Request.Builder()
                .url(url)
                .addHeader("x-admin-key", keyToUse)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    return@withContext AdminStatsResult(isSuccess = true, statusCode = code, data = JSONObject(body))
                }
                return@withContext AdminStatsResult(isSuccess = false, statusCode = code, errorMessage = "Réponse vide du serveur")
            } else if (code == 401 || code == 403) {
                return@withContext AdminStatsResult(isSuccess = false, statusCode = code, errorMessage = "Accès refusé : mot de passe incorrect")
            }
            AdminStatsResult(isSuccess = false, statusCode = code, errorMessage = "Erreur serveur HTTP $code")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la récupération des stats admin : ${e.message}")
            AdminStatsResult(isSuccess = false, statusCode = -1, errorMessage = e.message ?: "Serveur injoignable")
        }
    }

    /**
     * Met à jour la configuration distante depuis la console admin de l'application.
     * Protégé et vérifié par le serveur à chaque écriture.
     */
    suspend fun pushRemoteConfig(config: RemoteAppConfig, secretOverride: String? = null): Boolean = withContext(Dispatchers.IO) {
        val keyToUse = secretOverride ?: sessionAdminSecret ?: ""
        try {
            val url = "$backendUrl/api/admin/config"
            val payload = RemoteAppConfig.toJson(config).toString()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(url)
                .addHeader("x-admin-key", keyToUse)
                .post(payload.toRequestBody(mediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                AppUpdateManager.getInstance(context).onConfigReceived(config)
                return@withContext true
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Erreur mise à jour config distante : ${e.message}")
            false
        }
    }

    fun schedulePeriodicSync() {
        TelemetrySyncWorker.schedulePeriodic(context)
    }
}
