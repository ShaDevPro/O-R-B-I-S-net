package com.sha.orbis.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * PermissionGate — Façade de compatibilité rétroactive déléguant à OrbisPermissionManager.
 */
object PermissionGate {

    /**
     * Neutralisé : OrbisNet fonctionne à 100% sur réseau Internet/Wi-Fi (Nostr & WebRTC).
     * Aucune autorisation SMS n'est requise.
     */
    fun hasSmsPermissions(context: Context): Boolean = false

    fun hasNotificationPermission(context: Context): Boolean =
        OrbisPermissionManager.isGranted(context, OrbisPermission.NOTIFICATIONS)

    fun hasContactsPermission(context: Context): Boolean =
        OrbisPermissionManager.isGranted(context, OrbisPermission.CONTACTS)

    fun hasPhoneStatePermission(context: Context): Boolean =
        OrbisPermissionManager.isGranted(context, OrbisPermission.SIM_TELEPHONY)

    fun hasAudioPermission(context: Context): Boolean =
        OrbisPermissionManager.isGranted(context, OrbisPermission.RECORD_AUDIO)

    fun hasCameraPermission(context: Context): Boolean =
        OrbisPermissionManager.isGranted(context, OrbisPermission.CAMERA)

    fun hasCallPermissions(context: Context, isVideo: Boolean): Boolean =
        OrbisPermissionManager.isGranted(context, if (isVideo) OrbisPermission.CALL_VIDEO else OrbisPermission.CALL_VOICE)

    fun requiredCallPermissions(context: Context, isVideo: Boolean): Array<String> =
        OrbisPermissionManager.getMissingPermissions(context, if (isVideo) OrbisPermission.CALL_VIDEO else OrbisPermission.CALL_VOICE)

    fun hasStoragePermission(context: Context): Boolean =
        OrbisPermissionManager.isGranted(context, OrbisPermission.MEDIA_IMAGES)

    fun hasRequiredPermissions(context: Context): Boolean =
        hasPhoneStatePermission(context) && hasNotificationPermission(context) && hasAudioPermission(context)

    /**
     * Liste unifiée et sécurisée des permissions indispensables pour l'initialisation d'OrbisNet.
     * N'exige plus de permissions SMS superflues pour satisfaire aux exigences strictes du Google Play Store.
     */
    fun requiredPermissions(): Array<String> {
        val essential = OrbisPermissionManager.getEssentialOnboardingPermissions()
        return essential.flatMap { it.getManifestPermissions().toList() }.distinct().toTypedArray()
    }
}
