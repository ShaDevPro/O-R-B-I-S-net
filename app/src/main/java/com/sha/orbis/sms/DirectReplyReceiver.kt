package com.sha.orbis.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Message
import com.sha.orbis.storage.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DirectReplyReceiver : BroadcastReceiver() {

    companion object {
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val EXTRA_RECIPIENT_PHONE = "extra_recipient_phone"
        const val EXTRA_CONV_ID = "extra_conv_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInput.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim() ?: return

        if (replyText.isBlank()) return

        val recipientPhone = intent.getStringExtra(EXTRA_RECIPIENT_PHONE) ?: return
        val convId = intent.getStringExtra(EXTRA_CONV_ID) ?: "conv_${recipientPhone.filter { it.isDigit() }}"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val sessionManager = SessionManager(context)
        val convRepository = ConversationRepository(context)

        try {
            val msgId = "msg_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"

            // 1. Send via Nostr Relays (E2EE Kind 4) if sovereign address
            val isNostrAddress = recipientPhone.startsWith("npub1") || recipientPhone.length == 64
            if (isNostrAddress) {
                try {
                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                    nostrSync.sendDirectMessage(
                        recipientNpubOrHex = recipientPhone,
                        conversationId = convId,
                        text = replyText,
                        messageId = msgId
                    )

                } catch (e: Exception) {
                    android.util.Log.w("DirectReplyReceiver", "Failed to send direct reply via Nostr: ${e.message}")
                }
            }

            // 2. Save message locally
            val message = Message(
                id = msgId,
                conversationId = convId,
                senderId = "me",
                text = replyText,
                timestamp = System.currentTimeMillis(),
                encrypted = true,
                status = com.sha.orbis.model.MessageDeliveryStatus.SENT
            )
            convRepository.addMessage(convId, message)

            val updateIntent = Intent(com.sha.orbis.notification.OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                putExtra(com.sha.orbis.notification.OrbisEventBus.EXTRA_CONV_ID, convId)
                setPackage(context.packageName)
            }
            context.sendBroadcast(updateIntent)

            val contactName = convRepository.loadContacts().find { it.phone == recipientPhone }?.name ?: recipientPhone

            // 3. Confirm reply on notification
            SmsNotificationHelper.updateNotificationAfterReply(
                context = context,
                notificationId = notificationId,
                recipientName = contactName,
                replyText = replyText
            )
        } catch (e: Exception) {
            android.util.Log.e("DirectReplyReceiver", "Error in direct reply: ${e.message}")
        }
    }
}
