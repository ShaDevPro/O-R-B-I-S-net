package com.sha.orbis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

object ThemeCompatibility {
    @Composable
    fun Apply(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}