package com.sha.orbis.nostr.client

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.sha.orbis.nostr.model.NostrEvent
import com.sha.orbis.nostr.model.NostrFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Gestionnaire du pool de relais décentralisés Nostr.
 * Assure la redondance maximale (multi-relais), la déduplication intelligente des flux
 * et fournit l'état de santé du réseau en temps réel pour l'UI/UX.
 */
class RelayPoolManager private constructor(private val context: Context) : RelayClient.RelayListener {

    companion object {
        private const val TAG = "RelayPoolManager"

        val DEFAULT_RELAYS = listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.snort.social",
            "wss://relay.primal.net",
            "wss://nostr.mom"
        )

        @Volatile
        private var INSTANCE: RelayPoolManager? = null

        fun getInstance(context: Context): RelayPoolManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RelayPoolManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    data class RelayDetail(
        val url: String,
        val state: RelayClient.State,
        val pingMs: Long
    )

    data class PoolHealth(
        val totalRelays: Int,
        val connectedCount: Int,
        val averagePingMs: Long,
        val details: List<RelayDetail>
    ) {
        val isConnected: Boolean get() = connectedCount > 0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher(
                ThreadPoolExecutor(
                    0, 64, 60L, TimeUnit.SECONDS,
                    SynchronousQueue(),
                    ThreadFactory { runnable ->
                        Thread(runnable).apply {
                            name = "OkHttp-RelayPool"
                            isDaemon = true // CRITIQUE: threads daemon pour ne JAMAIS bloquer DestroyJavaVM (WaitForOtherNonDaemonThreadsToExit) → ANR
                        }
                    }
                )
            )
        )
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val clients = ConcurrentHashMap<String, RelayClient>()

    // Cache LRU pour dédupliquer les événements reçus simultanément de plusieurs relais
    private val seenEventIds = LruCache<String, Boolean>(2048)

    // Flux réactif des événements entrants validés et dédupliqués
    private val _incomingEvents = MutableSharedFlow<NostrEvent>(extraBufferCapacity = 128)
    val incomingEvents: SharedFlow<NostrEvent> = _incomingEvents.asSharedFlow()

    // Flux réactif de l'état du réseau pour l'interface utilisateur
    private val _poolHealth = MutableStateFlow(
        PoolHealth(
            totalRelays = DEFAULT_RELAYS.size,
            connectedCount = 0,
            averagePingMs = -1L,
            details = emptyList()
        )
    )
    val poolHealth: StateFlow<PoolHealth> = _poolHealth.asStateFlow()

    init {
        for (url in DEFAULT_RELAYS) {
            val client = RelayClient(url, okHttpClient, this)
            clients[url] = client
        }
        updateHealthState()
    }

    fun start() {
        Log.d(TAG, "Démarrage du pool de relais (${clients.size} relais configurés)")
        clients.values.forEach { it.connect() }
    }

    fun stop() {
        Log.d(TAG, "Arrêt du pool de relais")
        clients.values.forEach { it.disconnect() }
        okHttpClient.connectionPool.evictAll()
    }

    /**
     * Reconnecte les relais du pool déconnectés ou tous si [force] est true.
     */
    fun reconnect(force: Boolean = false) {
        Log.d(TAG, "Reconnexion du pool de relais (force=$force)")
        clients.values.forEach { it.reconnect(force) }
    }

    /**
     * Publie un événement en éventail (fan-out) vers tous les relais connectés.
     * Retourne le nombre de relais vers lesquels l'envoi a réussi.
     */
    fun publish(event: NostrEvent): Int {
        var sentCount = 0
        for (client in clients.values) {
            if (client.sendEvent(event)) {
                sentCount++
            }
        }
        Log.d(TAG, "Événement ${event.id.take(8)} diffusé à $sentCount/${clients.size} relais")
        return sentCount
    }

    /**
     * Souscrit à un filtre d'événements sur l'ensemble des relais du pool.
     */
    fun subscribe(subscriptionId: String, filters: List<NostrFilter>) {
        clients.values.forEach { it.subscribe(subscriptionId, filters) }
    }

    /**
     * Clôture une souscription sur l'ensemble des relais.
     */
    fun unsubscribe(subscriptionId: String) {
        clients.values.forEach { it.unsubscribe(subscriptionId) }
    }

    override fun onStateChanged(client: RelayClient, state: RelayClient.State) {
        if (state == RelayClient.State.ERROR) {
            try {
                com.sha.orbis.telemetry.TelemetryManager.getInstance(context).recordError("nostr_relay")
            } catch (_: Exception) {}
        }
        updateHealthState()
    }

    override fun onMessage(client: RelayClient, message: RelayMessage) {
        when (message) {
            is RelayMessage.EventMsg -> {
                val event = message.event
                val isNew: Boolean
                synchronized(seenEventIds) {
                    isNew = seenEventIds.get(event.id) == null
                    if (isNew) {
                        seenEventIds.put(event.id, true)
                    }
                }

                if (isNew) {
                    scope.launch {
                        _incomingEvents.emit(event)
                    }
                }
            }
            is RelayMessage.OkMsg -> {
                Log.d(TAG, "Relais ${client.url}: OK pour événement ${message.eventId.take(8)} (accepté=${message.accepted}): ${message.message}")
            }
            is RelayMessage.NoticeMsg -> {
                Log.d(TAG, "Notice de ${client.url}: ${message.message}")
            }
            else -> {}
        }
    }

    private fun updateHealthState() {
        val details = clients.values.map {
            RelayDetail(url = it.url, state = it.state, pingMs = it.pingMs)
        }
        val connected = details.count { it.state == RelayClient.State.CONNECTED }
        val pings = details.filter { it.state == RelayClient.State.CONNECTED && it.pingMs > 0 }.map { it.pingMs }
        val avgPing = if (pings.isNotEmpty()) pings.average().toLong() else -1L

        _poolHealth.value = PoolHealth(
            totalRelays = details.size,
            connectedCount = connected,
            averagePingMs = avgPing,
            details = details
        )
    }
}
