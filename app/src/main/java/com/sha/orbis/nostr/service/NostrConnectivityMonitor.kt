package com.sha.orbis.nostr.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log

/**
 * Moniteur de connectivité réseau pour le maillage décentralisé Nostr.
 * Détecte le rétablissement de la connectivité (Wi-Fi, Données Mobiles)
 * et réveille automatiquement les WebSockets du pool de relais.
 */
object NostrConnectivityMonitor {

    private const val TAG = "NostrConnMonitor"
    private var isRegistered = false

    fun start(context: Context) {
        if (isRegistered) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Connectivité réseau rétablie () -> Réactivation du pool Nostr")
                try {
                    NostrSyncManager.getInstance(appContext).reconnect()
                } catch (e: Exception) {
                    Log.w(TAG, "Erreur lors de la reconnexion Nostr: ")
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Connexion réseau perdue ()")
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(networkCallback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, networkCallback)
            }
            isRegistered = true
            Log.i(TAG, "NostrConnectivityMonitor enregistré avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "Impossible d'enregistrer le NetworkCallback: ")
        }
    }
}
