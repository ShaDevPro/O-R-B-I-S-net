package com.sha.orbis.social

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.sha.orbis.R
import com.sha.orbis.media.LocationGpsHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.nostr.service.NostrSyncManager
import com.sha.orbis.storage.FriendCircleRepository
import com.sha.orbis.storage.SocialRepository
import java.util.UUID

/**
 * Modular Manager responsible for handling Family SOS Emergency alerts.
 * Ensures GPS permissions, prompts to enable GPS if disabled, retrieves
 * precise coordinates, and broadcasts the SOS post with live location.
 */
object FamilySosManager {

    /**
     * Checks if location permission is granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        return LocationGpsHelper.hasLocationPermission(context)
    }

    /**
     * Checks if GPS / Network location provider is enabled on the device.
     */
    fun isLocationEnabled(context: Context): Boolean {
        return LocationGpsHelper.isLocationEnabled(context)
    }

    /**
     * Opens Android System Location Settings screen so user can toggle GPS on.
     */
    fun openLocationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Triggers the full Family Emergency workflow:
     * 1. Checks family circle membership.
     * 2. If location permission is missing, invokes [onRequestLocationPermission].
     * 3. If GPS is turned off in settings, invokes [onNeedEnableGps].
     * 4. Acquires GPS coordinates asynchronously.
     * 5. Creates and publishes the SOS post with GPS payload and broadcasts it.
     */
    fun dispatchFamilyEmergency(
        context: Context,
        sessionManager: SessionManager,
        circleRepo: FriendCircleRepository,
        socialRepo: SocialRepository,
        onRequestLocationPermission: () -> Unit,
        onNeedEnableGps: () -> Unit,
        onAlertDispatched: (post: SocialPost) -> Unit
    ) {
        val familyPhones = circleRepo.getFamilyMembers()
        if (familyPhones.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.family_hub_empty_desc), Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Check Location Permission
        if (!hasLocationPermission(context)) {
            onRequestLocationPermission()
            return
        }

        // 2. Check if GPS hardware/service is turned ON
        if (!isLocationEnabled(context)) {
            onNeedEnableGps()
            return
        }

        // 3. Acquire live GPS location
        Toast.makeText(context, context.getString(R.string.family_sos_acquiring_gps), Toast.LENGTH_SHORT).show()

        LocationGpsHelper.getOfflineLocation(
            context = context,
            onLocationResult = { lat, lon, _ ->
                val gpsPayload = LocationGpsHelper.formatGpsPayload(lat, lon)
                val content = "${context.getString(R.string.family_sos_alert_title)}\n$gpsPayload"
                createAndBroadcastSos(
                    context = context,
                    sessionManager = sessionManager,
                    socialRepo = socialRepo,
                    content = content,
                    onAlertDispatched = onAlertDispatched
                )
            },
            onError = { _ ->
                // Fallback: Dispatch with clear warning that GPS could not be acquired
                val content = "${context.getString(R.string.family_sos_alert_title)}\n${context.getString(R.string.family_sos_no_location_note)}"
                createAndBroadcastSos(
                    context = context,
                    sessionManager = sessionManager,
                    socialRepo = socialRepo,
                    content = content,
                    onAlertDispatched = onAlertDispatched
                )
            }
        )
    }

    /**
     * Fallback emergency dispatch when user chooses to send without waiting for GPS fix.
     */
    fun dispatchEmergencyWithoutGps(
        context: Context,
        sessionManager: SessionManager,
        socialRepo: SocialRepository,
        onAlertDispatched: (post: SocialPost) -> Unit
    ) {
        val content = "${context.getString(R.string.family_sos_alert_title)}\n${context.getString(R.string.family_sos_no_location_note)}"
        createAndBroadcastSos(
            context = context,
            sessionManager = sessionManager,
            socialRepo = socialRepo,
            content = content,
            onAlertDispatched = onAlertDispatched
        )
    }

    private fun createAndBroadcastSos(
        context: Context,
        sessionManager: SessionManager,
        socialRepo: SocialRepository,
        content: String,
        onAlertDispatched: (post: SocialPost) -> Unit
    ) {
        val sosPost = SocialPost(
            id = "sos_${UUID.randomUUID().toString().take(8)}",
            authorPhone = sessionManager.userPhone,
            authorName = sessionManager.userName.ifBlank { "Moi" },
            authorAvatarPath = sessionManager.userAvatarPath,
            content = content,
            targetCircleId = "circle_family",
            timestamp = System.currentTimeMillis()
        )

        // Save locally
        socialRepo.addPost(sosPost)

        // Broadcast to Nostr relays
        try {
            NostrSyncManager.getInstance(context).publishPost(sosPost)
        } catch (_: Exception) {}

        // Notify caller and show feedback
        Toast.makeText(context, context.getString(R.string.family_sos_broadcast_sent), Toast.LENGTH_LONG).show()
        onAlertDispatched(sosPost)
    }
}
