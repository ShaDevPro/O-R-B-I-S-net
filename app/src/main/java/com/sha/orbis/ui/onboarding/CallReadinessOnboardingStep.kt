package com.sha.orbis.ui.onboarding

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsPhone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sha.orbis.R
import com.sha.orbis.ui.theme.OrbisColorPalette

/**
 * Écran d'onboarding dédié à la préparation des appels et de l'arrière-plan.
 * Rend l'activation des deux autorisations vitales (Batterie + Réveil lockscreen)
 * évidente, rassurante et automatique dès le premier lancement.
 */
@Composable
fun CallReadinessOnboardingStep(
    onComplete: () -> Unit,
    onSkip: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isBatteryExempt by remember {
        mutableStateOf(CallReadinessHelper.isBatteryExempt(context))
    }
    var isScreenWakeGranted by remember {
        mutableStateOf(CallReadinessHelper.isScreenWakeGranted(context))
    }
    val isOemRequired = remember {
        CallReadinessHelper.isOemSpecificRequired()
    }
    val mfrDisplayName = remember {
        CallReadinessHelper.getManufacturerDisplayName()
    }

    // Réévaluation automatique en direct dès que l'utilisateur revient des réglages système
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryExempt = CallReadinessHelper.isBatteryExempt(context)
                isScreenWakeGranted = CallReadinessHelper.isScreenWakeGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isAllReady = isBatteryExempt && isScreenWakeGranted

    val primaryButtonBg by animateColorAsState(
        targetValue = if (isAllReady) OrbisColorPalette.StatusActive else MaterialTheme.colorScheme.primary,
        animationSpec = tween(400),
        label = "btn_bg_anim"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Logo & Badge de sécurité
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Titre & Sous-titre rassurants
            Text(
                text = stringResource(R.string.onboarding_call_setup_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_call_setup_desc),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Carte 1 : Exemption de batterie (Doze mode)
            ReadinessPermissionCard(
                icon = Icons.Default.BatteryChargingFull,
                title = stringResource(R.string.onboarding_battery_title),
                description = stringResource(R.string.onboarding_battery_desc),
                isGranted = isBatteryExempt,
                actionLabel = stringResource(R.string.onboarding_btn_enable),
                onActionClick = {
                    Toast.makeText(context, context.getString(R.string.onboarding_hint_battery), Toast.LENGTH_SHORT).show()
                    CallReadinessHelper.requestBatteryExemption(context)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Carte 2 : Allumer l'écran / Réveil lockscreen
            ReadinessPermissionCard(
                icon = Icons.Default.PhoneInTalk,
                title = stringResource(R.string.onboarding_lockscreen_title),
                description = stringResource(R.string.onboarding_lockscreen_desc),
                isGranted = isScreenWakeGranted,
                actionLabel = stringResource(R.string.onboarding_btn_enable),
                onActionClick = {
                    Toast.makeText(context, context.getString(R.string.onboarding_hint_lockscreen), Toast.LENGTH_SHORT).show()
                    CallReadinessHelper.requestScreenWake(context)
                }
            )

            // Carte 3 (Optionnelle) : Constructeur spécifique (Xiaomi, Vivo, Huawei, etc.)
            if (isOemRequired) {
                Spacer(modifier = Modifier.height(14.dp))

                ReadinessPermissionCard(
                    icon = Icons.Default.SettingsPhone,
                    title = stringResource(R.string.onboarding_oem_title, mfrDisplayName),
                    description = stringResource(R.string.onboarding_oem_desc),
                    isGranted = false,
                    isRecommendedOnly = true,
                    actionLabel = stringResource(R.string.onboarding_btn_check),
                    onActionClick = {
                        CallReadinessHelper.requestOemAutostart(context)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section Boutons de validation en bas
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryButtonBg,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isAllReady) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(R.string.onboarding_btn_finish),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isAllReady) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (onSkip != null) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_call_setup_skip),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Carte modulaire affichant l'état et l'action d'une permission vitale.
 */
@Composable
private fun ReadinessPermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onActionClick: () -> Unit,
    isRecommendedOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardBorderColor = when {
        isGranted -> OrbisColorPalette.StatusActive.copy(alpha = 0.5f)
        isRecommendedOnly -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        else -> OrbisColorPalette.StatusWarning.copy(alpha = 0.6f)
    }

    val iconBgColor = when {
        isGranted -> OrbisColorPalette.StatusActive.copy(alpha = 0.12f)
        isRecommendedOnly -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else -> OrbisColorPalette.StatusWarning.copy(alpha = 0.12f)
    }

    val iconTint = when {
        isGranted -> OrbisColorPalette.StatusActive
        isRecommendedOnly -> MaterialTheme.colorScheme.primary
        else -> OrbisColorPalette.StatusWarning
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.2.dp, cardBorderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(iconBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Badge d'état
                val badgeText = when {
                    isGranted -> stringResource(R.string.onboarding_status_active)
                    isRecommendedOnly -> stringResource(R.string.onboarding_status_recommended)
                    else -> stringResource(R.string.onboarding_status_needed)
                }
                val badgeColor = when {
                    isGranted -> OrbisColorPalette.StatusActive
                    isRecommendedOnly -> MaterialTheme.colorScheme.primary
                    else -> OrbisColorPalette.StatusWarning
                }
                val badgeBg = badgeColor.copy(alpha = 0.12f)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isGranted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isRecommendedOnly) {
                        OutlinedButton(
                            onClick = onActionClick,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = actionLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Button(
                            onClick = onActionClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OrbisColorPalette.StatusWarning,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = actionLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
