package com.sha.orbis.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = OrbisColorPalette.DarkAccent,
    onPrimary = OrbisColorPalette.DarkBackground,
    primaryContainer = OrbisColorPalette.DarkSurfaceElevated,
    onPrimaryContainer = OrbisColorPalette.DarkTextPrimary,
    background = OrbisColorPalette.DarkBackground,
    onBackground = OrbisColorPalette.DarkTextPrimary,
    surface = OrbisColorPalette.DarkSurface,
    onSurface = OrbisColorPalette.DarkTextPrimary,
    surfaceVariant = OrbisColorPalette.DarkSurfaceVariant,
    onSurfaceVariant = OrbisColorPalette.DarkTextSecondary,
    outline = OrbisColorPalette.DarkBorder,
    outlineVariant = OrbisColorPalette.DarkBorderSubtle,
    error = OrbisColorPalette.StatusError
)

private val LightColorScheme = lightColorScheme(
    primary = OrbisColorPalette.LightAccent,
    onPrimary = OrbisColorPalette.LightBackground,
    primaryContainer = OrbisColorPalette.LightSurfaceElevated,
    onPrimaryContainer = OrbisColorPalette.LightTextPrimary,
    background = OrbisColorPalette.LightBackground,
    onBackground = OrbisColorPalette.LightTextPrimary,
    surface = OrbisColorPalette.LightSurface,
    onSurface = OrbisColorPalette.LightTextPrimary,
    surfaceVariant = OrbisColorPalette.LightSurfaceVariant,
    onSurfaceVariant = OrbisColorPalette.LightTextSecondary,
    outline = OrbisColorPalette.LightBorder,
    outlineVariant = OrbisColorPalette.LightBorderSubtle,
    error = OrbisColorPalette.StatusError
)

@Composable
fun OrbisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    // Clamping fontScale between 0.85f and 1.25f protects UI layouts while providing accessible text scaling
    val clampedDensity = androidx.compose.ui.unit.Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale.coerceIn(0.85f, 1.25f)
    )

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides clampedDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
