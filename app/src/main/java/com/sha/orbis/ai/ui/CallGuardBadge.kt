package com.sha.orbis.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.ai.guard.ThreatLevel

/**
 * Pastille compacte de réputation et de sécurité pour les appels dans le journal.
 * Respecte strictement l'i18n et les thèmes clairs/sombres.
 */
@Composable
fun CallGuardBadge(
    threatLevel: String?,
    trustScore: Int = 100,
    modifier: Modifier = Modifier,
    compact: Boolean = true
) {
    val level = try {
        if (!threatLevel.isNullOrBlank()) ThreatLevel.valueOf(threatLevel) else ThreatLevel.SAFE
    } catch (_: Exception) {
        ThreatLevel.SAFE
    }

    val (bg, border, content, icon, label) = when (level) {
        ThreatLevel.CRITICAL, ThreatLevel.HIGH_RISK -> Quintuple(
            Color(0xFFEF4444).copy(alpha = 0.15f),
            Color(0xFFEF4444).copy(alpha = 0.5f),
            Color(0xFFEF4444),
            Icons.Default.Warning,
            stringResource(R.string.call_guard_badge_suspicious)
        )
        ThreatLevel.SUSPICIOUS -> Quintuple(
            Color(0xFFF59E0B).copy(alpha = 0.15f),
            Color(0xFFF59E0B).copy(alpha = 0.5f),
            Color(0xFFF59E0B),
            Icons.Default.Shield,
            stringResource(R.string.call_guard_badge_unknown)
        )
        ThreatLevel.SAFE -> Quintuple(
            Color(0xFF0284C7).copy(alpha = 0.12f),
            Color(0xFF0284C7).copy(alpha = 0.4f),
            Color(0xFF0284C7),
            Icons.Default.VerifiedUser,
            stringResource(R.string.call_guard_badge_verified)
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.8.dp, border, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = if (compact) label else "$label ($trustScore%)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            softWrap = false
        )
    }
}

private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
