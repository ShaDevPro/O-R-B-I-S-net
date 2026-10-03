package com.sha.orbis.ui.social

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.ui.components.OrbisAvatar

/**
 * Palette de flamme vivante OrbisNet : rouge incandescent, orange feu, ambre solaire et rubis.
 */
val FireFlameColors = listOf(
    Color(0xFFFF2A00), // Rouge incandescent
    Color(0xFFFF7A00), // Orange flamme vif
    Color(0xFFFFD000), // Or solaire étincelant
    Color(0xFFFF0055), // Rubis fuchsia ardent
    Color(0xFFFF2A00)  // Boucle
)

val FireBadgeGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFF3D00),
        Color(0xFFFF9100)
    )
)

/**
 * Pastille de compteur de stories cumulées avec centrage optique parfait du chiffre.
 * Utilise PlatformTextStyle(includeFontPadding = false) et lineHeight = fontSize pour éliminer tout décalage vertical.
 */
@Composable
fun StoryCountBadge(
    count: Int,
    isUnseen: Boolean = true,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (isUnseen) Brush.linearGradient(
                    listOf(Color(0xFFFF2A00), Color(0xFFFF5E00))
                ) else Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondary)
                )
            )
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val fontSize = if (count > 99) 7.5.sp else if (count > 9) 8.5.sp else 10.sp
        Text(
            text = "$count",
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                    includeFontPadding = false
                ),
                lineHeight = fontSize
            ),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * Anneau et badge de story animés avec flamme de feu ("Petite flamme de feu").
 * Rend l'arrivée d'une nouvelle story ultra-visible, vivante et prestigieuse, tout en restant fluide et élégante.
 */
@Composable
fun StoryFireRing(
    modifier: Modifier = Modifier,
    hasStory: Boolean = true,
    hasUnseenStory: Boolean = true,
    ringSize: Dp = 62.dp,
    avatarSize: Dp = 52.dp,
    storyCount: Int = 1,
    showFlameBadge: Boolean = true,
    flameBadgeAlignment: Alignment = Alignment.BottomEnd,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (!hasStory) {
        // Pas de story : rendu simple direct sans anneau
        Box(
            modifier = modifier.size(avatarSize),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else Modifier

    val infiniteTransition = rememberInfiniteTransition(label = "story_fire_transition")

    // 1. Rotation continue et fluide du gradient de flamme
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fire_rotation"
    )

    // 2. Respiration / pulsation subtile de la chaleur du feu (1.0f -> 1.05f)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fire_pulse"
    )

    // 3. Danse / vacillement vertical de la petite flamme (-1.2dp -> +1.0dp)
    val flameBob by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame_bob"
    )

    // 4. Léger battement d'intensité de la flamme (0.95f -> 1.10f)
    val flameScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame_scale"
    )

    Box(
        modifier = modifier
            .size(ringSize)
            .then(clickableModifier),
        contentAlignment = Alignment.Center
    ) {
        // --- ANNEAU EXTÉRIEUR DE LA STORY ---
        if (hasUnseenStory) {
            // Anneau feu animé en rotation avec dégradé sweep flamboyant
            Box(
                modifier = Modifier
                    .size(ringSize)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .rotate(rotationAngle)
                    .background(Brush.sweepGradient(FireFlameColors))
            )
        } else {
            // Déjà vue : anneau discret sobre
            Box(
                modifier = Modifier
                    .size(ringSize - 2.dp)
                    .clip(CircleShape)
                    .border(1.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), CircleShape)
            )
        }

        // --- GAP DE DÉMARCATION ET CONTENEUR AVATAR ---
        Box(
            modifier = Modifier
                .size(avatarSize + 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }

        // --- PETITE FLAMME DE FEU ANIMÉE ("🔥") ---
        if (hasUnseenStory && showFlameBadge) {
            val badgeSize = if (ringSize >= 50.dp) 20.dp else 16.dp
            val flameFontSize = if (ringSize >= 50.dp) 11.sp else 9.sp
            val xOffset = if (flameBadgeAlignment == Alignment.BottomStart) (-1).dp else 1.dp

            Box(
                modifier = Modifier
                    .align(flameBadgeAlignment)
                    .offset(x = xOffset, y = (flameBob).dp)
                    .scale(flameScale)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(FireBadgeGradient)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔥",
                    fontSize = flameFontSize,
                    lineHeight = flameFontSize,
                    modifier = Modifier.padding(bottom = 0.5.dp)
                )
            }
        }

        // --- BADGE DE CUMUL MULTI-STORIES (ex: 2, 3) OPTIQUEMENT CENTRÉ ---
        if (storyCount > 1) {
            val countBadgeSize = if (ringSize >= 50.dp) 18.dp else 15.dp
            StoryCountBadge(
                count = storyCount,
                isUnseen = hasUnseenStory,
                size = countBadgeSize,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-1).dp)
            )
        }
    }
}

/**
 * Avatar universel connecté au système de story avec cercle de flamme de feu.
 */
@Composable
fun StoryFireAvatar(
    avatarPath: String?,
    name: String,
    size: Dp = 52.dp,
    hasStory: Boolean = false,
    hasUnseenStory: Boolean = false,
    storyCount: Int = 1,
    showFlameBadge: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!hasStory) {
        OrbisAvatar(
            avatarPath = avatarPath,
            name = name,
            size = size,
            modifier = modifier,
            onClick = onClick
        )
    } else {
        val ringSize = size + 10.dp
        StoryFireRing(
            hasStory = true,
            hasUnseenStory = hasUnseenStory,
            ringSize = ringSize,
            avatarSize = size,
            storyCount = storyCount,
            showFlameBadge = showFlameBadge,
            onClick = onClick,
            modifier = modifier
        ) {
            OrbisAvatar(
                avatarPath = avatarPath,
                name = name,
                size = size,
                onClick = null // Géré par StoryFireRing
            )
        }
    }
}
