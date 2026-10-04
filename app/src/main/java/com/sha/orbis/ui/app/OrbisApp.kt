package com.sha.orbis.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable

@Composable
fun OrbisApp(
    initialConvId: String? = null,
    initialPhone: String? = null,
    initialTitle: String? = null,
    initialPostId: String? = null,
    initialActionType: String? = null
) {
    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize())
}
