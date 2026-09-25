package com.sha.orbis.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.call.diagnostic.CallDiagnosticEngine
import com.sha.orbis.call.diagnostic.CallDiagnosticLogger
import com.sha.orbis.call.diagnostic.CallDiagnosticSettings
import com.sha.orbis.call.diagnostic.DiagnosticStatus
import com.sha.orbis.call.diagnostic.DiagnosticStepId
import com.sha.orbis.call.diagnostic.DiagnosticStepResult
import com.sha.orbis.call.diagnostic.LastCallDebugTracker
import com.sha.orbis.call.diagnostic.TelecomDiagnosticProbe
import com.sha.orbis.permissions.PermissionGate
import com.sha.orbis.security.BatteryOptimizationHelper
import com.sha.orbis.security.DeviceManufacturer
import com.sha.orbis.security.OEMDiagnosticHelper
import com.sha.orbis.ui.theme.OrbisColorPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbisCallDiagnosticScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val diagnosticState by CallDiagnosticEngine.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    var isBatteryExempted by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }

    val manufacturer = remember { OEMDiagnosticHelper.detectManufacturer() }
    val brandName = remember {
        val b = (Build.BRAND ?: "").replaceFirstChar { it.uppercase() }
        val m = (Build.MODEL ?: "")
        "$b $m"
    }

    // Gestionnaire de permissions pour l'étape 1
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        CallDiagnosticEngine.runFullDiagnostic(context)
    }

    // Lancer automatiquement le diagnostic au premier affichage s'il n'a pas encore tourné
    LaunchedEffect(Unit) {
        if (diagnosticState.stepResults.isEmpty() && !diagnosticState.isRunning) {
            CallDiagnosticEngine.runFullDiagnostic(context)
        }
    }

    // Arrêter la tonalité de test si l'écran est quitté
    DisposableEffect(Unit) {
        onDispose {
            CallDiagnosticEngine.stopAudioTestTone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.oem_diag_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0 && !diagnosticState.isRunning) {
                        IconButton(onClick = { CallDiagnosticEngine.runFullDiagnostic(context) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Navigation par onglets
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = stringResource(R.string.call_diag_tab_repair),
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = stringResource(R.string.call_diag_tab_guide),
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                )
            }

            // Contenu de l'onglet actif
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (selectedTab == 0) {
                    DiagnosticAndRepairTab(
                        context = context,
                        state = diagnosticState,
                        onRunAll = { CallDiagnosticEngine.runFullDiagnostic(context) },
                        onRequestPermissions = {
                            val perms = PermissionGate.requiredCallPermissions(context, isVideo = true)
                            if (perms.isNotEmpty()) {
                                permissionLauncher.launch(perms)
                            } else {
                                CallDiagnosticEngine.runFullDiagnostic(context)
                            }
                        },
                        onOpenSettings = { openAppSettings(context) },
                        onOpenPhoneAccounts = { TelecomDiagnosticProbe.openPhoneAccountSettings(context) },
                        onRepairStep = { step -> CallDiagnosticEngine.repairAndRecheckSingleStep(context, step) },
                        onToggleAudioTest = { CallDiagnosticEngine.playAudioTestTone(context) }
                    )
                } else {
                    OemGuideTab(
                        context = context,
                        manufacturer = manufacturer,
                        brandName = brandName,
                        isBatteryExempted = isBatteryExempted,
                        onUpdateBatteryStatus = {
                            isBatteryExempted = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                        }
                    )
                }
            }
        }
    }
}

// ── ONGLET 1 : DIAGNOSTIC & AUTO-RÉPARATION ─────────────────────────────────

@Composable
private fun DiagnosticAndRepairTab(
    context: Context,
    state: com.sha.orbis.call.diagnostic.CallDiagnosticState,
    onRunAll: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPhoneAccounts: () -> Unit,
    onRepairStep: (DiagnosticStepId) -> Unit,
    onToggleAudioTest: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Carte d'en-tête et jauge globale
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.call_diag_header_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.call_diag_header_desc),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Barre de progression si diagnostic en cours
                if (state.isRunning) {
                    val progress = if (state.totalSteps > 0) state.completedStepsCount.toFloat() / state.totalSteps.toFloat() else 0f
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                        Text(
                            text = stringResource(R.string.call_diag_btn_running),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (state.stepResults.isNotEmpty()) {
                    // Résumé du diagnostic terminé
                    val summaryBg = when {
                        state.isAllOk -> OrbisColorPalette.StatusActive.copy(alpha = 0.12f)
                        state.hasIssues -> OrbisColorPalette.StatusWarning.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    }
                    val summaryColor = when {
                        state.isAllOk -> OrbisColorPalette.StatusActive
                        state.hasIssues -> OrbisColorPalette.StatusWarning
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(summaryBg)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.isAllOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = summaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            val summaryText = when {
                                state.isAllOk -> stringResource(R.string.call_diag_summary_all_good)
                                state.repairedCount > 0 && state.failedCount == 0 -> stringResource(R.string.call_diag_summary_repaired, state.repairedCount)
                                else -> stringResource(R.string.call_diag_summary_issues, state.failedCount + state.warningCount)
                            }
                            Text(
                                text = summaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = summaryColor
                            )
                        }
                    }
                }

                // Bouton principal d'action
                Button(
                    onClick = onRunAll,
                    enabled = !state.isRunning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (state.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.call_diag_btn_running))
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.call_diag_btn_run_all), fontWeight = FontWeight.SemiBold)
                    }
                }

                // Bouton Réinitialiser réglages par défaut
                OutlinedButton(
                    onClick = {
                        CallDiagnosticSettings.resetAll(context)
                        onRunAll()
                    },
                    enabled = !state.isRunning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.call_diag_btn_reset_defaults),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Cartes détaillées pour chacune des 8 étapes
        DiagnosticStepId.entries.forEach { step ->
            val result = state.stepResults[step]
            val isCurrentRunning = state.currentRunningStep == step

            DiagnosticStepCard(
                step = step,
                result = result,
                isRunning = isCurrentRunning,
                isAudioTestPlaying = state.isAudioTestPlaying,
                onRequestPermissions = onRequestPermissions,
                onOpenSettings = onOpenSettings,
                onOpenPhoneAccounts = onOpenPhoneAccounts,
                onRepairStep = { onRepairStep(step) },
                onToggleAudioTest = onToggleAudioTest
            )
        }

        // ── Carte Résumé Dernier Appel Réel ──
        LastCallSummaryCard()

        // ── Carte Rapport de Débogage Développeur ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.call_diag_report_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.call_diag_report_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }

                val reportPath = remember { CallDiagnosticLogger.getReportAbsolutePath(context) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.call_diag_report_path_label),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = reportPath,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { CallDiagnosticLogger.copyReportToClipboard(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.call_diag_btn_copy_report), fontSize = 12.sp)
                    }
                    Button(
                        onClick = { CallDiagnosticLogger.shareReport(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.call_diag_btn_share_report), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStepCard(
    step: DiagnosticStepId,
    result: DiagnosticStepResult?,
    isRunning: Boolean,
    isAudioTestPlaying: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPhoneAccounts: () -> Unit,
    onRepairStep: () -> Unit,
    onToggleAudioTest: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status = when {
        isRunning -> DiagnosticStatus.RUNNING
        result != null -> result.status
        else -> DiagnosticStatus.IDLE
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Ligne d'en-tête de l'étape
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = step.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(step.titleRes),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (step.isVideoSpecific) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "Vidéo",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(step.descRes),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Badge de statut
                StatusBadge(status = status)
            }

            // Détails techniques
            if (result != null && result.detail.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = result.detail,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            // Boutons d'actions contextuels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton interactif pour tester le son
                if (step == DiagnosticStepId.AUDIO_ROUTING || step == DiagnosticStepId.MICROPHONE) {
                    OutlinedButton(
                        onClick = onToggleAudioTest,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isAudioTestPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isAudioTestPlaying) stringResource(R.string.call_diag_btn_stop_audio) else stringResource(R.string.call_diag_btn_test_audio),
                            fontSize = 12.sp
                        )
                    }
                }

                // Boutons d'auto-réparation
                when {
                    step == DiagnosticStepId.PERMISSIONS && status == DiagnosticStatus.FAILED -> {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.call_diag_btn_open_settings), fontSize = 12.sp)
                        }
                        Button(
                            onClick = onRequestPermissions,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.call_diag_btn_grant_perms), fontSize = 12.sp)
                        }
                    }

                    step == DiagnosticStepId.TELECOM && status == DiagnosticStatus.WARNING -> {
                        OutlinedButton(
                            onClick = onOpenPhoneAccounts,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.call_diag_btn_phone_accounts), fontSize = 12.sp)
                        }
                        Button(
                            onClick = onRepairStep,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.call_diag_btn_repair_step), fontSize = 12.sp)
                        }
                    }

                    step == DiagnosticStepId.BACKGROUND_DOZE && status == DiagnosticStatus.WARNING -> {
                        OutlinedButton(
                            onClick = {
                                com.sha.orbis.call.diagnostic.BackgroundDozeProbe.openFullScreenIntentSettings(context)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.call_diag_btn_fullscreen_intent), fontSize = 12.sp)
                        }
                        Button(
                            onClick = onRepairStep,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.call_diag_btn_repair_step), fontSize = 12.sp)
                        }
                    }

                    (status == DiagnosticStatus.FAILED || status == DiagnosticStatus.WARNING) && result?.canAutoRepair == true -> {
                        Button(
                            onClick = onRepairStep,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.call_diag_btn_repair_step), fontSize = 12.sp)
                        }
                    }

                    status == DiagnosticStatus.SUCCESS || status == DiagnosticStatus.REPAIRED -> {
                        OutlinedButton(
                            onClick = onRepairStep,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.call_diag_btn_retest_step), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DiagnosticStatus) {
    val (labelRes, bg, fg, icon) = when (status) {
        DiagnosticStatus.IDLE -> Quadruple(
            R.string.call_diag_status_idle,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            null
        )
        DiagnosticStatus.RUNNING -> Quadruple(
            R.string.call_diag_status_running,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.primary,
            null
        )
        DiagnosticStatus.SUCCESS -> Quadruple(
            R.string.call_diag_status_success,
            OrbisColorPalette.StatusActive.copy(alpha = 0.15f),
            OrbisColorPalette.StatusActive,
            Icons.Default.CheckCircle
        )
        DiagnosticStatus.REPAIRED -> Quadruple(
            R.string.call_diag_status_repaired,
            Color(0xFF00B4D8).copy(alpha = 0.18f),
            Color(0xFF0096C7),
            Icons.Default.Build
        )
        DiagnosticStatus.WARNING -> Quadruple(
            R.string.call_diag_status_warning,
            OrbisColorPalette.StatusWarning.copy(alpha = 0.15f),
            OrbisColorPalette.StatusWarning,
            Icons.Default.Warning
        )
        DiagnosticStatus.FAILED -> Quadruple(
            R.string.call_diag_status_failed,
            MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.error,
            Icons.Default.Error
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (status == DiagnosticStatus.RUNNING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = fg
                )
                Spacer(modifier = Modifier.width(5.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = stringResource(labelRes),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = fg
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// ── ONGLET 2 : GUIDE CONSTRUCTEUR & BATTERIE (EXISTANT) ─────────────────────

@Composable
private fun OemGuideTab(
    context: Context,
    manufacturer: DeviceManufacturer,
    brandName: String,
    isBatteryExempted: Boolean,
    onUpdateBatteryStatus: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // En-tête appareil
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.oem_diag_device_detected, brandName),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.oem_diag_subtitle),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Statut de la batterie
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (isBatteryExempted) OrbisColorPalette.StatusActive else OrbisColorPalette.StatusWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.oem_diag_battery_status),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    val badgeText = if (isBatteryExempted) stringResource(R.string.battery_opt_active) else stringResource(R.string.battery_opt_inactive)
                    val badgeBg = if (isBatteryExempted) OrbisColorPalette.StatusActive.copy(alpha = 0.15f) else OrbisColorPalette.StatusWarning.copy(alpha = 0.15f)
                    val badgeColor = if (isBatteryExempted) OrbisColorPalette.StatusActive else OrbisColorPalette.StatusWarning

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                if (!isBatteryExempted) {
                    Button(
                        onClick = {
                            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                            onUpdateBatteryStatus()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.oem_diag_btn_open_battery))
                    }
                }
            }
        }

        // Guide constructeur
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val mfrName = when (manufacturer) {
                    DeviceManufacturer.VIVO -> "Vivo / iQOO"
                    DeviceManufacturer.HONOR -> "Honor (MagicUI)"
                    DeviceManufacturer.HUAWEI -> "Huawei (EMUI)"
                    DeviceManufacturer.XIAOMI -> "Xiaomi / Redmi / Poco"
                    DeviceManufacturer.SAMSUNG -> "Samsung (One UI)"
                    DeviceManufacturer.OPPO_REALME -> "Oppo / Realme"
                    DeviceManufacturer.GENERIC -> brandName
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.oem_diag_guide_title, mfrName),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                when (manufacturer) {
                    DeviceManufacturer.VIVO -> {
                        DiagnosticStepItem(text = stringResource(R.string.oem_diag_step_vivo_1))
                        DiagnosticStepItem(text = stringResource(R.string.oem_diag_step_vivo_2))
                        DiagnosticStepItem(text = stringResource(R.string.oem_diag_step_vivo_3))
                        DiagnosticStepItem(text = stringResource(R.string.oem_diag_step_vivo_4))
                    }
                    DeviceManufacturer.HONOR, DeviceManufacturer.HUAWEI -> {
                        DiagnosticStepItem(text = stringResource(R.string.oem_diag_step_honor_1))
                        DiagnosticStepItem(text = stringResource(R.string.oem_diag_step_honor_2))
                    }
                    DeviceManufacturer.XIAOMI -> {
                        DiagnosticStepItem(text = stringResource(R.string.oem_diag_step_xiaomi_1))
                        DiagnosticStepItem(text = stringResource(R.string.oem_diag_step_xiaomi_2))
                    }
                    DeviceManufacturer.SAMSUNG -> {
                        DiagnosticStepItem(text = stringResource(R.string.oem_diag_step_samsung_1))
                    }
                    else -> {
                        DiagnosticStepItem(text = stringResource(R.string.oem_diag_step_generic_1))
                        DiagnosticStepItem(text = stringResource(R.string.oem_diag_step_generic_2))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        OEMDiagnosticHelper.openOEMAutostartSettings(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.oem_diag_btn_open_oem))
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStepItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp
        )
    }
}

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Throwable) {}
}

@Composable
private fun LastCallSummaryCard() {
    val callId = LastCallDebugTracker.callId

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.call_diag_last_call_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.call_diag_last_call_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }

            if (callId == null) {
                Text(
                    text = stringResource(R.string.call_diag_last_call_empty),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                val isSuccess = LastCallDebugTracker.everConnected ||
                        LastCallDebugTracker.remoteAudioTrackReceived ||
                        LastCallDebugTracker.lastIceState.equals("CONNECTED", ignoreCase = true) ||
                        LastCallDebugTracker.lastIceState.equals("COMPLETED", ignoreCase = true)
                val isFailed = LastCallDebugTracker.lastIceState.equals("FAILED", ignoreCase = true)

                val badgeColor = when {
                    isSuccess -> OrbisColorPalette.StatusActive
                    isFailed -> OrbisColorPalette.StatusError
                    else -> OrbisColorPalette.StatusWarning
                }

                val badgeText = when {
                    isSuccess -> "CONNECTÉ ✓"
                    isFailed -> "ÉCHEC ICE"
                    else -> "ICE: ${LastCallDebugTracker.lastIceState}"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val callTypeStr = "${if (LastCallDebugTracker.isVideo) "Vidéo" else "Audio"} • ${LastCallDebugTracker.direction}"
                    Text(
                        text = callTypeStr,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                if (LastCallDebugTracker.peerName.isNotEmpty() || LastCallDebugTracker.peerPhone.isNotEmpty()) {
                    Text(
                        text = "Correspondant : ${LastCallDebugTracker.peerName} (${LastCallDebugTracker.peerPhone})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Statut : ${LastCallDebugTracker.endReason}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val offerCodecs = LastCallDebugTracker.sdpOfferCodecs.ifEmpty { "N/A" }
                    val answerCodecs = LastCallDebugTracker.sdpAnswerCodecs.ifEmpty { "N/A" }

                    Text(
                        text = "Codecs SDP : Offer=[$offerCodecs] ➔ Answer=[$answerCodecs]",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Candidats ICE : Local=${LastCallDebugTracker.localIceCandidatesCount} | Reçus=${LastCallDebugTracker.remoteIceCandidatesCount}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Flux distants : Audio=${if (LastCallDebugTracker.remoteAudioTrackReceived) "Reçu ✓" else "Non reçu ✗"}" +
                                if (LastCallDebugTracker.isVideo) " | Vidéo=${if (LastCallDebugTracker.remoteVideoTrackReceived) "Reçue ✓" else "Non reçue ✗"}" else "",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (LastCallDebugTracker.remoteAudioTrackReceived) OrbisColorPalette.StatusActive else OrbisColorPalette.StatusWarning
                    )

                    if (LastCallDebugTracker.audioRecordError != null) {
                        Text(
                            text = "Micro (AudioRecord) : ${LastCallDebugTracker.audioRecordError}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbisColorPalette.StatusError
                        )
                    }

                    if (LastCallDebugTracker.audioTrackError != null) {
                        Text(
                            text = "Écouteur (AudioTrack) : ${LastCallDebugTracker.audioTrackError}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbisColorPalette.StatusError
                        )
                    }
                }
            }
        }
    }
}

