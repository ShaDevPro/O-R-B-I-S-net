package com.sha.orbis.ui.calls

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.sha.orbis.nostr.service.NostrSyncManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import com.sha.orbis.ai.ui.CallGuardBadge
import android.widget.Toast
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.call.OrbisCallManager
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.CallDirection
import com.sha.orbis.model.CallGroup
import com.sha.orbis.model.CallRecord
import com.sha.orbis.model.Contact
import com.sha.orbis.storage.CallLogRepository
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.ui.components.OrbisAvatar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class CallFilterTab { ALL, MISSED }

private data class PendingRecall(
    val phone: String,
    val name: String,
    val avatar: String?,
    val isVideo: Boolean
)

/**
 * Page Appel (Journal d'appels souverain) haut de gamme :
 * - Regroupement de tous les appels audio et vidéo par utilisateur / contact dans une carte empilée.
 * - Au clic sur la carte, elle se déplie avec animation fluide pour révéler l'historique complet.
 * - Actions directes (appel vocal vert, appel vidéo bleu, discussion instantanée, suppression).
 * - Support trilingue complet (FR, EN, AR) et RTL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    currentAccountId: String? = null,
    searchQuery: String = "",
    onOpenChat: ((phone: String, name: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionManager = remember(context) { SessionManager(context) }
    val activeAccountId = currentAccountId ?: sessionManager.activeAccountId
    val callLogRepo = remember(context, activeAccountId) { CallLogRepository(context, activeAccountId) }
    val convRepo = remember(context, activeAccountId) { ConversationRepository(context, activeAccountId) }
    val locale = Locale.getDefault()

    var rawGroups by remember(activeAccountId) { mutableStateOf(callLogRepo.loadCallsGroupedByUser()) }
    var selectedFilter by remember { mutableStateOf(CallFilterTab.ALL) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showNewCallSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var userToDeleteCalls by remember { mutableStateOf<CallGroup?>(null) }
    var pendingRecallCall by remember { mutableStateOf<PendingRecall?>(null) }

    // Clés des cartes dépliées
    var expandedUserKeys by remember { mutableStateOf(setOf<String>()) }

    fun refresh() {
        rawGroups = callLogRepo.loadCallsGroupedByUser()
    }

    LaunchedEffect(activeAccountId) {
        com.sha.orbis.call.OrbisMissedCallManager.clearAllMissedCalls(context)
        refresh()
    }

    DisposableEffect(activeAccountId) {
        refresh()
        onDispose {}
    }

    val filteredGroups = remember(rawGroups, selectedFilter, searchQuery) {
        rawGroups.filter { g ->
            val matchFilter = when (selectedFilter) {
                CallFilterTab.ALL    -> true
                CallFilterTab.MISSED -> g.hasMissed
            }
            val matchSearch = searchQuery.isBlank() ||
                g.peerName.contains(searchQuery, ignoreCase = true) ||
                g.peerPhone.contains(searchQuery, ignoreCase = true)
            matchFilter && matchSearch
        }
    }

    val dateSections: List<Pair<String, List<CallGroup>>> = remember(filteredGroups) {
        buildDateSections(filteredGroups, context, locale)
    }

    fun startCall(phone: String, name: String, avatar: String?, isVideo: Boolean) {
        com.sha.orbis.call.OrbisMissedCallManager.cancelMissedCallNotification(context, phone)
        OrbisCallManager.startOutgoingCall(
            context = context,
            peerPhone = phone,
            peerName = name,
            peerAvatar = avatar,
            myPhone = sessionManager.userPhone,
            isVideoCall = isVideo
        )
        refresh()
    }

    fun requestCall(phone: String, name: String, avatar: String?, isVideo: Boolean) {
        if (com.sha.orbis.ai.suggestions.OrbisSuggestionLibrary.isOffHours()) {
            pendingRecallCall = PendingRecall(phone, name, avatar, isVideo)
        } else {
            startCall(phone, name, avatar, isVideo)
        }
    }

    fun toggleExpand(userKey: String) {
        expandedUserKeys = if (expandedUserKeys.contains(userKey)) {
            expandedUserKeys - userKey
        } else {
            expandedUserKeys + userKey
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewCallSheet = true },
                containerColor = Color(0xFF10B981),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = stringResource(R.string.calls_new_call),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Barre d'onglets de filtrage et menu - Design Premium WhatsApp Sky Blue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallFilterChip(
                        label = stringResource(R.string.calls_filter_all),
                        count = rawGroups.size,
                        isSelected = selectedFilter == CallFilterTab.ALL,
                        icon = Icons.Default.Call,
                        onClick = { selectedFilter = CallFilterTab.ALL }
                    )
                    CallFilterChip(
                        label = stringResource(R.string.calls_filter_missed),
                        count = rawGroups.count { it.hasMissed },
                        isSelected = selectedFilter == CallFilterTab.MISSED,
                        isMissedBadge = false,
                        icon = Icons.AutoMirrored.Filled.CallMissed,
                        onClick = { selectedFilter = CallFilterTab.MISSED }
                    )
                }

                Box {
                    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                    val callsBorderLight = Color(0xFFBAE6FD)
                    val callsBorderDark = Color(0xFF2B4C7E)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                            )
                            .border(
                                1.dp,
                                if (isDark) callsBorderDark.copy(alpha = 0.5f) else callsBorderLight.copy(alpha = 0.65f),
                                CircleShape
                            )
                            .clickable { showMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.calls_clear_history),
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            onClick = {
                                showMenu = false
                                showClearDialog = true
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF2B4C7E).copy(alpha = 0.4f) else Color(0xFFBAE6FD).copy(alpha = 0.6f),
                thickness = 0.8.dp
            )

            var isRefreshingCalls by remember { mutableStateOf(false) }

            PullToRefreshBox(
                isRefreshing = isRefreshingCalls,
                onRefresh = {
                    isRefreshingCalls = true
                    coroutineScope.launch {
                        try {
                            NostrSyncManager.getInstance(context).reconnect(force = false)
                        } catch (_: Exception) {}
                        refresh()
                        isRefreshingCalls = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (dateSections.isEmpty()) {
                    CallsEmptyState(
                        isMissedOnly = selectedFilter == CallFilterTab.MISSED,
                        onStartNewCall = { showNewCallSheet = true }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp)
                    ) {
                        dateSections.forEach { (sectionLabel, groups) ->
                            item(key = "hdr_$sectionLabel") {
                                CallDateHeader(label = sectionLabel)
                            }
                            items(
                                items = groups,
                                key = { it.peerPhone.ifBlank { it.peerName } }
                            ) { group ->
                                val userKey = group.peerPhone.ifBlank { group.peerName }
                                val isExpanded = expandedUserKeys.contains(userKey)

                                StackedCallUserCard(
                                    group = group,
                                    isExpanded = isExpanded,
                                    onToggleExpand = { toggleExpand(userKey) },
                                    onVoiceCall = {
                                        requestCall(group.peerPhone, group.peerName, group.peerAvatar, false)
                                    },
                                    onVideoCall = {
                                        requestCall(group.peerPhone, group.peerName, group.peerAvatar, true)
                                    },
                                    onOpenChat = {
                                        onOpenChat?.invoke(group.peerPhone, group.peerName)
                                    },
                                    onDeleteHistory = {
                                        userToDeleteCalls = group
                                    },
                                    onRecallCall = { call ->
                                        requestCall(group.peerPhone, group.peerName, group.peerAvatar, call.isVideo)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal nouveau appel (sélection de contacts)
    if (showNewCallSheet) {
        NewCallContactsBottomSheet(
            contacts = convRepo.loadContacts(),
            onDismiss = { showNewCallSheet = false },
            onStartCall = { contact, isVideo ->
                showNewCallSheet = false
                requestCall(contact.phone, contact.name, contact.avatarPath, isVideo)
            }
        )
    }

    // Confirmation suppression de tout l'historique
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    stringResource(R.string.calls_clear_history),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    stringResource(R.string.calls_clear_confirm),
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        callLogRepo.clearAll()
                        refresh()
                        showClearDialog = false
                    }
                ) {
                    Text(
                        stringResource(R.string.calls_clear_history),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Confirmation suppression des appels pour un utilisateur spécifique
    userToDeleteCalls?.let { group ->
        AlertDialog(
            onDismissRequest = { userToDeleteCalls = null },
            title = {
                Text(
                    stringResource(R.string.calls_clear_user_history),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    "${group.peerName.ifBlank { group.peerPhone }} : ${group.calls.size} ${stringResource(R.string.calls_stacked_count, group.calls.size)}",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        callLogRepo.deleteCallsForUser(group.peerPhone)
                        userToDeleteCalls = null
                        refresh()
                    }
                ) {
                    Text(
                        stringResource(R.string.calls_delete_entry),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDeleteCalls = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Smart Recall Contextuel Nocturne (Module 2)
    pendingRecallCall?.let { call ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val suggestions = remember(call.phone) {
            com.sha.orbis.ai.suggestions.OrbisSuggestionLibrary.getDiverseDeclineSuggestions(
                context = context,
                peerId = call.phone
            )
        }
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val textPrimary = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
        val textSub = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
        val cardBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF0FDF4)
        val borderCol = if (isDark) Color(0xFF334155) else Color(0xFFBAE6FD)

        ModalBottomSheet(
            onDismissRequest = { pendingRecallCall = null },
            sheetState = sheetState,
            containerColor = if (isDark) Color(0xFF0F172A) else Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Off-hours / Night icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PhoneMissed,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.smart_recall_dialog_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.smart_recall_dialog_desc),
                    fontSize = 13.5.sp,
                    color = textSub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(18.dp))

                // Suggestions list in the peer's preferred language (FR, EN, AR, or DZ)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val sentText = suggestion.text
                                    pendingRecallCall = null
                                    coroutineScope.launch {
                                        try {
                                            NostrSyncManager.getInstance(context).sendDirectMessage(
                                                recipientNpubOrHex = call.phone,
                                                conversationId = call.phone,
                                                text = sentText
                                            )
                                            Toast.makeText(context, context.getString(R.string.smart_recall_msg_sent), Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) {}
                                    }
                                },
                            color = cardBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, borderCol)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = suggestion.icon,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = suggestion.text,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Actions: Call anyway or Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = { pendingRecallCall = null },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = textSub,
                            fontSize = 14.sp
                        )
                    }

                    Button(
                        onClick = {
                            val c = call
                            pendingRecallCall = null
                            startCall(c.phone, c.name, c.avatar, c.isVideo)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(
                            imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.smart_recall_btn_call_anyway),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Carte empilée souveraine et élégante pour un utilisateur :
 * - Regroupe tous les appels passés avec cet utilisateur.
 * - Au clic, se déplie avec animation pour révéler la liste de chaque appel individuel.
 */
@Composable
private fun StackedCallUserCard(
    group: CallGroup,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    onOpenChat: () -> Unit,
    onDeleteHistory: () -> Unit,
    onRecallCall: (CallRecord) -> Unit
) {
    val context = LocalContext.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "chevronRotation"
    )

    val hasMissed = group.hasMissed
    val totalCalls = group.calls.size
    val latestCall = group.latestCall
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    var showBlockDialog by remember { mutableStateOf(false) }

    val callsAccent = Color(0xFF10B981)
    val callsBorderLight = Color(0xFF10B981).copy(alpha = 0.22f)
    val callsBorderDark = Color(0xFF10B981).copy(alpha = 0.35f)
    val callsTextDark = Color(0xFFF1F5F9)
    val callsTextLight = Color(0xFF0F172A)
    val callsSubtextDark = Color(0xFF94A3B8)
    val callsSubtextLight = Color(0xFF64748B)

    // Dégradé vert élégant cloné de la carte Cercle Famille du feed
    val cardGradient = if (isDark) {
        androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF10B981).copy(alpha = 0.14f),
                MaterialTheme.colorScheme.surface
            )
        )
    } else {
        androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF10B981).copy(alpha = 0.08f),
                MaterialTheme.colorScheme.surface
            )
        )
    }

    val cardBorder = BorderStroke(
        1.dp,
        if (isDark) callsBorderDark else callsBorderLight
    )

    val primaryText = if (isDark) callsTextDark else callsTextLight
    val subtext = if (isDark) callsSubtextDark else callsSubtextLight

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 400f
                )
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = cardBorder,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Ligne principale de la carte empilée
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleExpand)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Avatar avec pastille du type d'appel
                    Box(modifier = Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                        OrbisAvatar(
                            avatarPath = group.peerAvatar,
                            name = group.peerName,
                            size = 46.dp
                        )
                        // Mini pastille indiquant le type du dernier appel
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 2.dp, y = 2.dp)
                                .size(17.dp)
                                .clip(CircleShape)
                                .background(if (latestCall.isVideo) callsAccent else Color(0xFF10B981)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (latestCall.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // 2. Nom, résumé du dernier appel et badge empilé
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = group.peerName.ifBlank { group.peerPhone },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            // Call Guard Trust Badge (placé à côté du nom, compact et garanti sur une seule ligne)
                            CallGuardBadge(
                                threatLevel = latestCall.threatLevel,
                                trustScore = latestCall.trustScore,
                                compact = true
                            )
                        }

                        // Direction & heure du dernier appel
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val (dirIcon, dirTint) = when (latestCall.direction) {
                                CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to Color(0xFF10B981)
                                CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to Color(0xFF10B981)
                                CallDirection.MISSED   -> Icons.AutoMirrored.Filled.CallMissed to Color(0xFFEF4444)
                            }
                            Icon(
                                imageVector = dirIcon,
                                contentDescription = null,
                                tint = dirTint,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = formatCallTime(context, latestCall.timestamp),
                                fontSize = 12.sp,
                                color = subtext
                            )
                            if (latestCall.durationSeconds > 0) {
                                Text("•", fontSize = 10.sp, color = subtext)
                                Text(
                                    text = formatCallDuration(context, latestCall.durationSeconds),
                                    fontSize = 12.sp,
                                    color = subtext,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Badge empilé : total des appels du contact
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(callsAccent.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = if (totalCalls > 1) {
                                        stringResource(R.string.calls_stacked_count, totalCalls)
                                    } else {
                                        stringResource(R.string.calls_stacked_single)
                                    },
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = callsAccent
                                )
                                if (group.missedCount > 0) {
                                    Text(
                                        text = "• " + stringResource(R.string.calls_stacked_missed_count, group.missedCount),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }

                // 3. Actions rapides (Audio vert, Vidéo bleu, Chevron rotatif)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onVoiceCall,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = stringResource(R.string.calls_voice_call),
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onVideoCall,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(callsAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = stringResource(R.string.calls_video_call),
                                tint = callsAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) {
                                stringResource(R.string.calls_collapse_history)
                            } else {
                                stringResource(R.string.calls_expand_history, totalCalls)
                            },
                            tint = subtext,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation)
                        )
                    }
                }
            }

            // Section dépliable : historique détaillé de tous les appels avec ce contact
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    HorizontalDivider(
                        color = if (isDark) callsBorderDark else callsBorderLight,
                        thickness = 0.6.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // En-tête de section dépliée avec actions contextuelles
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.calls_expand_history, totalCalls).uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = callsAccent,
                            letterSpacing = 0.6.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = onOpenChat,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = callsAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.tab_chats),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = callsAccent
                                )
                            }
                            TextButton(
                                onClick = onDeleteHistory,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.calls_delete_entry),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            TextButton(
                                onClick = { showBlockDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.call_guard_action_block_report),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Liste détaillée de chaque appel
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        group.calls.forEachIndexed { index, call ->
                            CallItemDetailRow(
                                call = call,
                                context = context,
                                callsAccent = callsAccent,
                                isDark = isDark,
                                textPrimary = primaryText,
                                textSub = subtext,
                                onRecall = { onRecallCall(call) }
                            )
                            if (index < group.calls.size - 1) {
                                HorizontalDivider(
                                    color = (if (isDark) callsBorderDark else callsBorderLight).copy(alpha = 0.5f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

    if (showBlockDialog) {
        val blockSuccessMsg = stringResource(R.string.call_guard_block_success)
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.call_guard_block_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.call_guard_block_dialog_desc, group.peerName.ifBlank { group.peerPhone })
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockDialog = false
                        com.sha.orbis.storage.BlockedContactsRepository(context).blockContact(
                            phone = group.peerPhone,
                            name = group.peerName
                        )
                        Toast.makeText(context, blockSuccessMsg, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.call_guard_action_block_report),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Ligne individuelle pour chaque appel dans la carte dépliée.
 */
@Composable
private fun CallItemDetailRow(
    call: CallRecord,
    context: Context,
    callsAccent: Color,
    isDark: Boolean,
    textPrimary: Color,
    textSub: Color,
    onRecall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            val (dirIcon, dirTint) = when (call.direction) {
                CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to Color(0xFF10B981)
                CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to Color(0xFF10B981)
                CallDirection.MISSED   -> Icons.AutoMirrored.Filled.CallMissed to Color(0xFFEF4444)
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(dirTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = dirIcon,
                    contentDescription = null,
                    tint = dirTint,
                    modifier = Modifier.size(15.dp)
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val dirLabel = when (call.direction) {
                        CallDirection.OUTGOING -> stringResource(R.string.calls_outgoing)
                        CallDirection.INCOMING -> stringResource(R.string.calls_incoming)
                        CallDirection.MISSED   -> stringResource(R.string.calls_missed)
                    }
                    val typeLabel = if (call.isVideo) stringResource(R.string.calls_video_call) else stringResource(R.string.calls_voice_call)
                    Text(
                        text = "$dirLabel • $typeLabel",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (call.direction == CallDirection.MISSED) Color(0xFFEF4444) else textPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy • HH:mm:ss", Locale.getDefault()).format(Date(call.timestamp)),
                        fontSize = 11.sp,
                        color = textSub
                    )
                    if (call.durationSeconds > 0) {
                        Text("•", fontSize = 10.sp, color = textSub)
                        Text(
                            text = formatCallDuration(context, call.durationSeconds),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSub
                        )
                    } else if (call.direction == CallDirection.MISSED) {
                        Text("•", fontSize = 10.sp, color = Color(0xFFEF4444))
                        Text(
                            text = stringResource(R.string.calls_unanswered),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }

        // Bouton de rappel instantané adapté au type (audio vert ou vidéo bleu)
        IconButton(
            onClick = onRecall,
            modifier = Modifier.size(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (call.isVideo) callsAccent.copy(alpha = 0.15f)
                        else Color(0xFF10B981).copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = stringResource(R.string.calls_call_back),
                    tint = if (call.isVideo) callsAccent else Color(0xFF10B981),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun CallDateHeader(label: String) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val callsBorderLight = Color(0xFFBAE6FD)
    val callsBorderDark = Color(0xFF2B4C7E)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = if (isDark) callsBorderDark else callsBorderLight,
            thickness = 0.6.dp
        )
        Text(
            text = label.uppercase(),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            letterSpacing = 0.8.sp
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = if (isDark) callsBorderDark else callsBorderLight,
            thickness = 0.6.dp
        )
    }
}

@Composable
private fun CallsEmptyState(isMissedOnly: Boolean, onStartNewCall: () -> Unit) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val callsAccent = Color(0xFF0284C7)
    val callsCardBgLight = Color(0xFFE0F2FE)
    val callsCardBgDark = Color(0xFF1E3A5F)
    val callsBorderLight = Color(0xFFBAE6FD)
    val callsBorderDark = Color(0xFF2B4C7E)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (isDark) callsCardBgDark else callsCardBgLight)
                    .border(1.dp, if (isDark) callsBorderDark else callsBorderLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMissedOnly) Icons.AutoMirrored.Filled.PhoneMissed else Icons.Default.Call,
                    contentDescription = null,
                    tint = callsAccent,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.calls_empty_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.calls_empty_desc),
                fontSize = 13.5.sp,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = callsAccent,
                shadowElevation = 2.dp,
                modifier = Modifier.clickable(onClick = onStartNewCall)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(R.string.calls_new_call),
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CallFilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    isMissedBadge: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val callsAccent = Color(0xFF10B981)
    val callsSubtextDark = Color(0xFFCBD5E1)
    val callsSubtextLight = Color(0xFF475569)

    // Arrière-plan constant et épuré : aucun basculement vers un gris terne
    val chipBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.45f) else Color(0xFFF8FAFC)

    // Seule l'épaisseur de la bordure verte s'agrandit considérablement (2.5dp vs 1dp) sans casser le visuel
    val activeBorder = if (isSelected) {
        BorderStroke(2.5.dp, callsAccent)
    } else {
        BorderStroke(1.dp, callsAccent.copy(alpha = if (isDark) 0.35f else 0.30f))
    }

    val activeColor = if (isSelected) {
        callsAccent
    } else {
        if (isDark) callsSubtextDark else callsSubtextLight
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = chipBg,
        border = activeBorder,
        shadowElevation = 0.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isMissedBadge && count > 0) Color(0xFFEF4444) else activeColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = activeColor,
                letterSpacing = 0.2.sp
            )
            if (count > 0) {
                val badgeBg = when {
                    isMissedBadge -> Color(0xFFEF4444)
                    isSelected -> callsAccent
                    else -> callsAccent.copy(alpha = if (isDark) 0.20f else 0.12f)
                }
                val badgeTextColor = when {
                    isMissedBadge || isSelected -> Color.White
                    else -> if (isDark) Color(0xFFA7F3D0) else callsAccent
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeBg)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (count > 99) "99+" else count.toString(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeTextColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewCallContactsBottomSheet(
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onStartCall: (Contact, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var contactQuery by remember { mutableStateOf("") }
    val filtered = remember(contacts, contactQuery) {
        if (contactQuery.isBlank()) contacts
        else contacts.filter {
            it.name.contains(contactQuery, ignoreCase = true) ||
            it.phone.contains(contactQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                stringResource(R.string.calls_new_call),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(4.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = contactQuery,
                onValueChange = { contactQuery = it },
                placeholder = { Text(stringResource(R.string.search_contact), fontSize = 13.5.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (contactQuery.isNotBlank()) {
                        IconButton(onClick = { contactQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                items(filtered, key = { it.phone }) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStartCall(contact, false) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OrbisAvatar(avatarPath = contact.avatarPath, name = contact.name, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                contact.name,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                contact.phone,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onStartCall(contact, false) }) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { onStartCall(contact, true) }) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 58.dp)
                    )
                }
            }
        }
    }
}

private fun buildDateSections(
    groups: List<CallGroup>,
    context: Context,
    locale: Locale
): List<Pair<String, List<CallGroup>>> {
    if (groups.isEmpty()) return emptyList()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 86_400_000L
    val weekStart = todayStart - 6 * 86_400_000L
    val map = linkedMapOf<String, MutableList<CallGroup>>()
    for (g in groups) {
        val key = when {
            g.lastTimestamp >= todayStart     -> context.getString(R.string.calls_section_today)
            g.lastTimestamp >= yesterdayStart -> context.getString(R.string.calls_section_yesterday)
            g.lastTimestamp >= weekStart      -> {
                val cal = Calendar.getInstance().apply { timeInMillis = g.lastTimestamp }
                SimpleDateFormat("EEEE", locale).format(cal.time)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            }
            else -> {
                val cal = Calendar.getInstance().apply { timeInMillis = g.lastTimestamp }
                SimpleDateFormat("d MMM", locale).format(cal.time)
            }
        }
        map.getOrPut(key) { mutableListOf() }.add(g)
    }
    return map.map { (k, v) -> k to v }
}

private fun formatCallTime(context: Context, timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L      -> context.getString(R.string.notif_time_just_now)
        diff < 3_600_000L   -> context.getString(R.string.notif_time_minutes_ago, (diff / 60_000L).coerceAtLeast(1))
        diff < 86_400_000L  -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < 172_800_000L -> context.getString(R.string.notif_time_yesterday)
        else                -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatCallDuration(context: Context, seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> context.getString(R.string.calls_duration_hours, h, m)
        m > 0 -> context.getString(R.string.calls_duration_mins, m, s)
        else  -> context.getString(R.string.calls_duration_secs, s)
    }
}
