package com.sha.orbis.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class OrbisCallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ACCEPT_CALL       = "com.sha.orbis.ACTION_ACCEPT_CALL"
        const val ACTION_ACCEPT_VIDEO_CALL = "com.sha.orbis.ACTION_ACCEPT_VIDEO_CALL"
        const val ACTION_ACCEPT_VOICE_CALL = "com.sha.orbis.ACTION_ACCEPT_VOICE_CALL"
        const val ACTION_DECLINE_CALL      = "com.sha.orbis.ACTION_DECLINE_CALL"
        const val ACTION_END_CALL          = "com.sha.orbis.ACTION_END_CALL"
        const val ACTION_CALL_BACK         = "com.sha.orbis.ACTION_CALL_BACK"
        const val ACTION_MESSAGE_BACK      = "com.sha.orbis.ACTION_MESSAGE_BACK"
        const val ACTION_DISMISS_MISSED_CALL = "com.sha.orbis.ACTION_DISMISS_MISSED_CALL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            ACTION_ACCEPT_CALL, ACTION_ACCEPT_VIDEO_CALL -> {
                OrbisCallNotificationHelper.cancelCallNotification(context)
                OrbisCallManager.acceptCall(withVideo = (action == ACTION_ACCEPT_VIDEO_CALL))
                val incomingIntent = Intent(context, com.sha.orbis.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("extra_incoming_call", true)
                    putExtra("extra_call_id", intent.getStringExtra("extra_call_id"))
                }
                context.startActivity(incomingIntent)
            }
            ACTION_ACCEPT_VOICE_CALL -> {
                OrbisCallNotificationHelper.cancelCallNotification(context)
                OrbisCallManager.acceptCall(withVideo = false)
                val incomingIntent = Intent(context, com.sha.orbis.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("extra_incoming_call", true)
                    putExtra("extra_call_id", intent.getStringExtra("extra_call_id"))
                }
                context.startActivity(incomingIntent)
            }
            ACTION_DECLINE_CALL -> {
                OrbisCallNotificationHelper.cancelCallNotification(context)
                OrbisCallManager.rejectCall()
            }
            ACTION_END_CALL -> {
                OrbisCallNotificationHelper.cancelCallNotification(context)
                OrbisCallManager.endCall()
            }
            ACTION_DISMISS_MISSED_CALL -> {
                val phone = intent.getStringExtra("extra_peer_phone") ?: ""
                if (phone.isNotBlank()) {
                    OrbisMissedCallManager.clearMissedCount(context, phone)
                }
            }
        }
    }
}

