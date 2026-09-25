package com.sha.orbis.ui.permissions

import androidx.compose.runtime.Composable
import com.sha.orbis.ui.onboarding.OnboardingFlow

@Composable
fun PermissionScreen(onRequestPermissions: () -> Unit = {}) {
    OnboardingFlow(onComplete = onRequestPermissions)
}
