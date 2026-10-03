package com.sha.orbis.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.sha.orbis.MainActivity
import com.sha.orbis.R
import com.sha.orbis.ui.components.AvatarManager
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.PrivateConversationRepository

object SmsNotificationHelper {

    const val CHANNEL_MESSAGES = "orbis_messages_channel"
    const val CHANNEL_INVITATIONS = "orbis_invitations_channel"
    const val CHANNEL_SOCIAL = "orbis_social_channel"

    private const val GROUP_ORBIS_MESSAGES = "com.sha.orbis.MESSAGES_GROUP"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            // 1. Messages Channel (High Importance - Sound, Vibration, Heads-up)
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages Privés Orbis (E2EE Internet)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Réception de messages privés chiffrés E2EE d'Orbis"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            // 2. Invitations Channel (High Importance)
            val invitesChannel = NotificationChannel(
                CHANNEL_INVITATIONS,
                "Invitations & Clés RSA",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Demandes de connexion sécurisée et d'échange de clés cryptographiques"
                enableVibration(true)
                setShowBadge(true)
            }

            // 3. Social / Timeline Channel (Default Importance)
            val socialChannel = NotificationChannel(
                CHANNEL_SOCIAL,
                "Réseau Social Orbis",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Activités, statuts et télémétrie"
                setShowBadge(false)
            }

            manager.createNotificationChannels(listOf(messagesChannel, invitesChannel, socialChannel))
        }
    }

    fun showIncomingMessageNotification(
        context: Context,
        sender: String,
        message: String,
        convId: String,
        senderPhone: String
    ) {
        // 0. Si l'utilisateur consulte activement cette conversation, aucune alerte ne doit être émise
        if (com.sha.orbis.notification.ActiveConversationTracker.isConversationActive(convId, senderPhone)) {
            android.util.Log.d("SmsNotificationHelper", "Notification ignorée : discussion active $convId à l'écran")
            return
        }

        ensureChannels(context)

        val notificationId = convId.hashCode()
        val repo = ConversationRepository(context)
        val contact = repo.loadContacts().find { it.phone == senderPhone || it.name.equals(sender, ignoreCase = true) }
        val displayName = contact?.name ?: sender
        val avatarPath = contact?.avatarPath
        val isPrivateConversation = PrivateConversationRepository(context).isLocked(convId)
        val privateNotificationTitle = context.getString(R.string.private_chat_notification_title)
        val privateNotificationBody = context.getString(R.string.private_chat_notification_body)

        // Déclencher la bannière in-app uniquement si l'application est au premier plan
        if (com.sha.orbis.notification.ActiveConversationTracker.isAppInForeground) {
            com.sha.orbis.notification.InAppNotificationManager.postNotification(
                title = if (isPrivateConversation) privateNotificationTitle else displayName,
                body = if (isPrivateConversation) privateNotificationBody else message,
                senderPhone = senderPhone,
                convId = convId,
                isPlainSms = false,
                avatarPath = if (isPrivateConversation) null else avatarPath
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) return
        }

        // 1. Main Intent (Open Chat)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.sha.orbis.ACTION_OPEN_CONVERSATION"
            data = android.net.Uri.parse("orbis://conv/$convId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_conv_id", convId)
            putExtra("extra_phone", senderPhone)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 2. Inline Direct Reply Action
        val replyAction = if (!isPrivateConversation) {
            val remoteInput = RemoteInput.Builder(DirectReplyReceiver.KEY_TEXT_REPLY)
                .setLabel("Répondre en chiffré...")
                .build()

            val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
                putExtra(DirectReplyReceiver.EXTRA_RECIPIENT_PHONE, senderPhone)
                putExtra(DirectReplyReceiver.EXTRA_CONV_ID, convId)
                putExtra(DirectReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                replyIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            NotificationCompat.Action.Builder(
                R.drawable.ic_logo,
                "Répondre",
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()
        } else {
            null
        }

        // 3. Build MessagingStyle
        val userPerson = Person.Builder()
            .setName("Moi")
            .setKey("me")
            .build()

        val senderPersonBuilder = Person.Builder()
            .setName(displayName)
            .setKey(senderPhone)

        if (!avatarPath.isNullOrBlank()) {
            val bitmap = AvatarManager.getAvatarBitmap(context, avatarPath)
            if (bitmap != null) {
                senderPersonBuilder.setIcon(IconCompat.createWithBitmap(bitmap))
            }
        }
        val senderPerson = senderPersonBuilder.build()

        fun formatMessagePreview(rawText: String): String {
            return when {
                rawText.startsWith("[AUDIO:") -> {
                    val parts = rawText.removePrefix("[AUDIO:").removeSuffix("]").split(":")
                    val dur = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    if (dur > 0) "🎙️ Note Vocale (${dur}s)" else "🎙️ Note Vocale"
                }
                rawText.startsWith("[GPS:") -> "📍 Coordonnées GPS"
                rawText.startsWith("[POST_SHARE:") -> "📰 Publication Partagée"
                rawText.startsWith("[VIDEO:") -> "🎥 Vidéo"
                else -> rawText
            }
        }

        val messagingStyle = if (!isPrivateConversation) {
            val style = NotificationCompat.MessagingStyle(userPerson)
                .setConversationTitle("🔒 $displayName")
            val recentMessages = repo.loadMessages(convId).takeLast(4)
            if (recentMessages.isNotEmpty()) {
                for (msg in recentMessages) {
                    val p = if (msg.senderId == "me") userPerson else senderPerson
                    style.addMessage(formatMessagePreview(msg.text), msg.timestamp, p)
                }
            } else {
                style.addMessage(formatMessagePreview(message), System.currentTimeMillis(), senderPerson)
            }
            style
        } else {
            null
        }

        val displayMessage = formatMessagePreview(message)
        val notificationTitle = if (isPrivateConversation) privateNotificationTitle else "🔒 $displayName"
        val notificationBody = if (isPrivateConversation) privateNotificationBody else displayMessage

        // 4. Lock Screen Privacy Public Version
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("Orbis GSM")
            .setContentText(if (isPrivateConversation) privateNotificationBody else "Nouveau message chiffré de $displayName")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val totalUnread = LauncherBadgeManager.calculateTotalUnread(context).coerceAtLeast(1)

        val isForeground = com.sha.orbis.notification.ActiveConversationTracker.isAppInForeground
        val notifPriority = if (isForeground) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH

        // 5. Main Notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setPriority(notifPriority)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setGroup(GROUP_ORBIS_MESSAGES)
            .setNumber(totalUnread)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .apply {
                messagingStyle?.let { setStyle(it) }
                replyAction?.let { addAction(it) }
                if (isForeground) {
                    setOnlyAlertOnce(true)
                }
            }
        val notification = notificationBuilder.build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        LauncherBadgeManager.updateBadge(context, totalUnread)
    }

    fun showInvitationNotification(
        context: Context,
        senderName: String,
        senderPhone: String
    ) {
        ensureChannels(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) return
        }

        val notificationId = ("invite_" + senderPhone).hashCode()
        val cleanDigits = senderPhone.filter { it.isDigit() }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_conv_id", "conv_$cleanDigits")
            putExtra("extra_phone", senderPhone)
            putExtra("extra_title", senderName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val totalUnread = LauncherBadgeManager.calculateTotalUnread(context).coerceAtLeast(1)

        com.sha.orbis.notification.InAppNotificationManager.postNotification(
            title = "🔑 Invitation de $senderName",
            body = "$senderName ($senderPhone) souhaite établir un canal chiffré.",
            senderPhone = senderPhone,
            convId = "conv_$cleanDigits",
            isPlainSms = false
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_INVITATIONS)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("🔑 Invitation Sécurisée Orbis")
            .setContentText("$senderName ($senderPhone) souhaite établir un canal chiffré.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$senderName ($senderPhone) vous a envoyé sa clé publique RSA pour dialoguer en E2EE sans Internet.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setNumber(totalUnread)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        LauncherBadgeManager.updateBadge(context, totalUnread)
    }

    fun showSocialNotification(
        context: Context,
        title: String,
        text: String,
        postId: String? = null,
        actionType: String? = null
    ) {
        ensureChannels(context)

        com.sha.orbis.notification.InAppNotificationManager.postNotification(
            title = title,
            body = text,
            isPlainSms = false
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) return
        }

        val notificationId = System.currentTimeMillis().toInt()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!postId.isNullOrBlank()) putExtra("extra_post_id", postId)
            if (!actionType.isNullOrBlank()) putExtra("extra_action_type", actionType)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SOCIAL)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun updateNotificationAfterReply(
        context: Context,
        notificationId: Int,
        recipientName: String,
        replyText: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) return
        }

        val repliedNotification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("🔒 $recipientName")
            .setContentText("Vous: $replyText (Chiffré & Envoyé)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(3000)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, repliedNotification)
    }

    fun cancelNotification(context: Context, key: String) {
        try {
            val notificationId = key.hashCode()
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (_: Exception) {}
    }
}
