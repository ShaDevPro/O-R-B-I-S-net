package com.sha.orbis.nostr.client

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sha.orbis.nostr.model.NostrEvent
import com.sha.orbis.nostr.model.NostrFilter
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Client WebSocket dédié à un unique relais Nostr public.
 * Fournit la reconnexion automatique avec backoff, la mesure de latence en temps réel
 * et la réémission automatique des abonnements actifs en cas de déconnexion réseau.
 */
class RelayClient(
    val url: String,
    private val okHttpClient: OkHttpClient,
    private val listener: RelayListener
) {
    enum class State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    interface RelayListener {
        fun onStateChanged(client: RelayClient, state: State)
        fun onMessage(client: RelayClient, message: RelayMessage)
    }

    private val tag = "RelayClient[${url.substringAfter("://").take(15)}]"
    private var webSocket: WebSocket? = null
    private val handler = Handler(Looper.getMainLooper())
    private val isManuallyClosed = AtomicBoolean(false)

    var state: State = State.DISCONNECTED
        private set(value) {
            field = value
            listener.onStateChanged(this, value)
        }

    var pingMs: Long = -1L
        private set

    private var lastConnectAttempt = 0L
    private var reconnectDelayMs = 1000L
    private val maxReconnectDelayMs = 30000L

    // Cache des abonnements actifs pour réémission automatique à la reconnexion
    private val activeSubscriptions = ConcurrentHashMap<String, List<NostrFilter>>()

    private val wsListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val latency = System.currentTimeMillis() - lastConnectAttempt
            pingMs = latency.coerceAtLeast(10L)
            Log.d(tag, "Connecté avec succès à $url (latence: ${pingMs}ms)")
            state = State.CONNECTED
            reconnectDelayMs = 1000L

            // Réémettre tous les abonnements actifs
            for ((subId, filters) in activeSubscriptions) {
                val reqMsg = RelayMessage.sendReq(subId, filters)
                webSocket.send(reqMsg)
                Log.d(tag, "Réabonnement automatique transmis pour $subId")
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = RelayMessage.parse(text)
            listener.onMessage(this@RelayClient, msg)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(tag, "WebSocket fermé: $code / $reason")
            state = State.DISCONNECTED
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(tag, "Erreur connexion relais $url: ${t.message}")
            state = State.ERROR
            scheduleReconnect()
        }
    }

    fun connect() {
        if (state == State.CONNECTED || state == State.CONNECTING) return
        isManuallyClosed.set(false)
        state = State.CONNECTING
        lastConnectAttempt = System.currentTimeMillis()

        try {
            val request = Request.Builder()
                .url(url)
                .build()
            webSocket = okHttpClient.newWebSocket(request, wsListener)
        } catch (e: Exception) {
            Log.e(tag, "Exception lors de l'ouverture du WebSocket: ${e.message}")
            state = State.ERROR
            scheduleReconnect()
        }
    }

    fun disconnect() {
        isManuallyClosed.set(true)
        handler.removeCallbacksAndMessages(null)
        try {
            webSocket?.close(1000, "Normal Closure")
        } catch (ignored: Exception) {}
        webSocket = null
        state = State.DISCONNECTED
    }

    /**
     * Tente de reconnecter le relais. Si [force] est vrai, ferme le socket actif
     * ou en attente pour forcer une nouvelle poignée de main immédiate.
     */
    fun reconnect(force: Boolean = false) {
        if (force) {
            disconnect()
            reconnectDelayMs = 1000L
            connect()
        } else if (state != State.CONNECTED) {
            if (state == State.CONNECTING && (System.currentTimeMillis() - lastConnectAttempt > 15000L)) {
                disconnect()
                reconnectDelayMs = 1000L
            }
            connect()
        }
    }

    fun sendEvent(event: NostrEvent): Boolean {
        val ws = webSocket
        if (state != State.CONNECTED || ws == null) {
            return false
        }
        val msg = RelayMessage.sendEvent(event)
        return ws.send(msg)
    }

    fun subscribe(subscriptionId: String, filters: List<NostrFilter>): Boolean {
        activeSubscriptions[subscriptionId] = filters
        val ws = webSocket
        if (state == State.CONNECTED && ws != null) {
            val msg = RelayMessage.sendReq(subscriptionId, filters)
            return ws.send(msg)
        }
        return false
    }

    fun unsubscribe(subscriptionId: String) {
        activeSubscriptions.remove(subscriptionId)
        val ws = webSocket
        if (state == State.CONNECTED && ws != null) {
            val msg = RelayMessage.sendClose(subscriptionId)
            ws.send(msg)
        }
    }

    private fun scheduleReconnect() {
        if (isManuallyClosed.get()) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!isManuallyClosed.get() && state != State.CONNECTED) {
                Log.d(tag, "Tentative de reconnexion automatique vers $url (délai: ${reconnectDelayMs}ms)...")
                connect()
                reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(maxReconnectDelayMs)
            }
        }, reconnectDelayMs)
    }
}
