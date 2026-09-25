package com.sha.orbis.ui.onboarding

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.permissions.PermissionGate
import com.sha.orbis.ui.i18n.LocaleManager
import com.sha.orbis.ui.theme.OrbisColorPalette

private enum class OnboardingSlideType {
    DECENTRALIZED_NOSTR,
    MILITARY_CRYPTO,
    SOVEREIGN_IDENTITY,
    PERMISSIONS
}

private data class OnboardingSlideData(
    val type: OnboardingSlideType,
    val badgeRes: Int,
    val titleRes: Int,
    val descRes: Int,
    val pill1Res: Int? = null,
    val pill2Res: Int? = null
)

@Composable
fun OnboardingFlow(onComplete: () -> Unit) {
    val context = LocalContext.current
    var currentSlideIndex by remember { mutableIntStateOf(0) }

    var hasPhonePermissions by remember { mutableStateOf(com.sha.orbis.permissions.OrbisPermissionManager.isGranted(context, com.sha.orbis.permissions.OrbisPermission.SIM_TELEPHONY)) }
    var hasNotifications by remember { mutableStateOf(com.sha.orbis.permissions.OrbisPermissionManager.isGranted(context, com.sha.orbis.permissions.OrbisPermission.NOTIFICATIONS)) }
    var hasContacts by remember { mutableStateOf(com.sha.orbis.permissions.OrbisPermissionManager.isGranted(context, com.sha.orbis.permissions.OrbisPermission.CONTACTS)) }
    var hasAudio by remember { mutableStateOf(com.sha.orbis.permissions.OrbisPermissionManager.isGranted(context, com.sha.orbis.permissions.OrbisPermission.RECORD_AUDIO)) }
    var hasBatteryOptim by remember { mutableStateOf(com.sha.orbis.permissions.OrbisPermissionManager.isBatteryOptimizationIgnored(context)) }
    var hasAttemptedPermissions by remember { mutableStateOf(false) }

    var showLanguageMenu by remember { mutableStateOf(false) }
    val currentLang = LocaleManager.currentLanguageState.value

    fun refreshPermissions() {
        hasPhonePermissions = com.sha.orbis.permissions.OrbisPermissionManager.isGranted(context, com.sha.orbis.permissions.OrbisPermission.SIM_TELEPHONY)
        hasNotifications = com.sha.orbis.permissions.OrbisPermissionManager.isGranted(context, com.sha.orbis.permissions.OrbisPermission.NOTIFICATIONS)
        hasContacts = com.sha.orbis.permissions.OrbisPermissionManager.isGranted(context, com.sha.orbis.permissions.OrbisPermission.CONTACTS)
        hasAudio = com.sha.orbis.permissions.OrbisPermissionManager.isGranted(context, com.sha.orbis.permissions.OrbisPermission.RECORD_AUDIO)
        hasBatteryOptim = com.sha.orbis.permissions.OrbisPermissionManager.isBatteryOptimizationIgnored(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasAttemptedPermissions = true
        refreshPermissions()
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    val slides = listOf(
        OnboardingSlideData(
            type = OnboardingSlideType.DECENTRALIZED_NOSTR,
            badgeRes = R.string.onboarding_slide1_badge,
            titleRes = R.string.onboarding_slide1_title,
            descRes = R.string.onboarding_slide1_desc,
            pill1Res = R.string.onboarding_slide1_pill1,
            pill2Res = R.string.onboarding_slide1_pill2
        ),
        OnboardingSlideData(
            type = OnboardingSlideType.MILITARY_CRYPTO,
            badgeRes = R.string.onboarding_slide2_badge,
            titleRes = R.string.onboarding_slide2_title,
            descRes = R.string.onboarding_slide2_desc,
            pill1Res = R.string.onboarding_slide2_pill1,
            pill2Res = R.string.onboarding_slide2_pill2
        ),
        OnboardingSlideData(
            type = OnboardingSlideType.SOVEREIGN_IDENTITY,
            badgeRes = R.string.onboarding_slide3_badge,
            titleRes = R.string.onboarding_slide3_title,
            descRes = R.string.onboarding_slide3_desc,
            pill1Res = R.string.onboarding_slide3_pill1,
            pill2Res = R.string.onboarding_slide3_pill2
        ),
        OnboardingSlideData(
            type = OnboardingSlideType.PERMISSIONS,
            badgeRes = R.string.onboarding_slide4_badge,
            titleRes = R.string.onboarding_slide4_title,
            descRes = R.string.onboarding_slide4_desc
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Top Bar: Skip Button + Language Selector Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Skip button (visible on slides 0, 1, 2)
            if (currentSlideIndex < slides.size - 1) {
                TextButton(
                    onClick = { currentSlideIndex = slides.size - 1 },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_btn_skip),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Language Selector Pill
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .clickable { showLanguageMenu = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when (currentLang) {
                                "fr" -> "🇫🇷 FR"
                                "ar" -> "🇩🇿 AR"
                                else -> "🇬🇧 EN"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                DropdownMenu(
                    expanded = showLanguageMenu,
                    onDismissRequest = { showLanguageMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("🇫🇷 Français") },
                        onClick = {
                            LocaleManager.setLanguage(context, "fr")
                            showLanguageMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🇬🇧 English") },
                        onClick = {
                            LocaleManager.setLanguage(context, "en")
                            showLanguageMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🇩🇿 العربية (RTL)") },
                        onClick = {
                            LocaleManager.setLanguage(context, "ar")
                            showLanguageMenu = false
                        }
                    )
                }
            }
        }

        // Center Content with Educational Animated Visuals (Scroll-protected on large font scale)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentSlideIndex,
                transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
                label = "onboarding_slider"
            ) { slideIndex ->
                val slide = slides[slideIndex]

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Interactive Hero Illustration per slide
                    when (slide.type) {
                        OnboardingSlideType.DECENTRALIZED_NOSTR -> NostrRelayHeroVisual()
                        OnboardingSlideType.MILITARY_CRYPTO -> MilitaryCryptoHeroVisual()
                        OnboardingSlideType.SOVEREIGN_IDENTITY -> DualSimHeroVisual()
                        OnboardingSlideType.PERMISSIONS -> PermissionsHeroVisual()
                    }

                    // Category Badge Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = stringResource(slide.badgeRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }

                    // Title
                    Text(
                        text = stringResource(slide.titleRes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp
                    )

                    // Educational Body Description
                    Text(
                        text = stringResource(slide.descRes),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Feature Key Highlights Pills (Slides 1, 2, 3)
                    if (slide.pill1Res != null && slide.pill2Res != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SlideFeaturePill(text = stringResource(slide.pill1Res))
                            SlideFeaturePill(text = stringResource(slide.pill2Res))
                        }
                    }

                    // Slide 4: Interactive System Readiness Box
                    if (slide.type == OnboardingSlideType.PERMISSIONS) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PermissionCheckItem(
                                        title = stringResource(R.string.perm_sim_title),
                                        isGranted = hasPhonePermissions,
                                        onClick = {
                                            if (!hasPhonePermissions) {
                                                permissionLauncher.launch(com.sha.orbis.permissions.OrbisPermission.SIM_TELEPHONY.getManifestPermissions())
                                            }
                                        }
                                    )
                                    PermissionCheckItem(
                                        title = stringResource(R.string.perm_notif_title),
                                        isGranted = hasNotifications,
                                        onClick = {
                                            if (!hasNotifications) {
                                                permissionLauncher.launch(com.sha.orbis.permissions.OrbisPermission.NOTIFICATIONS.getManifestPermissions())
                                            }
                                        }
                                    )
                                    PermissionCheckItem(
                                        title = stringResource(R.string.perm_mic_title),
                                        isGranted = hasAudio,
                                        onClick = {
                                            if (!hasAudio) {
                                                permissionLauncher.launch(com.sha.orbis.permissions.OrbisPermission.RECORD_AUDIO.getManifestPermissions())
                                            }
                                        }
                                    )
                                    PermissionCheckItem(
                                        title = stringResource(R.string.perm_contacts_title),
                                        isGranted = hasContacts,
                                        onClick = {
                                            if (!hasContacts) {
                                                permissionLauncher.launch(com.sha.orbis.permissions.OrbisPermission.CONTACTS.getManifestPermissions())
                                            }
                                        }
                                    )
                                    PermissionCheckItem(
                                        title = stringResource(R.string.perm_battery_title),
                                        isGranted = hasBatteryOptim,
                                        onClick = {
                                            com.sha.orbis.permissions.OrbisPermissionManager.openBatteryOptimizationSettings(context)
                                            hasBatteryOptim = com.sha.orbis.permissions.OrbisPermissionManager.isBatteryOptimizationIgnored(context)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Controls: Apple-style Capsules Indicator + Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pagination Capsule Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                slides.indices.forEach { idx ->
                    val isSelected = currentSlideIndex == idx
                    val width by animateDpAsState(targetValue = if (isSelected) 30.dp else 8.dp, label = "cap_w")
                    val color by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        label = "cap_c"
                    )

                    Box(
                        modifier = Modifier
                            .height(5.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // Primary Action Button: Standard Permissions First, then Start Orbis!
            val hasBasePermissions = hasPhonePermissions && hasNotifications && hasAudio
            Button(
                onClick = {
                    if (currentSlideIndex < slides.size - 1) {
                        currentSlideIndex++
                    } else {
                        if (!hasAttemptedPermissions && !hasBasePermissions) {
                            permissionLauncher.launch(com.sha.orbis.permissions.PermissionGate.requiredPermissions())
                        } else {
                            onComplete()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = if (currentSlideIndex < slides.size - 1) {
                            stringResource(R.string.onboarding_btn_next)
                        } else if (!hasAttemptedPermissions && !hasBasePermissions) {
                            stringResource(R.string.onboarding_btn_grant_permissions)
                        } else {
                            stringResource(R.string.onboarding_btn_enter_orbis)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Icon(
                        imageVector = if (currentSlideIndex < slides.size - 1 || hasAttemptedPermissions || hasBasePermissions) {
                            Icons.AutoMirrored.Filled.ArrowForward
                        } else {
                            Icons.Default.Check
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Option A (Progressive / Flexible) : Possibilité de passer cette étape si désiré
            if (currentSlideIndex == slides.size - 1 && (!hasAttemptedPermissions || !hasBasePermissions)) {
                androidx.compose.material3.TextButton(
                    onClick = onComplete,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.perm_dialog_btn_later),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Visual Illustrations
// -------------------------------------------------------------------------

@Composable
private fun NostrRelayHeroVisual() {
    val infiniteTransition = rememberInfiniteTransition(label = "nostr_anim")
    val pulseWave by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_a"
    )
    val innerPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "inner_pulse"
    )

    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = 38.dp.toPx()

            // Outer expanding relay wave
            drawCircle(
                color = primary.copy(alpha = waveAlpha),
                radius = baseRadius * pulseWave,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Secondary inner wave offset
            drawCircle(
                color = primary.copy(alpha = waveAlpha * 0.5f),
                radius = baseRadius * innerPulse,
                style = Stroke(width = 1.dp.toPx())
            )

            // Static background orbit
            drawCircle(
                color = outline,
                radius = baseRadius,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun MilitaryCryptoHeroVisual() {
    val infiniteTransition = rememberInfiniteTransition(label = "crypto_anim")
    val rot by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot"
    )

    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = center
            val radius = 40.dp.toPx()

            // Subtle base ring
            drawCircle(color = outline, radius = radius, style = Stroke(1.dp.toPx()))

            // Rotating cryptographic arc
            drawArc(
                color = primary,
                startAngle = rot,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun DualSimHeroVisual() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(110.dp)
    ) {
        // SIM 1 Card
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 72.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SimCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "SIM 1",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Bridge Icon
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }

        // SIM 2 Card
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 72.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SimCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "SIM 2",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionsHeroVisual() {
    Box(
        modifier = Modifier
            .size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun SlideFeaturePill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PermissionCheckItem(
    title: String,
    isGranted: Boolean,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    width = 1.dp,
                    color = if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = if (isGranted) stringResource(R.string.status_granted) else stringResource(R.string.status_pending),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
