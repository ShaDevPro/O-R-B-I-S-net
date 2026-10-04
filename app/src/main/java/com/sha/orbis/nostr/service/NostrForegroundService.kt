package com.sha.orbis.nostr.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service managing persistent Nostr relay connections — public stub.
 * Full implementation is proprietary and not included in this repository.
 */
class NostrForegroundService : Service() {

    companion object {
        fun start(context: Context) {
            // stub
        }

        fun stop(context: Context) {
            // stub
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
