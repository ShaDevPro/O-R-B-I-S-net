package com.sha.orbis.permissions

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Interface de contrôle pour déclencher une demande d'autorisation Orbis.
 */
class OrbisPermissionState internal constructor(
    private val permission: OrbisPermission,
    private val onTriggerLaunch: () -> Unit,
    val isGranted: () -> Boolean
) {
    fun launch() {
        onTriggerLaunch()
    }
}

/**
 * Hook Jetpack Compose universel et modulaire pour n'importe quelle autorisation d'OrbisNet.
 *
 * Gère de manière totalement transparente :
 *  1. La vérification préalable sans déclenchement intempestif
 *  2. La demande système native
 *  3. L'explication préalable / rationale
 *  4. La détection et le déblocage du "Refus Définitif" (redirection vers Paramètres système)
 *
 * @param permission Le type d'autorisation requis ([OrbisPermission])
 * @param onGranted Callback déclenché dès que l'autorisation est active
 * @param onDenied Callback optionnel déclenché en cas de refus
 */
@Composable
fun rememberOrbisPermissionLauncher(
    permission: OrbisPermission,
    onGranted: () -> Unit = {},
    onDenied: () -> Unit = {}
): OrbisPermissionState {
    val context = LocalContext.current
    val activity = context as? Activity

    var showDialog by remember { mutableStateOf(false) }
    var isPermanentlyDenied by remember { mutableStateOf(false) }

    val nativeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val missing = OrbisPermissionManager.getMissingPermissions(context, permission)
        val allGranted = missing.isEmpty() || results.all { it.value }

        if (allGranted) {
            showDialog = false
            onGranted()
        } else {
            activity?.let { act ->
                val status = OrbisPermissionManager.getStatus(act, permission)
                isPermanentlyDenied = (status == OrbisPermissionStatus.PermanentlyDenied)
                showDialog = true
            }
            onDenied()
        }
    }

    val triggerLaunch: () -> Unit = {
        if (OrbisPermissionManager.isGranted(context, permission)) {
            onGranted()
        } else if (permission == OrbisPermission.BATTERY_OPTIMIZATION) {
            OrbisPermissionManager.openBatteryOptimizationSettings(context)
        } else {
            val missing = OrbisPermissionManager.getMissingPermissions(context, permission)
            OrbisPermissionManager.markPermissionRequested(context, missing)

            if (activity != null) {
                val status = OrbisPermissionManager.getStatus(activity, permission)
                if (status == OrbisPermissionStatus.PermanentlyDenied) {
                    isPermanentlyDenied = true
                    showDialog = true
                } else {
                    nativeLauncher.launch(missing)
                }
            } else {
                nativeLauncher.launch(missing)
            }
        }
    }

    if (showDialog) {
        OrbisPermissionDialog(
            permission = permission,
            isPermanentlyDenied = isPermanentlyDenied,
            onGrantOrSettings = {
                showDialog = false
                if (isPermanentlyDenied) {
                    OrbisPermissionManager.openAppSettings(context)
                } else {
                    val missing = OrbisPermissionManager.getMissingPermissions(context, permission)
                    nativeLauncher.launch(missing)
                }
            },
            onDismiss = {
                showDialog = false
            }
        )
    }

    return remember(permission) {
        OrbisPermissionState(
            permission = permission,
            onTriggerLaunch = triggerLaunch,
            isGranted = { OrbisPermissionManager.isGranted(context, permission) }
        )
    }
}
