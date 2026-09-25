package com.sha.orbis.notification

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log

/**
 * Gestionnaire central du cycle de vie applicatif et de la discussion activement consultée.
 *
 * Permet d'éviter les notifications intempestives (bannières in-app et alertes système)
 * lorsque l'utilisateur est déjà présent sur l'écran de discussion du correspondant.
 */
object ActiveConversationTracker {

    private const val TAG = "ActiveConvTracker"

    @Volatile
    var isAppInForeground: Boolean = false
        private set

    @Volatile
    var activeConversationId: String? = null
        private set

    @Volatile
    var activeRecipientPhone: String? = null
        private set

    /**
     * Définit la conversation activement ouverte à l'écran par l'utilisateur.
     */
    fun setActiveConversation(convId: String?, recipientPhone: String? = null) {
        activeConversationId = convId
        activeRecipientPhone = recipientPhone
        Log.d(TAG, "Conversation active définie: convId=$convId, phone=$recipientPhone")
    }

    /**
     * Réinitialise la conversation active si elle correspond à l'identifiant fourni (ou toutes si null).
     */
    fun clearActiveConversation(convId: String? = null) {
        if (convId == null || activeConversationId == convId) {
            Log.d(TAG, "Conversation active réinitialisée (précédent: $activeConversationId)")
            activeConversationId = null
            activeRecipientPhone = null
        }
    }

    /**
     * Vérifie si l'utilisateur est actuellement sur l'écran de la discussion spécifiée.
     * Si l'application est en arrière-plan, renvoie toujours false pour garantir que l'alerte système est émise.
     */
    fun isConversationActive(convId: String?, phone: String? = null): Boolean {
        if (!isAppInForeground) return false
        val current = activeConversationId ?: return false

        // 1. Correspondance exacte ou préfixe sur convId
        if (convId != null) {
            if (current.equals(convId, ignoreCase = true)) return true
            val normCurrent = current.removePrefix("conv_")
            val normConvId = convId.removePrefix("conv_")
            if (normCurrent.equals(normConvId, ignoreCase = true)) return true

            // Comparaison des 8 derniers chiffres si applicable
            val currentDigits = normCurrent.filter { it.isDigit() }
            val convDigits = normConvId.filter { it.isDigit() }
            if (currentDigits.length >= 8 && convDigits.length >= 8) {
                if (currentDigits.takeLast(8) == convDigits.takeLast(8)) return true
            }
        }

        // 2. Correspondance sur le numéro de téléphone ou npub du destinataire
        val currentPhone = activeRecipientPhone
        if (phone != null && currentPhone != null) {
            if (currentPhone.equals(phone, ignoreCase = true)) return true
            val digits1 = phone.filter { it.isDigit() }
            val digits2 = currentPhone.filter { it.isDigit() }
            if (digits1.length >= 8 && digits2.length >= 8 && digits1.takeLast(8) == digits2.takeLast(8)) {
                return true
            }
        }

        // 3. Correspondance croisée convId avec phone enregistré
        if (convId != null && currentPhone != null) {
            val convDigits = convId.removePrefix("conv_").filter { it.isDigit() }
            val phoneDigits = currentPhone.filter { it.isDigit() }
            if (convDigits.length >= 8 && phoneDigits.length >= 8 && convDigits.takeLast(8) == phoneDigits.takeLast(8)) {
                return true
            }
        }

        // 4. Correspondance croisée phone avec convId enregistré
        if (phone != null) {
            val currentConvDigits = current.removePrefix("conv_").filter { it.isDigit() }
            val phoneDigits = phone.filter { it.isDigit() }
            if (currentConvDigits.length >= 8 && phoneDigits.length >= 8 && currentConvDigits.takeLast(8) == phoneDigits.takeLast(8)) {
                return true
            }
        }

        return false
    }

    /**
     * Initialise l'écouteur de cycle de vie global des activités pour maintenir `isAppInForeground`.
     */
    fun init(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                isAppInForeground = (startedActivities > 0)
                Log.d(TAG, "Activity started: ${activity.localClassName}, foreground=$isAppInForeground")
            }
            override fun onActivityResumed(activity: Activity) {
                isAppInForeground = true
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                isAppInForeground = (startedActivities > 0)
                Log.d(TAG, "Activity stopped: ${activity.localClassName}, foreground=$isAppInForeground")
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
