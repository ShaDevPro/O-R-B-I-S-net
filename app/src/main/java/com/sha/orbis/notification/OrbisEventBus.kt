package com.sha.orbis.notification

/**
 * Bus d'événements local pour la notification réactive des écrans de l'interface utilisateur
 * (rafraîchissement des conversations, synchronisation des messages entrants Nostr, etc.).
 */
object OrbisEventBus {
    const val ACTION_REFRESH_CONVERSATIONS = "com.sha.orbis.ACTION_REFRESH_CONVERSATIONS"
    const val EXTRA_CONV_ID = "extra_conv_id"
    const val EXTRA_MESSAGE_ID = "extra_message_id"

    // Alias de compatibilité ascendante pour les composants internes

    const val ACTION_ORBIS_SMS_RECEIVED = ACTION_REFRESH_CONVERSATIONS
    const val ACTION_ORBIS_STORY_RECEIVED = "com.sha.orbis.ACTION_ORBIS_STORY_RECEIVED"
    const val ACTION_ORBIS_POST_RECEIVED  = "com.sha.orbis.ACTION_ORBIS_POST_RECEIVED"
    const val ACTION_ORBIS_REACTION_RECEIVED = "com.sha.orbis.ACTION_ORBIS_REACTION_RECEIVED"
    const val ACTION_REFRESH_CALL_LOGS = "com.sha.orbis.ACTION_REFRESH_CALL_LOGS"
    const val ACTION_REFRESH_NOTIFICATIONS = "com.sha.orbis.ACTION_REFRESH_NOTIFICATIONS"
}
