package com.sha.orbis.ui.app

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.PushPin
import com.sha.orbis.model.MessageDeliveryStatus
import com.sha.orbis.ui.theme.OrbisColorPalette

import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.AccountProfile
import com.sha.orbis.ui.components.AvatarManager
import com.sha.orbis.model.Contact
import com.sha.orbis.model.Conversation
import com.sha.orbis.notification.OrbisEventBus
import com.sha.orbis.permissions.PermissionGate
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.storage.PrivateConversationRepository
import com.sha.orbis.storage.SocialRepository
import com.sha.orbis.social.SocialStory
import com.sha.orbis.ui.auth.AuthScreen
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.components.OrbisTopHeader
import com.sha.orbis.ui.contacts.ContactsScreen
import com.sha.orbis.ui.conversation.ConversationScreen
import com.sha.orbis.ui.conversation.PrivateChatPasswordSetupDialog
import com.sha.orbis.ui.conversation.PrivateConversationsScreen
import com.sha.orbis.ui.groups.CreateGroupScreen
import com.sha.orbis.ui.settings.SettingsScreen
import com.sha.orbis.ui.social.SocialStoriesBar
import com.sha.orbis.ui.social.TimelineScreen
import com.sha.orbis.media.MediaAttachmentHelper
import com.sha.orbis.media.VideoMediaHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.collectAsState
import com.sha.orbis.data.OrbisBadgeHub
import com.sha.orbis.ui.components.OrbisPremiumBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbisApp(
    initialConvId: String? = null,
    initialPhone: String? = null,
    initialTitle: String? = null,
    initialPostId: String? = null,
    initialActionType: String? = null
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    var currentAccountId by remember { mutableStateOf(sessionManager.activeAccountId) }
    var convRepository by remember(currentAccountId) { mutableStateOf(ConversationRepository(context, currentAccountId)) }
    val socialRepo = remember(currentAccountId) { SocialRepository(context, currentAccountId) }
    var stories by remember(currentAccountId) { mutableStateOf(socialRepo.loadStories()) }

    fun refreshChatStories() { stories = socialRepo.loadStories() }

    var targetSocialPostId by remember { mutableStateOf(initialPostId) }
    var targetSocialActionType by remember { mutableStateOf(initialActionType) }

    // REAL Conversations only (scoped to active account, auto-syncing accepted friends)
    var conversations by remember(currentAccountId) {
        mutableStateOf(convRepository.syncConnectedFriendsToConversations(context.getString(R.string.friends_connected_last_msg)))
    }

    val badgeSnapshot by OrbisBadgeHub.badgeState.collectAsState()

    var selectedTab by remember {
        mutableIntStateOf(
            if (!initialConvId.isNullOrBlank()) 1
            else 0
        )
    }
    var activeConversation by remember {
        mutableStateOf<Conversation?>(
            if (!initialConvId.isNullOrBlank()) {
                val saved = convRepository.loadConversations().find { it.id == initialConvId }
                saved ?: Conversation(
                    id = initialConvId,
                    title = initialTitle ?: "Discussion",
                    participants = listOf("me", initialPhone ?: ""),
                    lastMessage = "",
                    updatedAt = System.currentTimeMillis()
                )
            } else null
        )
    }
    var unlockedPrivateConversationIds by remember(currentAccountId) { mutableStateOf<Set<String>>(emptySet()) }
    var showNewChatSheet by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    var targetSlotForNewAccount by remember { mutableStateOf<Int?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val notifRepository = remember(context) { com.sha.orbis.storage.NotificationRepository(context) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var showFriendRequestsModal by remember { mutableStateOf(false) }
    var showAdminConsole by remember { mutableStateOf(false) }
    var showProfileScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showPrivateConversationsScreen by remember { mutableStateOf(false) }
    var activeUserWallState by remember { mutableStateOf<com.sha.orbis.ui.components.FriendProfilePreviewDialogState?>(null) }
    var isPinUnlocked by remember { mutableStateOf(!com.sha.orbis.security.DuressSecurityManager.isPinEnabled(context)) }
    val currentCallSession by com.sha.orbis.call.OrbisCallManager.callState.collectAsState()
    val callFeedback by com.sha.orbis.call.OrbisCallManager.callFeedback.collectAsState()
    var pendingAcceptVideo by remember { mutableStateOf<Boolean?>(null) }

    val recordAudioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            com.sha.orbis.call.OrbisAudioStreamer.onRecordAudioPermissionGranted(context)
            if (pendingAcceptVideo == false) {
                com.sha.orbis.call.OrbisCallManager.acceptCall(withVideo = false)
                pendingAcceptVideo = null
            }
        } else if (pendingAcceptVideo == false) {
            pendingAcceptVideo = null
        }
    }

    val callPermissionsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        if (map[android.Manifest.permission.RECORD_AUDIO] == true) {
            com.sha.orbis.call.OrbisAudioStreamer.onRecordAudioPermissionGranted(context)
        }
        if (pendingAcceptVideo == true && PermissionGate.hasCallPermissions(context, isVideo = true)) {
            com.sha.orbis.call.OrbisCallManager.acceptCall(withVideo = true)
        }
        pendingAcceptVideo = null
    }

    fun acceptIncomingCall(withVideo: Boolean) {
        pendingAcceptVideo = withVideo
        val needed = PermissionGate.requiredCallPermissions(context, isVideo = withVideo)
        if (needed.isEmpty()) {
            com.sha.orbis.call.OrbisCallManager.acceptCall(withVideo = withVideo)
            pendingAcceptVideo = null
        } else if (withVideo) {
            callPermissionsLauncher.launch(needed)
        } else {
            recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    fun navigateToEncryptedChat(convId: String, phone: String? = null, title: String? = null) {
        showNewChatSheet = false
        showCreateGroup = false
        showNotificationCenter = false
        showFriendRequestsModal = false
        showAdminConsole = false
        showProfileScreen = false
        showPrivateConversationsScreen = false
        selectedTab = 1
        val saved = convRepository.loadConversations().find { it.id == convId }
        activeConversation = saved ?: Conversation(
            id = convId,
            title = title ?: phone ?: "Discussion",
            participants = listOf("me", phone ?: ""),
            lastMessage = "",
            updatedAt = System.currentTimeMillis()
        )
    }

    fun navigateToSocialPost(postId: String, actionType: String?) {
        showNewChatSheet = false
        showCreateGroup = false
        showNotificationCenter = false
        showFriendRequestsModal = false
        showAdminConsole = false
        showProfileScreen = false
        showPrivateConversationsScreen = false
        activeUserWallState = null
        activeConversation = null
        selectedTab = 0
        targetSocialPostId = postId
        targetSocialActionType = actionType
    }

    val updateManager = remember { com.sha.orbis.update.AppUpdateManager.getInstance(context) }
    val updateStatus by updateManager.updateStatus.collectAsState()
    val telemetryManager = remember { com.sha.orbis.telemetry.TelemetryManager.getInstance(context) }

    LaunchedEffect(Unit) {
        com.sha.orbis.call.OrbisCallManager.init(context)
        // 1. Enregistre le lancement de l'application
        telemetryManager.recordEvent(com.sha.orbis.telemetry.FeatureType.APP_LAUNCH)
        // 2. Transmet immédiatement la télémétrie et rafraîchit la config dynamique (Force Update)
        telemetryManager.syncTelemetryAsync()
        // 3. Planifie la synchronisation périodique de fond (6h)
        telemetryManager.schedulePeriodicSync()
        // 4. Vérification de secours de la configuration distante
        updateManager.checkRemoteConfig(telemetryManager.backendUrl)
        com.sha.orbis.notification.InAppNotificationManager.onNavigateToConversation = { convId, phone, _ ->
            if (!convId.isNullOrBlank()) {
                navigateToEncryptedChat(convId, phone)
            }
        }
    }

    LaunchedEffect(initialConvId) {
        if (!initialConvId.isNullOrBlank()) {
            navigateToEncryptedChat(initialConvId, initialPhone, initialTitle)
        }
    }

    LaunchedEffect(initialPostId) {
        if (!initialPostId.isNullOrBlank()) {
            navigateToSocialPost(initialPostId, initialActionType)
        }
    }

    LaunchedEffect(currentAccountId) {
        OrbisBadgeHub.refresh(context, currentAccountId)
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            OrbisBadgeHub.markSocialRead(context)
        } else {
            OrbisBadgeHub.refresh(context, currentAccountId)
        }
    }

    if (!isPinUnlocked && com.sha.orbis.security.DuressSecurityManager.isPinEnabled(context)) {
        com.sha.orbis.ui.auth.PinLockScreen(
            onUnlockSuccess = { isDecoy ->
                isPinUnlocked = true
                if (isDecoy) {
                    conversations = emptyList()
                }
            }
        )
        return
    }

    // Blocage strict en cas de mise à jour obligatoire (Force Update)
    if (updateStatus is com.sha.orbis.update.UpdateStatus.ForceUpdateRequired) {
        val forcedConfig = (updateStatus as com.sha.orbis.update.UpdateStatus.ForceUpdateRequired).config
        com.sha.orbis.ui.update.ForceUpdateDialog(config = forcedConfig)
        return
    }

    if (currentCallSession != null) {
        val session = currentCallSession!!
        if (session.isVideoCall) {
            com.sha.orbis.ui.call.OrbisVideoCallScreen(
                session = session,
                onAcceptVideoCall = {
                    acceptIncomingCall(withVideo = true)
                },
                onAcceptVoiceOnly = {
                    acceptIncomingCall(withVideo = false)
                },
                onDeclineCall = { com.sha.orbis.call.OrbisCallManager.rejectCall() },
                onToggleCamera = { com.sha.orbis.call.OrbisCallManager.toggleCamera() },
                onSwitchCamera = { com.sha.orbis.call.OrbisCallManager.switchCamera() },
                onToggleMute = { com.sha.orbis.call.OrbisCallManager.toggleMute() },
                onToggleSpeaker = { com.sha.orbis.call.OrbisCallManager.toggleSpeaker() },
                onEndCall = { com.sha.orbis.call.OrbisCallManager.endCall() }
            )
        } else {
            com.sha.orbis.ui.call.OrbisVoiceCallScreen(
                session = session,
                onAcceptCall = {
                    acceptIncomingCall(withVideo = false)
                },
                onDeclineCall = { com.sha.orbis.call.OrbisCallManager.rejectCall() },
                onToggleMute = { com.sha.orbis.call.OrbisCallManager.toggleMute() },
                onToggleSpeaker = { com.sha.orbis.call.OrbisCallManager.toggleSpeaker() },
                onEndCall = { com.sha.orbis.call.OrbisCallManager.endCall() }
            )
        }
        return
    }

    val tabs = listOf(
        NavigationTabItem(stringResource(R.string.tab_timeline), Icons.Default.Public),
        NavigationTabItem(stringResource(R.string.tab_chats), Icons.AutoMirrored.Filled.Chat),
        NavigationTabItem(stringResource(R.string.tab_calls), Icons.Default.Call),
        NavigationTabItem(stringResource(R.string.tab_contacts), Icons.Default.People)
    )

    var triggerCreatePostInFeed by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var isRefreshingConversations by remember { mutableStateOf(false) }

    // Listen for incoming SMS/events to reload conversations and stories in real time
    DisposableEffect(currentAccountId) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == OrbisEventBus.ACTION_ORBIS_STORY_RECEIVED) {
                    refreshChatStories()
                } else {
                    conversations = convRepository.syncConnectedFriendsToConversations(
                        defaultMessage = ctx?.getString(R.string.friends_connected_last_msg) ?: "Connecté 🛡️"
                    )
                    OrbisBadgeHub.refresh(context, currentAccountId)
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS)
            addAction(OrbisEventBus.ACTION_ORBIS_STORY_RECEIVED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(currentAccountId) {
        conversations = convRepository.syncConnectedFriendsToConversations(
            defaultMessage = context.getString(R.string.friends_connected_last_msg)
        )
        OrbisBadgeHub.refresh(context, currentAccountId)
        refreshChatStories()
    }

    LaunchedEffect(selectedTab) {
        refreshChatStories()
        if (selectedTab == 1) {
            conversations = convRepository.syncConnectedFriendsToConversations(
                defaultMessage = context.getString(R.string.friends_connected_last_msg)
            )
            OrbisBadgeHub.refresh(context, currentAccountId)
        }
    }

    // Add Second Line Onboarding Sub-Flow
    if (targetSlotForNewAccount != null) {
        AuthScreen(
            targetSimSlotIndex = targetSlotForNewAccount!!,
            onAuthenticate = {
                currentAccountId = sessionManager.activeAccountId
                convRepository = ConversationRepository(context, currentAccountId)
                conversations = convRepository.loadConversations()
                targetSlotForNewAccount = null
                Toast.makeText(context, context.getString(R.string.account_created_success), Toast.LENGTH_LONG).show()
            },
            onCancel = {
                targetSlotForNewAccount = null
            }
        )
        return
    }

    LaunchedEffect(activeConversation) {
        if (activeConversation != null) {
            val otherParticipant = activeConversation!!.participants.firstOrNull { it != "me" && it != sessionManager.userPhone } ?: ""
            com.sha.orbis.notification.ActiveConversationTracker.setActiveConversation(activeConversation!!.id, otherParticipant)
            OrbisBadgeHub.markConversationRead(context, activeConversation!!.id, currentAccountId)
            conversations = convRepository.loadConversations()
        } else {
            com.sha.orbis.notification.ActiveConversationTracker.clearActiveConversation()
        }
    }

    if (activeUserWallState != null) {
        com.sha.orbis.ui.profile.UserProfileWallScreen(
            userPhone = activeUserWallState!!.phone,
            initialPseudo = activeUserWallState!!.pseudo,
            initialAvatarPath = activeUserWallState!!.avatarPath,
            initialRole = activeUserWallState!!.role,
            onBack = {
                conversations = convRepository.loadConversations()
                activeUserWallState = null
            },
            onOpenChat = { phone, name ->
                activeUserWallState = null
                showProfileScreen = false
                showSettingsScreen = false
                showNotificationCenter = false
                showFriendRequestsModal = false
                val conv = Conversation(
                    id = "conv_${phone.filter { it.isDigit() }}",
                    title = name,
                    participants = listOf("me", phone),
                    lastMessage = "",
                    updatedAt = System.currentTimeMillis()
                )
                val existing = convRepository.loadConversations().toMutableList()
                if (existing.none { it.id == conv.id }) {
                    existing.add(0, conv)
                    convRepository.saveConversations(existing)
                }
                conversations = convRepository.loadConversations()
                activeConversation = conv
            },
            onNavigateToPeerWall = { phone, pseudo, avatar, role ->
                activeUserWallState = com.sha.orbis.ui.components.FriendProfilePreviewDialogState(
                    phone,
                    pseudo,
                    avatar,
                    role
                )
            }
        )
        return
    }

    if (activeConversation != null) {
        val currentConversation = activeConversation!!
        val privateRepo = remember(context, currentAccountId) { PrivateConversationRepository(context, currentAccountId) }
        if (privateRepo.isLocked(currentConversation.id) && currentConversation.id !in unlockedPrivateConversationIds) {
            activeConversation = null
            showPrivateConversationsScreen = true
            return
        }

        val otherParticipant = currentConversation.participants.firstOrNull { it != "me" && it != sessionManager.userPhone } ?: ""
        androidx.compose.runtime.key(currentAccountId) {
            ConversationScreen(
                conversationTitle = currentConversation.title,
                conversationId = currentConversation.id,
                recipientPhone = otherParticipant,
                onBack = {
                    conversations = convRepository.loadConversations()
                    OrbisBadgeHub.markConversationRead(context, currentConversation.id, currentAccountId)
                    unlockedPrivateConversationIds = unlockedPrivateConversationIds - currentConversation.id
                    activeConversation = null
                },
                onOpenWall = { phone, pseudo, avatar, role ->
                    activeUserWallState = com.sha.orbis.ui.components.FriendProfilePreviewDialogState(phone, pseudo, avatar, role)
                }
            )
        }
        return
    }

    if (showPrivateConversationsScreen) {
        PrivateConversationsScreen(
            currentAccountId = currentAccountId,
            conversations = convRepository.loadConversations(),
            onBack = {
                conversations = convRepository.loadConversations()
                showPrivateConversationsScreen = false
            },
            onOpenConversation = { conv ->
                showPrivateConversationsScreen = false
                unlockedPrivateConversationIds = unlockedPrivateConversationIds + conv.id
                activeConversation = conv
            }
        )
        return
    }

    if (showCreateGroup) {
        CreateGroupScreen(
            onGroupCreated = { newConv ->
                conversations = convRepository.loadConversations()
                OrbisBadgeHub.refresh(context, currentAccountId)
                showCreateGroup = false
                if (newConv != null) {
                    activeConversation = newConv
                }
            },
            onBack = {
                showCreateGroup = false
            }
        )
        return
    }

    if (showAccountSwitcher) {
        AccountSwitchBottomSheet(
            onDismiss = { showAccountSwitcher = false },
            onAccountSwitched = { newAccount ->
                currentAccountId = newAccount.id
                activeConversation = null
                activeUserWallState = null
                convRepository = ConversationRepository(context, newAccount.id)
                conversations = convRepository.syncConnectedFriendsToConversations(
                    defaultMessage = context.getString(R.string.friends_connected_last_msg)
                )
                OrbisBadgeHub.refresh(context, newAccount.id)
                try {
                    com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).reconnect(force = true)
                } catch (_: Exception) {}
                Toast.makeText(context, context.getString(R.string.account_switched_to, newAccount.name, newAccount.operatorName), Toast.LENGTH_SHORT).show()
            },
            onAddNewAccount = { slotIndex ->
                targetSlotForNewAccount = slotIndex
            }
        )
    }

    if (callFeedback != null && currentCallSession == null) {
        com.sha.orbis.ui.call.OrbisCallFeedbackDialog(
            feedback = callFeedback!!,
            onDismiss = {
                com.sha.orbis.call.OrbisCallManager.clearCallFeedback()
            },
            onOpenChat = { phone ->
                com.sha.orbis.call.OrbisCallManager.clearCallFeedback()
                val digits = phone.filter { it.isDigit() }
                val convId = if (digits.isNotBlank()) "conv_$digits" else "conv_${phone.take(16)}"
                navigateToEncryptedChat(convId, phone)
            }
        )
    }

    if (showNewChatSheet) {
        com.sha.orbis.ui.chat.SelectContactScreen(
            onBack = { showNewChatSheet = false },
            onStartDirectChat = { conv ->
                val existing = convRepository.loadConversations().toMutableList()
                if (existing.none { it.id == conv.id }) {
                    existing.add(0, conv)
                    convRepository.saveConversations(existing)
                }
                conversations = convRepository.loadConversations()
                showNewChatSheet = false
                activeConversation = conv
            },
            onCreateGroupClick = {
                showNewChatSheet = false
                showCreateGroup = true
            }
        )
        return
    }

    if (showFriendRequestsModal) {
        androidx.compose.runtime.key(currentAccountId) {
            com.sha.orbis.ui.social.FriendRequestsScreen(
                currentAccountId = currentAccountId,
                onBack = {
                    showFriendRequestsModal = false
                    conversations = convRepository.loadConversations()
                    OrbisBadgeHub.refresh(context, currentAccountId)
                }
            )
        }
        return
    }

    if (showNotificationCenter) {
        androidx.compose.runtime.key(currentAccountId) {
            com.sha.orbis.ui.notifications.NotificationCenterScreen(
                currentAccountId = currentAccountId,
                onBack = {
                    showNotificationCenter = false
                    OrbisBadgeHub.refresh(context, currentAccountId)
                },
                onOpenConversation = { convId, phone, name ->
                    showNotificationCenter = false
                    showFriendRequestsModal = false
                    showAdminConsole = false
                    showProfileScreen = false
                    activeUserWallState = null
                    val allConvs = convRepository.loadConversations()
                    val target = allConvs.firstOrNull { !convId.isNullOrBlank() && it.id == convId }
                        ?: allConvs.firstOrNull { !phone.isNullOrBlank() && (it.participants.contains(phone) || FriendRequestRepository.isSamePhone(it.id.removePrefix("conv_"), phone)) }
                    if (target != null) {
                        activeConversation = target
                    } else if (!phone.isNullOrBlank()) {
                        val cleanPhone = phone.filter { it.isDigit() }
                        val newConv = Conversation(
                            id = "conv_$cleanPhone",
                            title = name?.takeIf { it.isNotBlank() && !it.startsWith("npub1") && !it.startsWith("+") } ?: phone,
                            participants = listOf("me", phone),
                            lastMessage = "",
                            updatedAt = System.currentTimeMillis()
                        )
                        val existing = convRepository.loadConversations().toMutableList()
                        if (existing.none { it.id == newConv.id }) {
                            existing.add(0, newConv)
                            convRepository.saveConversations(existing)
                        }
                        conversations = convRepository.loadConversations()
                        activeConversation = newConv
                    } else {
                        selectedTab = 1
                    }
                    OrbisBadgeHub.refresh(context, currentAccountId)
                },
                onOpenFriendRequests = {
                    showNotificationCenter = false
                    activeConversation = null
                    activeUserWallState = null
                    showFriendRequestsModal = true
                    OrbisBadgeHub.refresh(context, currentAccountId)
                },
                onOpenPost = { postId, actionType ->
                    navigateToSocialPost(postId, actionType)
                    OrbisBadgeHub.refresh(context, currentAccountId)
                },
                onOpenSettings = {
                    showNotificationCenter = false
                    activeConversation = null
                    activeUserWallState = null
                    showSettingsScreen = true
                    OrbisBadgeHub.refresh(context, currentAccountId)
                }
            )
        }
        return
    }

    if (showAdminConsole) {
        com.sha.orbis.ui.admin.AdminConsoleScreen(
            onBack = { showAdminConsole = false }
        )
        return
    }

    if (showProfileScreen) {
        com.sha.orbis.ui.profile.ProfileScreen(
            onBack = {
                conversations = convRepository.loadConversations()
                showProfileScreen = false
            },
            onLogout = {
                sessionManager.isAuthenticated = false
                showProfileScreen = false
            },
            onDeleteAccount = {
                val accId = sessionManager.activeAccountId
                sessionManager.deleteAccountCompletely(accId)
                conversations = emptyList()
                showProfileScreen = false
            },
            onOpenWall = { phone, pseudo, avatar, role ->
                activeUserWallState = com.sha.orbis.ui.components.FriendProfilePreviewDialogState(phone, pseudo, avatar, role)
            }
        )
        return
    }

    if (showSettingsScreen) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            OrbisTopHeader(
                title = stringResource(R.string.tab_settings),
                onBack = { showSettingsScreen = false }
            )
            SettingsScreen(
                onOpenAccountSwitcher = { showAccountSwitcher = true },
                onOpenAdminConsole = { showAdminConsole = true },
                onOpenProfile = { showProfileScreen = true },
                onOpenWall = { phone, pseudo, avatar, role ->
                    activeUserWallState = com.sha.orbis.ui.components.FriendProfilePreviewDialogState(phone, pseudo, avatar, role)
                },
                onLogout = { sessionManager.isAuthenticated = false }
            )
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OrbisTopHeader(
                title = when (selectedTab) {
                    0 -> stringResource(R.string.tab_timeline)
                    1 -> stringResource(R.string.tab_chats)
                    2 -> stringResource(R.string.tab_calls)
                    3 -> stringResource(R.string.tab_contacts)
                    else -> stringResource(R.string.tab_timeline)
                },
                activeAccount = sessionManager.activeAccount,
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                searchPlaceholder = when (selectedTab) {
                    3 -> stringResource(R.string.search_contact)
                    else -> stringResource(R.string.search_conversations)
                },
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = if (selectedTab == 0 || selectedTab == 2) null else {
                    {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }
                },
                onOpenAccountSwitcher = { showAccountSwitcher = true },
                onOpenNotifications = { showNotificationCenter = true },
                unreadNotificationCount = badgeSnapshot.unreadNotifications,
                onOpenSettings = { showSettingsScreen = true },
                onTitleLongClick = if (selectedTab == 1) {
                    {
                        isSearchActive = false
                        searchQuery = ""
                        conversations = convRepository.loadConversations()
                        showPrivateConversationsScreen = true
                    }
                } else null,
                onOpenMyWall = {
                    val myRole = com.sha.orbis.admin.AdminSecurityHelper.getUserSocialRole(sessionManager.userPhone, context)
                    activeUserWallState = com.sha.orbis.ui.components.FriendProfilePreviewDialogState(
                        phone = sessionManager.userPhone,
                        pseudo = sessionManager.userName.ifBlank { "Moi" },
                        avatarPath = sessionManager.userAvatarPath,
                        role = myRole
                    )
                },
                userAvatarPath = sessionManager.userAvatarPath,
                userName = sessionManager.userName
            )
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showNewChatSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddComment,
                        contentDescription = stringResource(R.string.new_chat),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Cradle-shaped surface behind the tabs
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    shape = com.sha.orbis.ui.components.CradleBottomBarShape(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left tabs (0: Timeline, 1: Chats)
                        tabs.take(2).forEachIndexed { index, item ->
                            val isSelected = selectedTab == index
                            val tabBadgeCount = when (index) {
                                0 -> badgeSnapshot.unreadSocial
                                1 -> badgeSnapshot.unreadChats
                                else -> 0
                            }
                            OrbisBottomNavItem(
                                item = item,
                                isSelected = isSelected,
                                badgeCount = tabBadgeCount,
                                onClick = {
                                    selectedTab = index
                                    isSearchActive = false
                                    searchQuery = ""
                                    if (index == 1) {
                                        conversations = convRepository.loadConversations()
                                    }
                                    OrbisBadgeHub.refresh(context, currentAccountId)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Spacer for center FAB
                        Spacer(modifier = Modifier.weight(1f))

                        // Right tabs (2: Calls, 3: Contacts)
                        tabs.drop(2).forEachIndexed { relativeIndex, item ->
                            val absoluteIndex = relativeIndex + 2
                            val isSelected = selectedTab == absoluteIndex
                            val tabBadgeCount = when (absoluteIndex) {
                                2 -> badgeSnapshot.unreadMissedCalls
                                3 -> badgeSnapshot.pendingFriendRequests
                                else -> 0
                            }
                            OrbisBottomNavItem(
                                item = item,
                                isSelected = isSelected,
                                badgeCount = tabBadgeCount,
                                onClick = {
                                    selectedTab = absoluteIndex
                                    isSearchActive = false
                                    searchQuery = ""
                                    OrbisBadgeHub.refresh(context, currentAccountId)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Elevated Center FAB '+' Button (hovers in the cradle notch)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-18).dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            if (selectedTab != 0) {
                                selectedTab = 0
                            }
                            triggerCreatePostInFeed = true
                        },
                        containerColor = Color(0xFF4C4199),
                        contentColor = Color.White,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(6.dp),
                        modifier = Modifier.size(58.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.new_post),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        val navigateToConversation: (String, String) -> Unit = { phoneOrId, name ->
            val friendRepo = FriendRequestRepository(context, currentAccountId)
            val isPendingSent = friendRepo.getPendingSentForPhone(phoneOrId) != null && !friendRepo.isFriend(phoneOrId)
            if (isPendingSent) {
                Toast.makeText(
                    context,
                    context.getString(R.string.chat_locked_pending_desc, name.ifBlank { phoneOrId }),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val currentConvs = convRepository.loadConversations()
                val existingById = currentConvs.find { it.id == phoneOrId }
                if (existingById != null) {
                    conversations = currentConvs
                    activeConversation = existingById
                } else {
                    val cleanDigits = phoneOrId.filter { it.isDigit() }
                    val convId = if (cleanDigits.isNotBlank()) "conv_$cleanDigits" else phoneOrId
                    val existingByDigits = currentConvs.find { it.id == convId }
                    if (existingByDigits != null) {
                        conversations = currentConvs
                        activeConversation = existingByDigits
                    } else {
                        val newConv = Conversation(
                            id = convId,
                            title = name.ifBlank { phoneOrId },
                            participants = listOf("me", phoneOrId),
                            lastMessage = "",
                            updatedAt = System.currentTimeMillis()
                        )
                        val mutable = currentConvs.toMutableList()
                        mutable.add(0, newConv)
                        convRepository.saveConversations(mutable)
                        conversations = convRepository.loadConversations()
                        activeConversation = newConv
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            androidx.compose.runtime.key(currentAccountId) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab_switch"
                ) { tab ->
                    when (tab) {
                        0 -> TimelineScreen(
                            currentAccountId = currentAccountId,
                            targetPostId = targetSocialPostId,
                            targetActionType = targetSocialActionType,
                            onTargetPostHandled = {
                                targetSocialPostId = null
                                targetSocialActionType = null
                            },
                            onOpenWall = { phone, pseudo, avatar, role ->
                                activeUserWallState = com.sha.orbis.ui.components.FriendProfilePreviewDialogState(phone, pseudo, avatar, role)
                            },
                            onOpenChat = navigateToConversation,
                            triggerCreatePost = triggerCreatePostInFeed,
                            onCreatePostTriggerHandled = { triggerCreatePostInFeed = false }
                        )
                        1 -> WhatsAppConversationsList(
                            conversations = conversations,
                            searchQuery = searchQuery,
                            currentAccountId = currentAccountId,
                            isRefreshing = isRefreshingConversations,
                            onRefresh = {
                                if (!isRefreshingConversations) {
                                    isRefreshingConversations = true
                                    scope.launch {
                                        try {
                                            conversations = convRepository.syncConnectedFriendsToConversations(
                                                defaultMessage = context.getString(R.string.friends_connected_last_msg)
                                            )
                                            OrbisBadgeHub.refresh(context, currentAccountId)
                                            com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).reconnect(force = true)
                                            kotlinx.coroutines.delay(800)
                                            conversations = convRepository.loadConversations()
                                        } catch (_: Exception) {
                                        } finally {
                                            isRefreshingConversations = false
                                        }
                                    }
                                }
                            },
                            onOpenChat = { conv ->
                                val other = conv.participants.firstOrNull { it != "me" && it != sessionManager.userPhone } ?: ""
                                val friendRepo = FriendRequestRepository(context, currentAccountId)
                                if (!conv.isGroup && other.isNotBlank() && friendRepo.getPendingSentForPhone(other) != null && !friendRepo.isFriend(other)) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.chat_locked_pending_desc, conv.title),
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    activeConversation = conv
                                }
                            },
                            onDeleteConversation = { conv ->
                                convRepository.deleteConversation(conv.id)
                                conversations = convRepository.loadConversations()
                            },
                            onTogglePin = { conv ->
                                val nowPinned = convRepository.togglePinConversation(conv.id)
                                conversations = convRepository.loadConversations()
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (nowPinned) R.string.conversation_pinned_toast
                                        else R.string.conversation_unpinned_toast
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onStartNewChat = { showNewChatSheet = true },
                            stories = stories,
                            userAvatarPath = sessionManager.userAvatarPath,
                            userName = sessionManager.userName.ifBlank { "Moi" },
                            currentPhone = sessionManager.userPhone,
                            onAddStory = { content, mediaPath, mediaBase64, gradientIndex, mediaType, mediaUrl, targetCircleId, excludedCircleIds, excludedPhones ->
                                val story = SocialStory(
                                    id = "story_${UUID.randomUUID().toString().take(8)}",
                                    authorPhone = sessionManager.userPhone,
                                    authorName = sessionManager.userName.ifBlank { "Moi" },
                                    authorAvatarPath = sessionManager.userAvatarPath,
                                    content = content,
                                    mediaType = mediaType,
                                    mediaPath = mediaPath,
                                    mediaBase64 = mediaBase64,
                                    mediaUrl = mediaUrl,
                                    backgroundGradientIndex = gradientIndex,
                                    createdAt = System.currentTimeMillis(),
                                    expiresAt = System.currentTimeMillis() + 86_400_000L,
                                    targetCircleId = targetCircleId,
                                    excludedCircleIds = excludedCircleIds,
                                    excludedPhones = excludedPhones,
                                    authorPubkey = try { com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).identityManager.publicKeyHex } catch (_: Exception) { null }
                                )
                                socialRepo.addStory(story)
                                try {
                                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                    nostrSync.publishStory(story)
                                } catch (_: Exception) { }
                                refreshChatStories()
                                Toast.makeText(context, context.getString(R.string.social_story_created), Toast.LENGTH_SHORT).show()
                            },
                            onDeleteStory = { storyId ->
                                socialRepo.deleteStory(storyId)
                                try {
                                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                    nostrSync.publishDeleteStory(storyId)
                                } catch (_: Exception) { }
                                refreshChatStories()
                                Toast.makeText(context, context.getString(R.string.social_toast_story_deleted), Toast.LENGTH_SHORT).show()
                            },
                            onStorySeen = { story ->
                                socialRepo.markStorySeen(story.id, sessionManager.userPhone)
                                try {
                                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                    nostrSync.publishStoryView(story)
                                } catch (_: Exception) {}
                                refreshChatStories()
                            },
                            onStoryReact = { story, emoji ->
                                socialRepo.addStoryReaction(story.id, sessionManager.userPhone, emoji)
                                try {
                                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                    val authorTargetKey = story.authorPubkey ?: nostrSync.friendRequestRepo.resolveNostrPubkeyForPhone(story.authorPhone)
                                    nostrSync.publishStoryReaction(story.id, authorTargetKey, emoji)
                                } catch (_: Exception) {}
                                refreshChatStories()
                            }
                        )
                        2 -> com.sha.orbis.ui.calls.CallHistoryScreen(
                            currentAccountId = currentAccountId,
                            searchQuery = searchQuery,
                            onOpenChat = navigateToConversation
                        )
                        3 -> ContactsScreen(
                            currentAccountId = currentAccountId,
                            searchQuery = searchQuery,
                            onSearchClick = { isSearchActive = true },
                            onContactClick = { contact ->
                                navigateToConversation(contact.phone, contact.name)
                            },
                            onOpenWall = { phone, pseudo, avatar, role ->
                                activeUserWallState = com.sha.orbis.ui.components.FriendProfilePreviewDialogState(phone, pseudo, avatar, role)
                            },
                            onCreateGroupClick = { showCreateGroup = true },
                            onOpenFriendRequests = { showFriendRequestsModal = true }
                        )
                    }
                }
            }
        }
    }
}

private data class NavigationTabItem(
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WhatsAppConversationsList(
    conversations: List<Conversation>,
    searchQuery: String,
    currentAccountId: String? = null,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onOpenChat: (Conversation) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    onTogglePin: (Conversation) -> Unit,
    onStartNewChat: () -> Unit,
    stories: List<SocialStory> = emptyList(),
    userAvatarPath: String? = null,
    userName: String = "",
    currentPhone: String = "",
    onAddStory: (content: String, mediaPath: String?, mediaBase64: String?, gradientIndex: Int, mediaType: String?, mediaUrl: String?, targetCircleId: String?, excludedCircleIds: List<String>, excludedPhones: List<String>) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onDeleteStory: ((String) -> Unit)? = null,
    onStorySeen: ((SocialStory) -> Unit)? = null,
    onStoryReact: ((SocialStory, String) -> Unit)? = null
) {
    var conversationToDelete by remember { mutableStateOf<Conversation?>(null) }
    var conversationActionTarget by remember { mutableStateOf<Conversation?>(null) }
    var conversationToLock by remember { mutableStateOf<Conversation?>(null) }
    var privateRefreshKey by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val sessionManager = remember(context) { SessionManager(context) }
    val activeAccountId = currentAccountId ?: sessionManager.activeAccountId
    val blockedRepo = remember(context, activeAccountId) { com.sha.orbis.storage.BlockedContactsRepository(context, activeAccountId) }
    val friendRepo = remember(context, activeAccountId) { FriendRequestRepository(context, activeAccountId) }
    val privateRepo = remember(context, activeAccountId) { PrivateConversationRepository(context, activeAccountId) }

    val filtered = remember(conversations, searchQuery, privateRefreshKey) {
        val nonBlocked = conversations.filterNot { conv ->
            val other = conv.participants.firstOrNull { it != "me" } ?: ""
            privateRepo.isLocked(conv.id) ||
            conv.participants.any { it != "me" && blockedRepo.isBlocked(it) } ||
            blockedRepo.isBlocked(conv.id) ||
            blockedRepo.isBlocked(conv.title) ||
            (!conv.isGroup && other.isNotBlank() && friendRepo.getPendingSentForPhone(other) != null && !friendRepo.isFriend(other))
        }
        val result = if (searchQuery.isBlank()) nonBlocked
        else nonBlocked.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.lastMessage.contains(searchQuery, ignoreCase = true)
        }
        result.sortedWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.updatedAt })
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { onRefresh?.invoke() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header_stories_strip") {
                com.sha.orbis.ui.social.feed.FeedStoriesStrip {
                    com.sha.orbis.ui.social.feed.FeedSectionTitle(
                        title = stringResource(R.string.social_stories_title)
                    )
                    SocialStoriesBar(
                        stories = stories,
                        userAvatarPath = userAvatarPath,
                        userName = userName.ifBlank { "Moi" },
                        currentPhone = currentPhone,
                        onAddStory = onAddStory,
                        onDeleteStory = onDeleteStory,
                        onStorySeen = onStorySeen,
                        onStoryReact = onStoryReact
                    )
                    com.sha.orbis.ui.social.feed.FeedSectionDivider(
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            if (filtered.isEmpty()) {
                item(key = "empty_conversations_state") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.empty_conversations_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.empty_conversations_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onStartNewChat,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.empty_conversations_btn), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { conv ->
                    WhatsAppConversationRow(
                        conversation = conv,
                        stories = stories,
                        currentPhone = currentPhone,
                        onClick = { onOpenChat(conv) },
                        onLongClick = { conversationActionTarget = conv }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }

    if (conversationActionTarget != null) {
        val conv = conversationActionTarget!!
        AlertDialog(
            onDismissRequest = { conversationActionTarget = null },
            title = {
                Text(
                    text = conv.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Pin / Unpin
                    TextButton(
                        onClick = {
                            conversationActionTarget = null
                            onTogglePin(conv)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (conv.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(if (conv.isPinned) R.string.conversation_action_unpin else R.string.conversation_action_pin),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!conv.isGroup) {
                        TextButton(
                            onClick = {
                                conversationActionTarget = null
                                conversationToLock = conv
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.private_chat_menu_lock),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            conversationActionTarget = null
                            conversationToDelete = conv
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.chat_menu_delete_conversation),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { conversationActionTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (conversationToLock != null) {
        val conv = conversationToLock!!
        PrivateChatPasswordSetupDialog(
            conversationTitle = conv.title,
            isChange = false,
            onDismiss = { conversationToLock = null },
            onConfirm = { password, answers ->
                privateRepo.lockConversation(conv, password, answers)
                privateRefreshKey++
                conversationToLock = null
                Toast.makeText(
                    context,
                    context.getString(R.string.private_chat_toast_locked, conv.title),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // Delete Conversation Dialog
    if (conversationToDelete != null) {
        val conv = conversationToDelete!!
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = {
                Text(
                    text = stringResource(R.string.chat_menu_delete_conversation_confirm_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.chat_menu_delete_conversation_confirm_desc),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteConversation(conv)
                        conversationToDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.chat_menu_delete_conversation),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WhatsAppConversationRow(
    conversation: Conversation,
    stories: List<SocialStory> = emptyList(),
    currentPhone: String = "",
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val timeFormatted = remember(conversation.updatedAt) {
        val now = System.currentTimeMillis()
        val diff = now - conversation.updatedAt
        if (diff < 86400000L) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(conversation.updatedAt))
        } else {
            "Hier"
        }
    }

    val context = LocalContext.current
    val avatarPath = remember(conversation) {
        AvatarManager.resolveConversationAvatar(
            context = context,
            conversation = conversation
        )
    }

    val lastMsg = remember(conversation.id, conversation.updatedAt) {
        com.sha.orbis.storage.ConversationRepository(context).loadMessages(conversation.id).lastOrNull()
    }

    val otherParticipant = remember(conversation) {
        conversation.participants.firstOrNull { it != "me" }
    }
    val isPeerVerified = remember(otherParticipant) {
        otherParticipant != null && (com.sha.orbis.admin.AdminSecurityHelper.isAdmin(otherParticipant) ||
            com.sha.orbis.security.OrbisTrustVerificationEngine.isAutomatedVerified(context, otherParticipant))
    }
    val isPeerOnline = remember(otherParticipant, conversation.updatedAt) {
        if (otherParticipant != null) {
            com.sha.orbis.social.PresenceHelper.isContactOnline(
                peerPhone = otherParticipant,
                peerPubkey = otherParticipant,
                context = context
            )
        } else false
    }

    val friendRepo = remember { com.sha.orbis.storage.FriendRequestRepository(context) }
    val peerStories = remember(stories, otherParticipant, currentPhone) {
        if (otherParticipant.isNullOrBlank() || conversation.isGroup) emptyList()
        else {
            val isFriend = friendRepo.isFriend(otherParticipant)
            if (!isFriend) emptyList()
            else stories.filter {
                !it.isExpired &&
                com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it.authorPhone, otherParticipant) &&
                com.sha.orbis.social.StoryAudiencePolicy.isVisibleToCurrentUser(context, it, currentPhone)
            }
        }
    }
    val hasPeerStory = peerStories.isNotEmpty()
    val hasUnseenPeerStory = remember(peerStories, currentPhone) {
        if (currentPhone.isBlank()) true
        else peerStories.any { s -> !s.seenBy.any { com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it, currentPhone) } }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (hasPeerStory) {
            com.sha.orbis.ui.social.StoryFireAvatar(
                avatarPath = avatarPath,
                name = conversation.title,
                size = 50.dp,
                hasStory = true,
                hasUnseenStory = hasUnseenPeerStory,
                storyCount = peerStories.size,
                showFlameBadge = true
            )
        } else {
            // Universal WhatsApp-style Avatar with photo support and presence indicator
            OrbisAvatar(
                avatarPath = avatarPath,
                name = conversation.title,
                size = 50.dp,
                isOnline = isPeerOnline
            )
        }

        // Conversation Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isPeerVerified) {
                        com.sha.orbis.ui.social.BlueVerifiedBadge(size = 14.dp)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (conversation.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.conversation_action_pin),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (conversation.isPinned) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isMineLast = lastMsg != null && lastMsg.senderId == "me"
                    if (isMineLast && conversation.unreadCount == 0) {
                        val (checkIcon, checkTint) = when (lastMsg.status) {
                            MessageDeliveryStatus.READ -> Icons.Default.DoneAll to androidx.compose.ui.graphics.Color(0xFF38BDF8)
                            MessageDeliveryStatus.DELIVERED -> Icons.Default.DoneAll to MaterialTheme.colorScheme.onSurfaceVariant
                            MessageDeliveryStatus.SENT -> Icons.Default.Check to MaterialTheme.colorScheme.onSurfaceVariant
                            MessageDeliveryStatus.SENDING -> Icons.Default.Schedule to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            MessageDeliveryStatus.FAILED -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
                        }
                        Icon(
                            imageVector = checkIcon,
                            contentDescription = null,
                            tint = checkTint,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    val previewText = lastMsg?.text ?: conversation.lastMessage
                    Text(

                        text = when {
                            previewText.startsWith("[IMAGE:") || MediaAttachmentHelper.isAlbumPayload(previewText) -> stringResource(R.string.chat_last_message_image)
                            previewText.startsWith("[DOC:") -> stringResource(R.string.chat_last_message_doc)
                            previewText.startsWith("[AUDIO:") -> stringResource(R.string.chat_last_message_audio)
                            VideoMediaHelper.isVideoPayload(previewText) -> stringResource(R.string.chat_last_message_video)
                            previewText.startsWith("[GPS:") -> stringResource(R.string.media_gps_label)
                            previewText.startsWith("[QUOTE:") -> {
                                val endIdx = previewText.indexOf("]\n")
                                if (endIdx != -1) previewText.substring(endIdx + 2)
                                else previewText
                            }
                            else -> previewText
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp
                    )
                }

                if (conversation.unreadCount > 0) {
                    OrbisPremiumBadge(
                        count = conversation.unreadCount,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OrbisBottomNavItem(
    item: NavigationTabItem,
    isSelected: Boolean,
    badgeCount: Int,
    isSecondaryBlock: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = activeColor,
                    modifier = Modifier.size(24.dp)
                )
                if (badgeCount > 0) {
                    OrbisPremiumBadge(
                        count = badgeCount,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-4).dp)
                    )
                }
            }
        }
    }
}
