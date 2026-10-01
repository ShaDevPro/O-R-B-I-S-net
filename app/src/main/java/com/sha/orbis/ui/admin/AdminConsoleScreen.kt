package com.sha.orbis.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sha.orbis.R
import com.sha.orbis.admin.AdminLogEntry
import com.sha.orbis.admin.AdminLogger
import com.sha.orbis.admin.LogLevel
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Contact
import com.sha.orbis.model.Conversation
import com.sha.orbis.model.Message
import com.sha.orbis.model.MessageDeliveryStatus
import com.sha.orbis.security.AesCipher
import com.sha.orbis.security.BatteryOptimizationHelper
import com.sha.orbis.security.BiometricAuthManager
import com.sha.orbis.security.DuressSecurityManager
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.social.OfficialAnnouncementsProvider
import com.sha.orbis.social.PollOption
import com.sha.orbis.social.SocialPoll
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.storage.BlockedContactsRepository
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendCircleRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.storage.LocalMessageStore
import com.sha.orbis.storage.SocialRepository
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.components.OrbisTopHeader
import com.sha.orbis.ui.theme.OrbisColorPalette
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AdminLightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun AdminConsoleScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var logsVersion by remember { mutableIntStateOf(0) }

    MaterialTheme(
        colorScheme = AdminLightColorScheme,
        typography = MaterialTheme.typography
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AdminLightColorScheme.background)
        ) {
            // Top Sovereign Header
            OrbisTopHeader(
                title = "Console Développeur",
                subtitle = "Superviseur Télémétrie Nostr & Kernel Orbis v2.0",
                onBack = onBack
            )

            // Tab Navigation
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFFF1F5F9),
                edgePadding = 12.dp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("📊 Santé", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selectedTab == 0) Color(0xFF0284C7) else Color(0xFF64748B)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("📢 Annonces", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selectedTab == 1) Color(0xFF0284C7) else Color(0xFF64748B)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("🛠️ Outils", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selectedTab == 2) Color(0xFF0284C7) else Color(0xFF64748B)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("📜 Logs", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selectedTab == 3) Color(0xFF0284C7) else Color(0xFF64748B)) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("🌍 Télémétrie", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selectedTab == 4) Color(0xFF0284C7) else Color(0xFF64748B)) }
                )
            }

            // Tab Contents
            when (selectedTab) {
                0 -> AdminHealthDashboardTab()
                1 -> AdminAnnouncementsTab()
                2 -> AdminToolsTab(
                    onDataChanged = {
                        logsVersion++
                    }
                )
                3 -> AdminLogsTab(
                    logsVersion = logsVersion,
                    onClearLogs = {
                        AdminLogger.clear()
                        logsVersion++
                    }
                )
                4 -> AdminTelemetryTab()
            }
        }
    }
}

/**
 * Tab 1: Live Health & Telemetry Dashboard
 */
@Composable
private fun AdminHealthDashboardTab() {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val convRepo = remember { ConversationRepository(context) }
    val messageStore = remember { LocalMessageStore(context) }
    val circleRepo = remember { FriendCircleRepository(context) }
    val socialRepo = remember { SocialRepository(context) }
    val friendReqRepo = remember { FriendRequestRepository(context) }
    val blockedRepo = remember { BlockedContactsRepository(context) }

    var cryptoBenchmarkResult by remember { mutableStateOf<String?>(null) }
    var isBenchmarking by remember { mutableStateOf(false) }

    val runtime = Runtime.getRuntime()
    val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val maxMemMb = runtime.maxMemory() / (1024 * 1024)
    val totalMemMb = runtime.totalMemory() / (1024 * 1024)

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
            else -> "$storageSizeBytes o"
        }
    }

    val isBatteryExempt = remember { BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Stat Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "RAM Allouée",
                value = "$usedMemMb Mo",
                subtitle = "sur $totalMemMb Mo",
                icon = Icons.Default.Memory,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Coffre Local",
                value = storageSizeFormatted,
                subtitle = "Fichiers JSON",
                icon = Icons.Default.Storage,
                tint = OrbisColorPalette.StatusActive,
                modifier = Modifier.weight(1f)
            )
            val poolHealth = remember { com.sha.orbis.nostr.client.RelayPoolManager.getInstance(context).poolHealth.value }
            MetricCard(
                title = "Relais Nostr",
                value = "${poolHealth.connectedCount}",
                subtitle = "/ ${poolHealth.totalRelays} actifs",
                icon = Icons.Default.Public,
                tint = if (poolHealth.connectedCount > 0) OrbisColorPalette.StatusActive else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        // Section: Cryptographic Kernel
        AdminSectionCard(
            title = "Moteur Cryptographique & Clés",
            icon = Icons.Default.Lock
        ) {
            AdminStatusRow(
                label = "Identité Nostr (Secp256k1)",
                value = if (sessionManager.publicKey.isNotBlank()) "BIP-340 / Schnorr E2EE" else "Non générée",
                isOk = sessionManager.publicKey.isNotBlank()
            )

            AdminStatusRow(
                label = "Fingerprint Clé Publique",
                value = if (sessionManager.publicKey.isNotBlank()) sessionManager.publicKey.take(16) + "..." else "N/A",
                isOk = true,
                onCopy = if (sessionManager.publicKey.isNotBlank()) sessionManager.publicKey else null
            )

            AdminStatusRow(
                label = "Chiffrement AES-256 GCM",
                value = "Matériel / CBC-PKCS5",
                isOk = true
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Benchmark button
            OutlinedButton(
                onClick = {
                    isBenchmarking = true
                    val start = System.currentTimeMillis()
                    val key = AesCipher.generateKey()
                    for (i in 0 until 50) {
                        val enc = AesCipher.encrypt("BENCHMARK_PAYLOAD_TEST_DATA_$i", key)
                        AesCipher.decrypt(enc, key)
                    }
                    val duration = System.currentTimeMillis() - start
                    cryptoBenchmarkResult = "50 cycles AES-256 exécutés en ${duration}ms (${String.format(Locale.US, "%.2f", 50.0 / (duration.coerceAtLeast(1) / 1000.0))} op/sec)"
                    isBenchmarking = false
                    AdminLogger.crypto("BENCHMARK", cryptoBenchmarkResult!!)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(if (isBenchmarking) "Calcul en cours..." else "Exécuter Benchmark Crypto", fontSize = 12.sp)
                }
            }

            if (cryptoBenchmarkResult != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(OrbisColorPalette.StatusActive.copy(alpha = 0.1f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "⚡ $cryptoBenchmarkResult",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OrbisColorPalette.StatusActive
                    )
                }
            }
        }

        // Section: System & Permissions
        AdminSectionCard(
            title = "Environnement Matériel & OS",
            icon = Icons.Default.Smartphone
        ) {
            AdminStatusRow(
                label = "Modèle Appareil",
                value = "${Build.MANUFACTURER} ${Build.MODEL}",
                isOk = true
            )
            AdminStatusRow(
                label = "Android SDK & Version",
                value = "Android SDK ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
                isOk = true
            )
            val nostrConnected = remember { com.sha.orbis.nostr.client.RelayPoolManager.getInstance(context).poolHealth.value.connectedCount > 0 }
            AdminStatusRow(
                label = "Réseau Relais Nostr Décentralisé",
                value = if (nostrConnected) "Connecté au réseau souverain 🌐" else "Déconnecté / En attente",
                isOk = nostrConnected
            )
            AdminStatusRow(
                label = "Exemption Doze Batterie",
                value = if (isBatteryExempt) "Exempté (Réception persistante)" else "Mode standard",
                isOk = isBatteryExempt
            )
            AdminStatusRow(
                label = "Ligne SIM / Opérateur",
                value = "${if (sessionManager.userSimSlotIndex >= 0) "SIM ${sessionManager.userSimSlotIndex + 1}" else "Sandbox"} (${sessionManager.userOperatorName.ifBlank { "GSM" }})",
                isOk = sessionManager.userSimSlotIndex >= 0
            )
        }

        // Section: Storage Database Footprint
        AdminSectionCard(
            title = "Données & Tables Souveraines",
            icon = Icons.Default.Storage
        ) {
            val convs = convRepo.loadConversations()
            val contacts = convRepo.loadContacts()
            val msgsCount = messageStore.getTotalMessagesCount()
            val circles = circleRepo.loadCircles()
            val posts = socialRepo.loadPosts()
            val pendingReqs = friendReqRepo.getPendingReceived().size
            val blocked = blockedRepo.loadBlocked()

            AdminStatusRow(label = "Conversations Actives", value = "${convs.size} conversations", isOk = true)
            AdminStatusRow(label = "Messages Archivés", value = "$msgsCount messages chiffrés", isOk = true)
            AdminStatusRow(label = "Contacts & Cercles", value = "${contacts.size} contacts • ${circles.size} cercles", isOk = true)
            AdminStatusRow(label = "Publications Mur", value = "${posts.size} posts / sondages", isOk = true)
            AdminStatusRow(label = "Demandes d'amis & Bloqués", value = "$pendingReqs en attente • ${blocked.size} bloqués", isOk = true)
        }
    }
}

/**
 * Tab 3: Developer Tools & Simulation
 */
@Composable
private fun AdminToolsTab(
    onDataChanged: () -> Unit
) {
    val context = LocalContext.current
    val convRepo = remember(context) { ConversationRepository(context) }
    val messageStore = remember(context) { LocalMessageStore(context) }
    val socialRepo = remember(context) { SocialRepository(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Simulation Section
        AdminSectionCard(
            title = "Simulation & Injection d'Événements Nostr",
            icon = Icons.Default.DeveloperMode
        ) {
            Text(
                text = "Injecte des données de test réalistes pour tester les publications réseau, les alertes et les décodeurs souverains.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = {
                    val testPost = SocialPost(
                        id = "post_${System.currentTimeMillis()}",
                        authorPhone = "+213770000003",
                        authorName = "Orbis Network Hub",
                        authorAvatarPath = null,
                        authorRole = UserSocialRole.VERIFIED_E2EE,
                        content = "📡 Message réseau broadcasté via les relais Nostr chiffrés (Kind 1). Chiffrement souverain opérationnel !",
                        timestamp = System.currentTimeMillis()
                    )
                    socialRepo.addPost(testPost)
                    AdminLogger.info("SOCIAL", "Publication de test injectée sur le mur.")
                    onDataChanged()
                    Toast.makeText(context, "Post de test créé sur le mur !", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Simuler Publication Mur", fontSize = 12.sp)
                }
            }
        }

        // Database & Diagnostic Tools
        AdminSectionCard(
            title = "Maintenance Base de Données & Diagnostics",
            icon = Icons.Default.Storage
        ) {
            // Seed Demo Contacts
            OutlinedButton(
                onClick = {
                    val demoContact = Contact(
                        id = "c_213550112233",
                        name = "Support Orbis Démo",
                        phone = "+213550112233",
                        publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0demoKey...",
                        status = "Certifié Développeur 🛡️",
                        avatarPath = null
                    )
                    convRepo.insertOrUpdateContact(demoContact)
                    val demoConv = Conversation(
                        id = "c_213550112233",
                        title = "Support Orbis Démo",
                        participants = listOf("+213550112233"),
                        lastMessage = "Bienvenue sur Orbis Sovereign Network !",
                        updatedAt = System.currentTimeMillis()
                    )
                    convRepo.addConversation(demoConv)
                    val msg = Message(
                        id = "m_${System.currentTimeMillis()}",
                        conversationId = demoConv.id,
                        senderId = "+213550112233",
                        text = "Canal chiffré de test établi avec succès. Tous les paquets sont scellés avec RSA-2048.",
                        timestamp = System.currentTimeMillis(),
                        status = MessageDeliveryStatus.DELIVERED
                    )
                    messageStore.addMessage(demoConv.id, msg)
                    AdminLogger.info("STORAGE", "Contact & conversation de test initialisés.")
                    onDataChanged()
                    Toast.makeText(context, "Contact et conversation de test ajoutés !", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Générer Contact & Chat Démo", fontSize = 12.sp)
                }
            }

            // Export Diagnostic JSON
            OutlinedButton(
                onClick = {
                    val sessionManager = SessionManager(context)
                    val diagJson = JSONObject().apply {
                        put("appVersion", com.sha.orbis.BuildConfig.VERSION_NAME)
                        put("androidSdk", Build.VERSION.SDK_INT)
                        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                        put("userPhone", sessionManager.userPhone)
                        put("simSlot", sessionManager.userSimSlotIndex)
                        put("operator", sessionManager.userOperatorName)
                        put("publicKeyFingerprint", sessionManager.publicKey.take(24))
                        put("hasPin", DuressSecurityManager.isPinEnabled(context))
                        put("nostrRelaysConnected", com.sha.orbis.nostr.client.RelayPoolManager.getInstance(context).poolHealth.value.connectedCount)
                        put("isBatteryExempt", BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
                        put("conversationsCount", convRepo.loadConversations().size)
                        put("contactsCount", convRepo.loadContacts().size)
                        put("messagesCount", messageStore.getTotalMessagesCount())
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Orbis Diagnostics", diagJson.toString(2)))
                    AdminLogger.info("SYSTEM", "Rapport JSON de diagnostic exporté dans le presse-papiers.")
                    Toast.makeText(context, "Rapport de diagnostic copié dans le presse-papiers !", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Copier Diagnostic Système (JSON)", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Tab 4: Live Protocol & Event Logs
 */
@Composable
private fun AdminLogsTab(
    logsVersion: Int,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    val allLogs = remember(logsVersion) { AdminLogger.getLogs() }
    var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }

    val filteredLogs = remember(allLogs, selectedLevelFilter) {
        if (selectedLevelFilter == null) allLogs else allLogs.filter { it.level == selectedLevelFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Logs Système (${filteredLogs.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Copier",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val text = filteredLogs.joinToString("\n") { "[${it.formattedTime}] [${it.level}] [${it.tag}]: ${it.message}" }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Orbis Logs", text))
                        Toast.makeText(context, "Logs copiés !", Toast.LENGTH_SHORT).show()
                    }
                )
                Text(
                    text = "Effacer",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onClearLogs)
                )
            }
        }

        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryFilterChip(
                label = "Tous",
                isSelected = selectedLevelFilter == null,
                onClick = { selectedLevelFilter = null }
            )
            LogLevel.values().forEach { lvl ->
                CategoryFilterChip(
                    label = lvl.name,
                    isSelected = selectedLevelFilter == lvl,
                    onClick = { selectedLevelFilter = if (selectedLevelFilter == lvl) null else lvl }
                )
            }
        }

        // Terminal Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = log.formattedTime,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when (log.level) {
                                        LogLevel.INFO -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        LogLevel.NOSTR, LogLevel.GSM -> OrbisColorPalette.StatusActive.copy(alpha = 0.15f)
                                        LogLevel.CRYPTO -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                        LogLevel.WARN -> OrbisColorPalette.StatusWarning.copy(alpha = 0.15f)
                                        LogLevel.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    }
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = log.level.name,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (log.level) {
                                    LogLevel.INFO -> MaterialTheme.colorScheme.primary
                                    LogLevel.NOSTR, LogLevel.GSM -> OrbisColorPalette.StatusActive
                                    LogLevel.CRYPTO -> Color(0xFF8B5CF6)
                                    LogLevel.WARN -> OrbisColorPalette.StatusWarning
                                    LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                        Text(
                            text = "[${log.tag}]",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = log.message,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// Reusable Components

@Composable
private fun AdminSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF0F172A)
                )
            }
            HorizontalDivider(color = Color(0xFFE2E8F0))
            content()
        }
    }
}

@Composable
private fun AdminStatusRow(
    label: String,
    value: String,
    isOk: Boolean,
    onCopy: String? = null
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isOk) OrbisColorPalette.StatusActive else OrbisColorPalette.StatusWarning,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (onCopy != null) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(label, onCopy))
                        Toast.makeText(context, "Copié !", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            }
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = tint)
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 9.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}



/**
 * Tab 2: Official Announcements & Wall Feeds Manager
 */
@Composable
private fun AdminAnnouncementsTab() {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val socialRepo = remember { SocialRepository(context) }

    var postsVersion by remember { mutableIntStateOf(0) }
    var posts by remember(postsVersion) { mutableStateOf(socialRepo.loadPosts()) }
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: Toutes, 1: Officielles, 2: Épinglées
    var searchQuery by remember { mutableStateOf("") }

    var showCreateEditDialog by remember { mutableStateOf(false) }
    var postToEdit by remember { mutableStateOf<SocialPost?>(null) }
    var postToDelete by remember { mutableStateOf<SocialPost?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    fun reloadPosts() {
        posts = socialRepo.loadPosts()
        postsVersion++
    }

    val filteredPosts = remember(posts, selectedFilterIndex, searchQuery) {
        posts.filter { p ->
            val matchFilter = when (selectedFilterIndex) {
                1 -> p.isOfficialAnnouncement || p.authorRole == UserSocialRole.FOUNDER_DEV || AdminSecurityHelper.isAdmin(p.authorPhone)
                2 -> p.isPinned
                else -> true
            }
            val matchQuery = searchQuery.isBlank() ||
                p.content.contains(searchQuery, ignoreCase = true) ||
                p.authorName.contains(searchQuery, ignoreCase = true) ||
                p.hashtags.any { it.contains(searchQuery, ignoreCase = true) }

            matchFilter && matchQuery
        }
    }

    // Dialogs
    if (showCreateEditDialog) {
        AdminCreateEditAnnouncementDialog(
            postToEdit = postToEdit,
            onDismiss = {
                showCreateEditDialog = false
                postToEdit = null
            },
            onSaved = {
                showCreateEditDialog = false
                postToEdit = null
                reloadPosts()
            }
        )
    }

    if (postToDelete != null) {
        AlertDialog(
            onDismissRequest = { postToDelete = null },
            title = { Text("Supprimer la publication ?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Voulez-vous vraiment supprimer définitivement cette annonce du fil d'actualité ?\n\n\"${postToDelete!!.content.take(80)}...\"",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = OfficialAnnouncementsProvider.deleteOfficialPost(
                            context = context,
                            callerPhone = sessionManager.userPhone,
                            postId = postToDelete!!.id
                        )
                        if (deleted) {
                            Toast.makeText(context, "Publication supprimée avec succès.", Toast.LENGTH_SHORT).show()
                            reloadPosts()
                        } else {
                            Toast.makeText(context, "Erreur : privilèges admin requis.", Toast.LENGTH_SHORT).show()
                        }
                        postToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { postToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Réinitialiser les annonces par défaut ?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Cette action restaurera les annonces officielles d'origine (Site officiel GitHub et Bienvenue) avec leurs signatures authentifiées.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val restored = OfficialAnnouncementsProvider.restoreDefaultAnnouncements(
                            context = context,
                            callerPhone = sessionManager.userPhone
                        )
                        if (restored) {
                            Toast.makeText(context, "Annonces officielles restaurées !", Toast.LENGTH_SHORT).show()
                            reloadPosts()
                        } else {
                            Toast.makeText(context, "Erreur : privilèges admin requis.", Toast.LENGTH_SHORT).show()
                        }
                        showResetConfirmDialog = false
                    }
                ) {
                    Text("Restaurer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Total Posts",
                    value = posts.size.toString(),
                    icon = Icons.Default.Public,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Officielles",
                    value = posts.count { it.isOfficialAnnouncement || it.authorRole == UserSocialRole.FOUNDER_DEV || AdminSecurityHelper.isAdmin(it.authorPhone) }.toString(),
                    icon = Icons.Default.Campaign,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Épinglées",
                    value = posts.count { it.isPinned }.toString(),
                    icon = Icons.Default.PushPin,
                    tint = OrbisColorPalette.StatusActive,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Top Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        postToEdit = null
                        showCreateEditDialog = true
                    },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Nouvelle Annonce", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { showResetConfirmDialog = true },
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("Défauts", fontSize = 11.sp)
                    }
                }
            }
        }

        // Search & Filter
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher dans les annonces & posts...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                // Category Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryFilterChip(
                        label = "Toutes (${posts.size})",
                        isSelected = selectedFilterIndex == 0,
                        onClick = { selectedFilterIndex = 0 }
                    )
                    CategoryFilterChip(
                        label = "Officielles 🛡️",
                        isSelected = selectedFilterIndex == 1,
                        onClick = { selectedFilterIndex = 1 }
                    )
                    CategoryFilterChip(
                        label = "Épinglées 📌",
                        isSelected = selectedFilterIndex == 2,
                        onClick = { selectedFilterIndex = 2 }
                    )
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "Publications actives (${filteredPosts.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Posts List or Empty State
        if (filteredPosts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (posts.isEmpty()) "Aucune annonce enregistrée" else "Aucune annonce trouvée pour ce filtre",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Vous pouvez créer une nouvelle annonce officielle ou restaurer les annonces par défaut.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredPosts, key = { it.id }) { post ->
                AdminAnnouncementCard(
                    post = post,
                    onTogglePin = {
                        val toggled = OfficialAnnouncementsProvider.togglePinOfficialPost(
                            context = context,
                            callerPhone = sessionManager.userPhone,
                            postId = post.id
                        )
                        if (toggled) {
                            val msg = if (post.isPinned) "Annonce désépinglée." else "Annonce épinglée en haut du fil !"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            reloadPosts()
                        }
                    },
                    onEdit = {
                        postToEdit = post
                        showCreateEditDialog = true
                    },
                    onDelete = {
                        postToDelete = post
                    }
                )
            }
        }
    }
}

@Composable
private fun AdminAnnouncementCard(
    post: SocialPost,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isOfficial = post.isOfficialAnnouncement || post.authorRole == UserSocialRole.FOUNDER_DEV || AdminSecurityHelper.isAdmin(post.authorPhone)
    val timeFormatted = remember(post.timestamp) {
        SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(post.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (post.isPinned) Color.White else Color(0xFFF8FAFC)
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (post.isPinned) 1.5.dp else 1.dp,
            if (post.isPinned) Color(0xFF0284C7) else Color(0xFFE2E8F0)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    OrbisAvatar(
                        avatarPath = post.authorAvatarPath,
                        name = post.authorName,
                        size = 36.dp
                    )

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = post.authorName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isOfficial) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "OFFICIEL",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                            if (post.isPinned) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "📌 ÉPINGLÉ",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${post.authorPhone} • $timeFormatted",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // RSA signature indicator
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OrbisColorPalette.StatusActive.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(10.dp), tint = OrbisColorPalette.StatusActive)
                        Text("RSA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = OrbisColorPalette.StatusActive)
                    }
                }
            }

            // Post content
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )

            // Hashtags (FlowRow prevents character wrapping)
            if (post.hashtags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    post.hashtags.forEach { tag ->
                        Text(
                            text = "#$tag",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Poll summary if attached
            if (post.poll != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Poll, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Sondage : ${post.poll.question}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        post.poll.options.forEach { opt ->
                            Text(
                                text = "• ${opt.text} (${opt.voteCount} votes)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Bottom Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Pin / Unpin button
                OutlinedButton(
                    onClick = onTogglePin,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(if (post.isPinned) "Désépingler" else "Épingler", fontSize = 11.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Edit button
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Modifier", fontSize = 11.sp)
                        }
                    }

                    // Delete button
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Supprimer", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminCreateEditAnnouncementDialog(
    postToEdit: SocialPost?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }

    var content by remember { mutableStateOf(postToEdit?.content ?: "") }
    var hashtagsText by remember { mutableStateOf(postToEdit?.hashtags?.joinToString(", ") ?: "orbis, website, update") }
    var isPinned by remember { mutableStateOf(postToEdit?.isPinned ?: true) }

    var hasPoll by remember { mutableStateOf(postToEdit?.poll != null) }
    var pollQuestion by remember { mutableStateOf(postToEdit?.poll?.question ?: "") }
    var option1 by remember { mutableStateOf(postToEdit?.poll?.options?.getOrNull(0)?.text ?: "") }
    var option2 by remember { mutableStateOf(postToEdit?.poll?.options?.getOrNull(1)?.text ?: "") }
    var option3 by remember { mutableStateOf(postToEdit?.poll?.options?.getOrNull(2)?.text ?: "") }

    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (postToEdit != null) "Modifier l'Annonce" else "Nouvelle Annonce Officielle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Signé mathématiquement RSA-2048 • O R B I S",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Content field
                Text("Contenu de l'annonce", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("Écrivez votre publication (texte, lien web, changelog...). Ex: https://shadevpro.github.io/O-R-B-I-S-net/") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                // Hashtags
                Text("Hashtags (séparés par virgules)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                OutlinedTextField(
                    value = hashtagsText,
                    onValueChange = { hashtagsText = it },
                    placeholder = { Text("ex: orbis, website, update, security") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )

                // Pinned switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Épingler en haut du fil", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it }
                    )
                }

                // Poll Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Poll, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF8B5CF6))
                        Text("Ajouter un sondage interactif", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Switch(
                        checked = hasPoll,
                        onCheckedChange = { hasPoll = it }
                    )
                }

                // Poll fields
                if (hasPoll) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = pollQuestion,
                            onValueChange = { pollQuestion = it },
                            placeholder = { Text("Question du sondage...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = option1,
                            onValueChange = { option1 = it },
                            placeholder = { Text("Option 1...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = option2,
                            onValueChange = { option2 = it },
                            placeholder = { Text("Option 2...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = option3,
                            onValueChange = { option3 = it },
                            placeholder = { Text("Option 3 (optionnel)...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (content.isBlank()) {
                                Toast.makeText(context, "Le contenu de l'annonce ne peut pas être vide.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val pollOpts = listOf(option1, option2, option3).filter { it.isNotBlank() }
                            if (hasPoll && (pollQuestion.isBlank() || pollOpts.size < 2)) {
                                Toast.makeText(context, "Le sondage requiert une question et au moins 2 options.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isSubmitting = true
                            val hashtagsList = hashtagsText.split(",", "#", " ")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }

                            val success = OfficialAnnouncementsProvider.publishOfficialPost(
                                context = context,
                                callerPhone = sessionManager.userPhone,
                                callerPrivateKey = sessionManager.privateKey,
                                content = content,
                                hashtags = hashtagsList,
                                isPinned = isPinned,
                                pollQuestion = if (hasPoll) pollQuestion else null,
                                pollOptions = if (hasPoll) pollOpts else emptyList(),
                                existingPostId = postToEdit?.id
                            )

                            isSubmitting = false
                            if (success) {
                                Toast.makeText(context, "Annonce officielle publiée et signée avec succès !", Toast.LENGTH_SHORT).show()
                                onSaved()
                            } else {
                                Toast.makeText(context, "Échec : privilèges administrateur requis.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = content.isNotBlank() && !isSubmitting,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (postToEdit != null) "Mettre à jour" else "Publier l'Annonce")
                    }
                }
            }
        }
    }
}
