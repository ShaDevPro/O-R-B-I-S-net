package com.sha.orbis.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.ai.guard.ThreatLevel
import com.sha.orbis.ai.guard.ThreatType

/**
 * Premium visual indicator badge for threat levels evaluated by ORBIS Guard-LLM.
 */
@Composable
fun OrbisThreatBadge(
    threatLevel: ThreatLevel,
    threatType: ThreatType = ThreatType.LEGITIMATE,
    confidenceScore: Float = 0.9f,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val (bgColor, borderColor, contentColor, icon, label) = when (threatLevel) {
        ThreatLevel.CRITICAL -> {
            val title = when (threatType) {
                ThreatType.SCAM_EMERGENCY -> "Arnaque Urgence"
                ThreatType.PHISHING_BANK -> "Phishing Bancaire"
                else -> "Arnaque Critique"
            }
            BadgeStyle(
                bg = Color(0x33E11D48),
                border = Color(0xFFE11D48),
                content = Color(0xFFFF4D6D),
                icon = Icons.Default.Shield,
                label = if (compact) "Danger" else "$title (${(confidenceScore * 100).toInt()}%)"
            )
        }
        ThreatLevel.HIGH_RISK -> {
            val title = when (threatType) {
                ThreatType.PHISHING_DELIVERY -> "Faux Colis"
                ThreatType.SCAM_CRYPTO_LOTTERY -> "Faux Gains"
                else -> "Risque Élevé"
            }
            BadgeStyle(
                bg = Color(0x33F97316),
                border = Color(0xFFF97316),
                content = Color(0xFFFB923C),
                icon = Icons.Default.Warning,
                label = if (compact) "Risque" else "$title (${(confidenceScore * 100).toInt()}%)"
            )
        }
        ThreatLevel.SUSPICIOUS -> {
            BadgeStyle(
                bg = Color(0x33EAB308),
                border = Color(0xFFEAB308),
                content = Color(0xFFFACC15),
                icon = Icons.Default.WarningAmber,
                label = if (compact) "Spam" else "Spam suspect (${(confidenceScore * 100).toInt()}%)"
            )
        }
        ThreatLevel.SAFE -> {
            if (threatType == ThreatType.OTP_SECURITY) {
                BadgeStyle(
                    bg = Color(0x333B82F6),
                    border = Color(0xFF3B82F6),
                    content = Color(0xFF60A5FA),
                    icon = Icons.Default.CheckCircle,
                    label = if (compact) "OTP" else "OTP Sécurisé"
                )
            } else {
                BadgeStyle(
                    bg = Color(0x2210B981),
                    border = Color(0xFF10B981),
                    content = Color(0xFF34D399),
                    icon = Icons.Default.CheckCircle,
                    label = if (compact) "Sûr" else "Vérifié Sûr"
                )
            }
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(0.8.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(if (compact) 11.dp else 13.dp)
        )
        Text(
            text = label,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

private data class BadgeStyle(
    val bg: Color,
    val border: Color,
    val content: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String
)
