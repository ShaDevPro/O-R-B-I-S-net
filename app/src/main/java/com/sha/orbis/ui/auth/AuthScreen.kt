package com.sha.orbis.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable

@Composable
fun AuthScreen(
    onAuthenticate: () -> Unit,
    targetSimSlotIndex: Int = -1,
    onCancel: (() -> Unit)? = null
) {
    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize())
}
