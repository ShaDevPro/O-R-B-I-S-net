package com.sha.orbis.permissions

import android.Manifest
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.sha.orbis.R

/**
 * OrbisPermission — Définition typée et universelle des besoins en autorisations d'OrbisNet.
 *
 * Isole les spécificités de chaque niveau d'API Android (Android 8.0 jusqu'à Android 15+)
 * et associe à chaque permission ses ressources i18n et son icône représentative.
 */
enum class OrbisPermission(
    val titleRes: Int,
    val descRes: Int,
    val deniedDescRes: Int,
    val icon: ImageVector
) {
    CALL_VOICE(
        titleRes = R.string.perm_call_voice_title,
        descRes = R.string.perm_call_voice_desc,
        deniedDescRes = R.string.perm_call_voice_denied_desc,
        icon = Icons.Default.Phone
    ) {
        override fun getManifestPermissions(): Array<String> {
            val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                list += Manifest.permission.BLUETOOTH_CONNECT
            }
            return list.toTypedArray()
        }
    },

    CALL_VIDEO(
        titleRes = R.string.perm_call_video_title,
        descRes = R.string.perm_call_video_desc,
        deniedDescRes = R.string.perm_call_video_denied_desc,
        icon = Icons.Default.Videocam
    ) {
        override fun getManifestPermissions(): Array<String> {
            val list = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                list += Manifest.permission.BLUETOOTH_CONNECT
            }
            return list.toTypedArray()
        }
    },

    RECORD_AUDIO(
        titleRes = R.string.perm_mic_title,
        descRes = R.string.perm_mic_desc,
        deniedDescRes = R.string.perm_mic_denied_desc,
        icon = Icons.Default.Mic
    ) {
        override fun getManifestPermissions(): Array<String> =
            arrayOf(Manifest.permission.RECORD_AUDIO)
    },

    CAMERA(
        titleRes = R.string.perm_camera_title,
        descRes = R.string.perm_camera_desc,
        deniedDescRes = R.string.perm_camera_denied_desc,
        icon = Icons.Default.CameraAlt
    ) {
        override fun getManifestPermissions(): Array<String> =
            arrayOf(Manifest.permission.CAMERA)
    },

    NOTIFICATIONS(
        titleRes = R.string.perm_notif_title,
        descRes = R.string.perm_notif_desc,
        deniedDescRes = R.string.perm_notif_denied_desc,
        icon = Icons.Default.Notifications
    ) {
        override fun getManifestPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray()
            }
    },

    CONTACTS(
        titleRes = R.string.perm_contacts_title,
        descRes = R.string.perm_contacts_desc,
        deniedDescRes = R.string.perm_contacts_denied_desc,
        icon = Icons.Default.Contacts
    ) {
        override fun getManifestPermissions(): Array<String> =
            arrayOf(Manifest.permission.READ_CONTACTS)
    },

    MEDIA_IMAGES(
        titleRes = R.string.perm_media_title,
        descRes = R.string.perm_media_desc,
        deniedDescRes = R.string.perm_media_denied_desc,
        icon = Icons.Default.Image
    ) {
        override fun getManifestPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                emptyArray()
            }
    },

    SIM_TELEPHONY(
        titleRes = R.string.perm_sim_title,
        descRes = R.string.perm_sim_desc,
        deniedDescRes = R.string.perm_sim_denied_desc,
        icon = Icons.Default.Phone
    ) {
        override fun getManifestPermissions(): Array<String> {
            val list = mutableListOf(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                list += Manifest.permission.READ_PHONE_NUMBERS
            }
            return list.toTypedArray()
        }
    },

    BATTERY_OPTIMIZATION(
        titleRes = R.string.perm_battery_title,
        descRes = R.string.perm_battery_desc,
        deniedDescRes = R.string.perm_battery_desc,
        icon = Icons.Default.Power
    ) {
        override fun getManifestPermissions(): Array<String> = emptyArray()
    };

    /**
     * Renvoie le tableau exact des permissions manifestes exigées pour la version courante d'Android.
     */
    abstract fun getManifestPermissions(): Array<String>
}
