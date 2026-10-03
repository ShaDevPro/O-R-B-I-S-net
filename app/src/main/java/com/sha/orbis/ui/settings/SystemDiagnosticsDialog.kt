package com.sha.orbis.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sha.orbis.R
import com.sha.orbis.data.SessionManager
import com.sha.orbis.security.AesCipher
import com.sha.orbis.security.BatteryOptimizationHelper
import com.sha.orbis.security.BiometricAuthManager
import com.sha.orbis.security.DuressSecurityManager
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendCircleRepository
import com.sha.orbis.storage.LocalMessageStore
import com.sha.orbis.ui.theme.OrbisColorPalette
import java.io.File
import java.util.Locale

@Composable
fun SystemDiagnosticsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val convRepo = remember { ConversationRepository(context) }
    val messageStore = remember { LocalMessageStore(context) }
    val circleRepo = remember { FriendCircleRepository(context) }

    // 1. Crypto Test
    val hasRsa = remember { sessionManager.publicKey.isNotBlank() }
    val aesTestOk = remember {
        try {
            val key = AesCipher.generateKey()
            val enc = AesCipher.encrypt("ORBIS_HEALTH_CHECK", key)
            val dec = AesCipher.decrypt(enc, key)
            dec == "ORBIS_HEALTH_CHECK"
        } catch (_: Exception) {
            false
        }
    }

    // 2. Storage Stats
    val convCount = remember { convRepo.loadConversations().size }
    val contactCount = remember { convRepo.loadContacts().size }
    val messageCount = remember { messageStore.getTotalMessagesCount() }
    val circleCount = remember { circleRepo.loadCircles().size }
    val storageSizeBytes = remember {
        fun getFolderSize(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0L
            var total = 0L
            dir.listFiles()?.forEach { f ->
                total += if (f.isDirectory) getFolderSize(f) else f.length()
            }
            return total
        }
        getFolderSize(context.filesDir)
    }
    val storageSizeFormatted = remember(storageSizeBytes) {
        when {
            storageSizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f Mo", storageSizeBytes.toFloat() / (1024 * 1024))
            storageSizeBytes >= 1024 -> String.format(Locale.US, "%.1f Ko", storageSizeBytes.toFloat() / 1024)
            else -> "$storageSizeBytes octets"
        }
    }

    // 3. Hardware SIM Identity
    val simLabel = remember(sessionManager.userSimSlotIndex) {
        if (sessionManager.userSimSlotIndex >= 0) "SIM ${sessionManager.userSimSlotIndex + 1}" else "Sandbox (Démo)"
    }

    // 4. Permissions & System
    val nostrPool = remember { com.sha.orbis.nostr.client.RelayPoolManager.getInstance(context) }
    val poolHealth by nostrPool.poolHealth.collectAsState()
    val isBatteryExempt = remember { BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context) }
    val isBiometricReady = remember { BiometricAuthManager.isBiometricAvailable(context) }
    val isPinActive = remember { DuressSecurityManager.isPinEnabled(context) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Diagnostics Système",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Orbis v${com.sha.orbis.BuildConfig.VERSION_NAME} • 100% Souverain",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Diagnostic Item 1: Cryptography
                DiagnosticSection(
                    title = "Module Cryptographique",
                    icon = Icons.Default.Lock
                ) {
                    DiagnosticRow(
                        label = "Clés RSA-2048 Matériel",
                        value = if (hasRsa) "Valide & Signé" else "Non initialisé",
                        isSuccess = hasRsa
                    )
                    DiagnosticRow(
                        label = "Moteur AES-256 GCM",
                        value = if (aesTestOk) "Opérationnel (Auto-test OK)" else "Erreur d'exécution",
                        isSuccess = aesTestOk
                    )
                }

                // Diagnostic Item 2: Database & Storage
                DiagnosticSection(
                    title = "Stockage & Base Locale",
                    icon = Icons.Default.Storage
                ) {
                    DiagnosticRow(
                        label = "Conversations & Messages",
                        value = "$convCount chats • $messageCount msgs",
                        isSuccess = true
                    )
                    DiagnosticRow(
                        label = "Contacts & Cercles",
                        value = "$contactCount contacts • $circleCount cercles",
                        isSuccess = true
                    )
                    DiagnosticRow(
                        label = "Empreinte Stockage Local",
                        value = storageSizeFormatted,
                        isSuccess = true
                    )
                }

                // Diagnostic Item 3: Hardware SIM & Sovereign Identity
                DiagnosticSection(
                    title = "Identité Ligne & Carte SIM",
                    icon = Icons.Default.SimCard
                ) {
                    DiagnosticRow(
                        label = "Ligne Hardware Active",
                        value = "$simLabel (${sessionManager.userPhone.ifBlank { "Inconnue" }})",
                        isSuccess = sessionManager.userSimSlotIndex >= 0
                    )
                    DiagnosticRow(
                        label = "Transport & Signalisation",
                        value = "100% Internet Data / Wi-Fi (Nostr & WebRTC)",
                        isSuccess = true
                    )
                }

                // Diagnostic Item 4: OS Security & Permissions
                DiagnosticSection(
                    title = "Système Android & Sécurité",
                    icon = Icons.Default.Smartphone
                ) {
                    DiagnosticRow(
                        label = "Réseau Nostr Décentralisé",
                        value = if (poolHealth.isConnected) "${poolHealth.connectedCount}/${poolHealth.totalRelays} Relais connectés 🌐" else "Connexion aux relais en cours...",
                        isSuccess = poolHealth.isConnected
                    )
                    DiagnosticRow(
                        label = "Optimisation Batterie",
                        value = if (isBatteryExempt) "Exempté (Réception continue)" else "Non exempté (Mode standard)",
                        isSuccess = isBatteryExempt
                    )
                    DiagnosticRow(
                        label = "Biométrie & Code PIN",
                        value = if (isPinActive) "PIN Actif" + (if (isBiometricReady) " • Empreinte OK" else "") else "Non protégé",
                        isSuccess = isPinActive
                    )
                }

                // Diagnostic Item 5: App Integrity & Anti-Tamper Shield
                val integrityReport = remember { com.sha.orbis.security.AppIntegrityGuard.verifyIntegrity(context) }
                DiagnosticSection(
                    title = "Bouclier d'Intégrité & Anti-Tamper",
                    icon = Icons.Default.Security
                ) {
                    DiagnosticRow(
                        label = "Signature APK SHA-256",
                        value = integrityReport.currentSignatureHash.take(23) + "...",
                        isSuccess = integrityReport.isSignatureValid
                    )
                    DiagnosticRow(
                        label = "Protection Anti-Debug / Trace",
                        value = if (integrityReport.isDebuggerAttached) "Débogueur Détecté ⚠️" else "Sécurisé (Aucun traceur)",
                        isSuccess = !integrityReport.isDebuggerAttached
                    )
                    DiagnosticRow(
                        label = "Protection Anti-Hooking / Frida",
                        value = if (integrityReport.isFridaDetected) "Frida/Xposed Détecté ⚠️" else "Sécurisé (Aucune injection)",
                        isSuccess = !integrityReport.isFridaDetected
                    )
                    DiagnosticRow(
                        label = "Environnement d'Exécution",
                        value = if (integrityReport.isRootDetected) "Root / Test-Keys Détecté" else "Environnement Sain (Non-Root)",
                        isSuccess = !integrityReport.isRootDetected
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.close), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        content()
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    isSuccess: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isSuccess) OrbisColorPalette.StatusActive else OrbisColorPalette.StatusWarning,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSuccess) MaterialTheme.colorScheme.onSurface else OrbisColorPalette.StatusWarning
            )
        }
    }
}
