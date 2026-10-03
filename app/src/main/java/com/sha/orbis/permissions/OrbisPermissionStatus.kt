package com.sha.orbis.permissions

/**
 * OrbisPermissionStatus — État fin et unifié d'une autorisation dans le cycle de vie Android.
 */
sealed class OrbisPermissionStatus {
    /**
     * L'autorisation est accordée par l'utilisateur.
     */
    data object Granted : OrbisPermissionStatus()

    /**
     * L'autorisation n'est pas accordée, mais le système Android permet encore
     * d'afficher la demande native (shouldShowRationale indique si une explication préalable est recommandée).
     */
    data class Denied(val shouldShowRationale: Boolean) : OrbisPermissionStatus()

    /**
     * L'autorisation a été refusée définitivement (« Ne plus demander » ou double refus sur Android 11+).
     * La seule issue est de guider l'utilisateur vers les Paramètres de l'application.
     */
    data object PermanentlyDenied : OrbisPermissionStatus()

    /**
     * Non applicable sur cette version d'Android (ex: POST_NOTIFICATIONS sur Android < 13).
     */
    data object NotApplicable : OrbisPermissionStatus()
}
