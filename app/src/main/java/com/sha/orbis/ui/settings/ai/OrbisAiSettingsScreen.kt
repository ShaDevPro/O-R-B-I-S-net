package com.sha.orbis.ui.settings.ai

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.ai.benchmark.OrbisAiBenchmark
import com.sha.orbis.ai.core.OrbisAiPreferences
import com.sha.orbis.ui.components.OrbisTopHeader
import com.sha.orbis.ui.theme.OrbisColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Premium settings screen for ORBIS On-Device AI LLMs.
 * Features:
 * - Individual and Master toggle switches (Disabled by default upon installation).
 * - Real-time live on-device benchmark for each engine and combined hardware execution.
 * - Millisecond latency, throughput, test accuracy and RAM footprint telemetry.
 */
@Composable
fun OrbisAiSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiPrefs = remember { OrbisAiPreferences(context) }

    var isGuardActive by remember { mutableStateOf(aiPrefs.isGuardEnabled) }
    var isReplyActive by remember { mutableStateOf(aiPrefs.isReplyEnabled) }
    var isDrivingActive by remember { mutableStateOf(aiPrefs.isDrivingAutoDeclineEnabled) }
    var isDndActive by remember { mutableStateOf(aiPrefs.isDndAutoDeclineEnabled) }
    var isSmartRecallActive by remember { mutableStateOf(aiPrefs.isSmartRecallEnabled) }
    var isSilentBurstActive by remember { mutableStateOf(aiPrefs.isSilentBurstShieldEnabled) }

    val isMasterActive = isGuardActive && isReplyActive

    // Benchmark State
    var selectedBenchmarkTab by remember { mutableIntStateOf(0) } // 0: Guard, 1: Reply, 2: Combined
    var isBenchmarking by remember { mutableStateOf(false) }
    var guardBenchmarkResult by remember { mutableStateOf<OrbisAiBenchmark.BenchmarkResult?>(null) }
    var replyBenchmarkResult by remember { mutableStateOf<OrbisAiBenchmark.BenchmarkResult?>(null) }
    var combinedScore by remember { mutableIntStateOf(0) }
    var combinedEfficiency by remember { mutableStateOf("") }
    var showDetailedLog by remember { mutableStateOf(false) }

    fun runSelectedBenchmark() {
        if (isBenchmarking) return
        isBenchmarking = true
        scope.launch {
            withContext(Dispatchers.Default) {
                // Short pause to ensure UI renders loading indicator
                delay(120)
                when (selectedBenchmarkTab) {
                    0 -> {
                        guardBenchmarkResult = OrbisAiBenchmark.runGuardBenchmark(repeatCycles = 3)
                    }
                    1 -> {
                        replyBenchmarkResult = OrbisAiBenchmark.runReplyBenchmark(repeatCycles = 3)
                    }
                    2 -> {
                        val combined = OrbisAiBenchmark.runCombinedBenchmark()
                        guardBenchmarkResult = combined.guardResult
                        replyBenchmarkResult = combined.replyResult
                        combinedScore = combined.overallScore
                        combinedEfficiency = combined.hardwareEfficiency
                    }
                }
            }
            isBenchmarking = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        OrbisTopHeader(
            title = stringResource(R.string.settings_ai_title),
            subtitle = stringResource(R.string.settings_ai_subtitle),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner: Sovereign Architecture
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.ai_proprietary_engines_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.ai_benchmark_zero_cloud_badge),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.ai_proprietary_engines_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            // Section 1: Activation & Commutateurs
            Text(
                text = stringResource(R.string.ai_section_switches_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Master Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val targetState = !isMasterActive
                            isGuardActive = targetState
                            isReplyActive = targetState
                            aiPrefs.setMasterEnabled(targetState)
                            Toast.makeText(
                                context,
                                if (targetState) context.getString(R.string.ai_master_enabled_toast) else context.getString(R.string.ai_master_disabled_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ai_master_switch_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ai_master_switch_desc),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }

                    Switch(
                        checked = isMasterActive,
                        onCheckedChange = { targetState ->
                            isGuardActive = targetState
                            isReplyActive = targetState
                            aiPrefs.setMasterEnabled(targetState)
                            Toast.makeText(
                                context,
                                if (targetState) context.getString(R.string.ai_master_enabled_toast) else context.getString(R.string.ai_master_disabled_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            // Engine Models Grouped Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    AutomationRow(
                        icon = Icons.Default.Shield,
                        title = stringResource(R.string.ai_guard_switch_title),
                        desc = stringResource(R.string.ai_guard_switch_desc),
                        checked = isGuardActive,
                        onCheckedChange = { checked ->
                            isGuardActive = checked
                            aiPrefs.isGuardEnabled = checked
                        },
                        activeColor = OrbisColorPalette.StatusActive
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 62.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.5.dp
                    )
                    AutomationRow(
                        icon = Icons.Default.AutoAwesome,
                        title = stringResource(R.string.ai_reply_switch_title),
                        desc = stringResource(R.string.ai_reply_switch_desc),
                        checked = isReplyActive,
                        onCheckedChange = { checked ->
                            isReplyActive = checked
                            aiPrefs.isReplyEnabled = checked
                        },
                        activeColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Section 2: Télécom & VoIP Automation
            Text(
                text = stringResource(R.string.settings_voip_telecom_section_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Télécom & VoIP Grouped Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    AutomationRow(
                        icon = Icons.Default.DirectionsCar,
                        title = stringResource(R.string.settings_driving_mode_title),
                        desc = stringResource(R.string.settings_driving_mode_desc),
                        checked = isDrivingActive,
                        onCheckedChange = { checked ->
                            isDrivingActive = checked
                            aiPrefs.isDrivingAutoDeclineEnabled = checked
                        },
                        activeColor = Color(0xFFF59E0B)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 62.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.5.dp
                    )
                    AutomationRow(
                        icon = Icons.Default.HourglassBottom,
                        title = stringResource(R.string.settings_dnd_mode_title),
                        desc = stringResource(R.string.settings_dnd_mode_desc),
                        checked = isDndActive,
                        onCheckedChange = { checked ->
                            isDndActive = checked
                            aiPrefs.isDndAutoDeclineEnabled = checked
                        },
                        activeColor = Color(0xFF8B5CF6)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 62.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.5.dp
                    )
                    AutomationRow(
                        icon = Icons.Default.Schedule,
                        title = stringResource(R.string.settings_smart_recall_title),
                        desc = stringResource(R.string.settings_smart_recall_desc),
                        checked = isSmartRecallActive,
                        onCheckedChange = { checked ->
                            isSmartRecallActive = checked
                            aiPrefs.isSmartRecallEnabled = checked
                        },
                        activeColor = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 62.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.5.dp
                    )
                    AutomationRow(
                        icon = Icons.Default.Shield,
                        title = stringResource(R.string.settings_silent_burst_title),
                        desc = stringResource(R.string.settings_silent_burst_desc),
                        checked = isSilentBurstActive,
                        onCheckedChange = { checked ->
                            isSilentBurstActive = checked
                            aiPrefs.isSilentBurstShieldEnabled = checked
                        },
                        activeColor = OrbisColorPalette.StatusActive
                    )
                }
            }

            // Section 3: Benchmark & Hardware Telemetry
            Text(
                text = stringResource(R.string.ai_section_benchmark_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ai_benchmark_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Benchmark Scope Selector Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedBenchmarkTab == 0,
                            onClick = { selectedBenchmarkTab = 0 },
                            label = { Text(stringResource(R.string.ai_benchmark_tab_guard), fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        FilterChip(
                            selected = selectedBenchmarkTab == 1,
                            onClick = { selectedBenchmarkTab = 1 },
                            label = { Text(stringResource(R.string.ai_benchmark_tab_reply), fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        FilterChip(
                            selected = selectedBenchmarkTab == 2,
                            onClick = { selectedBenchmarkTab = 2 },
                            label = { Text(stringResource(R.string.ai_benchmark_tab_combined), fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    // Benchmark Trigger Button
                    Button(
                        onClick = { runSelectedBenchmark() },
                        enabled = !isBenchmarking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isBenchmarking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.ai_benchmark_running))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ai_benchmark_run_btn), fontWeight = FontWeight.Bold)
                        }
                    }

                    // Active Benchmark Results
                    val activeResult = when (selectedBenchmarkTab) {
                        0 -> guardBenchmarkResult
                        1 -> replyBenchmarkResult
                        else -> guardBenchmarkResult ?: replyBenchmarkResult
                    }

                    if (activeResult != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Score & Rating Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (selectedBenchmarkTab == 2 && combinedScore > 0) "Score Global Combiné" else activeResult.engineName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (selectedBenchmarkTab == 2 && combinedEfficiency.isNotBlank()) combinedEfficiency else activeResult.performanceRating,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OrbisColorPalette.StatusActive,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            val scoreToDisplay = if (selectedBenchmarkTab == 2 && combinedScore > 0) combinedScore else activeResult.performanceScore
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OrbisColorPalette.StatusActive.copy(alpha = 0.15f))
                                    .border(1.dp, OrbisColorPalette.StatusActive.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$scoreToDisplay / 100",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = OrbisColorPalette.StatusActive
                                )
                            }
                        }

                        // 4-Card Telemetry Grid
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    title = stringResource(R.string.ai_benchmark_metric_latency),
                                    value = "${activeResult.avgLatencyMs} ms",
                                    subtitle = "Min: ${activeResult.minLatencyMs} • Max: ${activeResult.maxLatencyMs} ms",
                                    modifier = Modifier.weight(1f),
                                    valueColor = if (activeResult.avgLatencyMs < 2.0f) OrbisColorPalette.StatusActive else MaterialTheme.colorScheme.primary
                                )
                                MetricCard(
                                    title = stringResource(R.string.ai_benchmark_metric_throughput),
                                    value = "${activeResult.throughputPerSec} inf/s",
                                    subtitle = "${activeResult.totalInferences} passes réelles",
                                    modifier = Modifier.weight(1f),
                                    valueColor = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    title = stringResource(R.string.ai_benchmark_metric_accuracy),
                                    value = "${activeResult.accuracyPercent} %",
                                    subtitle = "Dataset étalonné",
                                    modifier = Modifier.weight(1f),
                                    valueColor = OrbisColorPalette.StatusActive
                                )
                                MetricCard(
                                    title = stringResource(R.string.ai_benchmark_metric_memory),
                                    value = "${activeResult.memoryFootprintKb} KB",
                                    subtitle = "Int8 quantifié en RAM",
                                    modifier = Modifier.weight(1f),
                                    valueColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.ai_benchmark_languages_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        // Detailed inspection expander
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showDetailedLog = !showDetailedLog }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Détails des tests étalons (${activeResult.details.size} scénarios)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (showDetailedLog) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = showDetailedLog,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (detail in activeResult.details) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = detail.inputSnippet,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Résultat: ${detail.result} • ${String.format("%.2f", detail.latencyMs)} ms",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = if (detail.isPassed) OrbisColorPalette.StatusActive else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(isActive: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isActive) OrbisColorPalette.StatusActive.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isActive) stringResource(R.string.ai_state_active) else stringResource(R.string.ai_state_inactive),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) OrbisColorPalette.StatusActive else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AutomationRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (checked) activeColor.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                StatusChip(isActive = checked)
            }
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = activeColor,
                checkedTrackColor = activeColor.copy(alpha = 0.3f)
            ),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
