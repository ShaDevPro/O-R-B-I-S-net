package com.sha.orbis.ui.backup

import androidx.compose.runtime.Composable
import com.sha.orbis.ui.security.SecurityScreen

@Composable
fun BackupPermissionsScreen(onRequestPermissions: () -> Unit = {}) {
    SecurityScreen()
}
