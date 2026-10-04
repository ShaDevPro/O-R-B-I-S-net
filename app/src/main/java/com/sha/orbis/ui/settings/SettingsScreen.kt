package com.sha.orbis.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import com.sha.orbis.social.UserSocialRole

@Composable
fun SettingsScreen(
    onOpenAccountSwitcher: (() -> Unit)? = null,
    onOpenAdminConsole: (() -> Unit)? = null,
    onOpenProfile: (() -> Unit)? = null,
    onOpenWall: ((phone: String, pseudo: String, avatar: String?, role: UserSocialRole) -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize())
}
