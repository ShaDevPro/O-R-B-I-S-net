package com.sha.orbis.ui.theme

import androidx.compose.ui.graphics.Color

object OrbisColorPalette {
    // Ultra-Dark Minimalist Theme (ChatGPT / Linear / Apple Dark style)
    val DarkBackground = Color(0xFF09090B)       // Pure deep neutral black
    val DarkSurface = Color(0xFF121215)          // Primary card background
    val DarkSurfaceVariant = Color(0xFF18181B)   // Secondary card & chips
    val DarkSurfaceElevated = Color(0xFF222226)  // Modals & elevated panels
    val DarkTextPrimary = Color(0xFFF4F4F5)      // Crisp high-contrast white text
    val DarkTextSecondary = Color(0xFFA1A1AA)    // Muted zinc secondary text
    val DarkTextTertiary = Color(0xFF71717A)     // Subtle caption & timestamps
    val DarkBorder = Color(0xFF27272A)           // 1px discrete structural border
    val DarkBorderSubtle = Color(0xFF1F1F23)     // Soft separator lines
    val DarkAccent = Color(0xFFFFFFFF)           // Primary white action accent
    val DarkAccentMuted = Color(0xFF3F3F46)      // Muted action outline

    // Ultra-Clean Light Theme
    val LightBackground = Color(0xFFFFFFFF)      // Pure crisp white
    val LightSurface = Color(0xFFF9F9FA)         // Primary surface
    val LightSurfaceVariant = Color(0xFFF4F4F5)  // Secondary card & chips
    val LightSurfaceElevated = Color(0xFFFFFFFF) // Modals & sheets
    val LightTextPrimary = Color(0xFF09090B)     // Deep black text
    val LightTextSecondary = Color(0xFF52525B)   // Neutral dark zinc text
    val LightTextTertiary = Color(0xFFA1A1AA)    // Muted caption text
    val LightBorder = Color(0xFFE4E4E7)          // Discrete light border
    val LightBorderSubtle = Color(0xFFF1F1F4)    // Subtle list separators
    val LightAccent = Color(0xFF09090B)          // Deep black action accent
    val LightAccentMuted = Color(0xFFD4D4D8)     // Muted action outline

    // System Semantic Accents (Minimalist)
    val StatusActive = Color(0xFF22C55E)         // Green pulse indicator for active SIM
    val StatusActiveBg = Color(0x1A22C55E)       // 10% opacity green background
    val StatusWarning = Color(0xFFF59E0B)        // Amber alert
    val StatusError = Color(0xFFEF4444)          // Clean red error
}
