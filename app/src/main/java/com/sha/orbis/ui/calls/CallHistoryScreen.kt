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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
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
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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
            // Barre d'onglets de filtrage et menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        onClick = { selectedFilter = CallFilterTab.ALL }
                    )
                    CallFilterChip(
                        label = stringResource(R.string.calls_filter_missed),
                        count = rawGroups.count { it.hasMissed },
                        isSelected = selectedFilter == CallFilterTab.MISSED,
                        isMissedBadge = true,
                        onClick = { selectedFilter = CallFilterTab.MISSED }
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
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
                                        fontSize = 14.sp
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
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.8.dp
            )

            var isRefreshingCalls by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

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
                                        startCall(group.peerPhone, group.peerName, group.peerAvatar, false)
                                    },
                                    onVideoCall = {
                                        startCall(group.peerPhone, group.peerName, group.peerAvatar, true)
                                    },
                                    onOpenChat = {
                                        onOpenChat?.invoke(group.peerPhone, group.peerName)
                                    },
                                    onDeleteHistory = {
                                        userToDeleteCalls = group
                                    },
                                    onRecallCall = { call ->
                                        startCall(group.peerPhone, group.peerName, group.peerAvatar, call.isVideo)
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
                startCall(contact.phone, contact.name, contact.avatarPath, isVideo)
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

    val cardBg = if (hasMissed) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    val cardBorder = if (hasMissed) {
        BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }

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
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder
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
                // 1. Avatar avec pastille superposée
                Box(modifier = Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                    OrbisAvatar(
                        avatarPath = group.peerAvatar,
                        name = group.peerName,
                        size = 46.dp
                    )
                    if (group.missedCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (group.missedCount > 9) "9+" else group.missedCount.toString(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                    // Mini pastille indiquant le type du dernier appel
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(if (latestCall.isVideo) MaterialTheme.colorScheme.primary else Color(0xFF10B981)),
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
                    Text(
                        text = group.peerName.ifBlank { group.peerPhone },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasMissed) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (latestCall.durationSeconds > 0) {
                            Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = formatCallDuration(context, latestCall.durationSeconds),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Badge empilé : total des appels du contact
                    Row(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
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
                            color = MaterialTheme.colorScheme.primary
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
                                .background(Color(0xFF10B981).copy(alpha = 0.12f)),
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
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = stringResource(R.string.calls_video_call),
                                tint = MaterialTheme.colorScheme.primary,
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
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
                            color = MaterialTheme.colorScheme.primary,
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
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.tab_chats),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2563EB)
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
                                onRecall = { onRecallCall(call) }
                            )
                            if (index < group.calls.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
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

/**
 * Ligne individuelle pour chaque appel dans la carte dépliée.
 */
@Composable
private fun CallItemDetailRow(
    call: CallRecord,
    context: Context,
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
                        color = if (call.direction == CallDirection.MISSED) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy • HH:mm:ss", Locale.getDefault()).format(Date(call.timestamp)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (call.durationSeconds > 0) {
                        Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = formatCallDuration(context, call.durationSeconds),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.outline
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
                        if (call.isVideo) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else Color(0xFF10B981).copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = stringResource(R.string.calls_call_back),
                    tint = if (call.isVideo) MaterialTheme.colorScheme.primary else Color(0xFF10B981),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun CallDateHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 0.6.dp
        )
        Text(
            text = label.uppercase(),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            letterSpacing = 0.8.sp
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 0.6.dp
        )
    }
}

@Composable
private fun CallsEmptyState(isMissedOnly: Boolean, onStartNewCall: () -> Unit) {
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMissedOnly) Icons.AutoMirrored.Filled.PhoneMissed else Icons.Default.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.calls_empty_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.calls_empty_desc),
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
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
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(R.string.calls_new_call),
                        color = MaterialTheme.colorScheme.onPrimary,
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
    onClick: () -> Unit
) {
    val activeBg    = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val activeColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = activeBg,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = activeColor
            )
            if (count > 0) {
                val badgeColor = if (isMissedBadge) Color(0xFFEF4444) else activeColor
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        count.toString(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
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
