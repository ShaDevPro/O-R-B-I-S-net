package com.sha.orbis.ui.conversation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.sha.orbis.ai.reply.OrbisReplyEngine
import com.sha.orbis.ai.ui.OrbisSmartReplyRow
import com.sha.orbis.nostr.service.NostrSyncManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Visibility
import com.sha.orbis.media.MediaDownloadManager
import com.sha.orbis.media.LinkPreviewHelper
import com.sha.orbis.ui.components.LinkifiedText
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.collectAsState
import com.sha.orbis.call.OrbisCallManager
import com.sha.orbis.ui.call.OrbisCallRequirementDialog
import com.sha.orbis.ui.call.OrbisPreCallDialog
import com.sha.orbis.ui.call.OrbisVoiceCallScreen
import com.sha.orbis.storage.FriendRequestRepository
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.data.ContactsPickerHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.media.AudioVoiceHelper
import com.sha.orbis.media.LocationGpsHelper
import com.sha.orbis.media.MediaAttachmentHelper
import com.sha.orbis.media.VideoMediaHelper
import com.sha.orbis.ui.components.FullScreenImageViewerDialog
import com.sha.orbis.ui.components.FullScreenVideoDialog
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import com.sha.orbis.model.Contact
import com.sha.orbis.model.CountryCode
import com.sha.orbis.model.Message
import com.sha.orbis.security.EphemeralMessageManager
import com.sha.orbis.notification.OrbisEventBus
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.theme.OrbisColorPalette
import androidx.compose.foundation.layout.heightIn
import com.sha.orbis.model.MessageDeliveryStatus
import com.sha.orbis.social.PresenceHelper
import com.sha.orbis.social.SharedPostPayload
import com.sha.orbis.social.UserSocialRole
import androidx.compose.ui.text.style.TextOverflow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    conversationTitle: String = "Discussion",
    conversationId: String = "conv_demo",
    recipientPhone: String = "",
    onBack: (() -> Unit)? = null,
    onOpenWall: ((phone: String, pseudo: String, avatar: String?, role: UserSocialRole) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }
    val repository = remember(context) { ConversationRepository(context) }
    val listState = rememberLazyListState()

    val targetPhone = remember(conversationId, recipientPhone, conversationTitle) {
        val dialCode = CountryCode.defaultCountry(context).dialCode

        // 1. Direct from parameter if valid
        if (recipientPhone.filter { it.isDigit() }.length >= 8) {
            return@remember ContactsPickerHelper.normalizePhoneNumber(recipientPhone, dialCode)
        }

        // 2. From saved Orbis Contacts repository
        val savedContacts = repository.loadContacts()
        val repoContact = savedContacts.find {
            (it.phone.filter { ch -> ch.isDigit() }.length >= 8 && "conv_${it.phone.filter { ch -> ch.isDigit() }}" == conversationId) ||
            (it.name.equals(conversationTitle, ignoreCase = true) && it.phone.filter { ch -> ch.isDigit() }.length >= 8)
        }
        if (repoContact != null && repoContact.phone.filter { it.isDigit() }.length >= 8) {
            return@remember ContactsPickerHelper.normalizePhoneNumber(repoContact.phone, dialCode)
        }

        // 3. Search directly in device contacts (ContactsContract) by matching contact name
        val deviceContact = ContactsPickerHelper.fetchDeviceContacts(context).find {
            it.name.equals(conversationTitle, ignoreCase = true) ||
            conversationTitle.contains(it.name, ignoreCase = true) ||
            it.name.contains(conversationTitle, ignoreCase = true)
        }
        if (deviceContact != null && deviceContact.phoneNumber.filter { it.isDigit() }.length >= 8) {
            val resolved = ContactsPickerHelper.normalizePhoneNumber(deviceContact.phoneNumber, dialCode)
            // Persist the resolved number into Orbis repository so it is never invalid
            val mutableContacts = savedContacts.toMutableList()
            val idx = mutableContacts.indexOfFirst { it.name.equals(conversationTitle, ignoreCase = true) }
            if (idx >= 0) {
                mutableContacts[idx] = mutableContacts[idx].copy(phone = resolved)
            } else {
                mutableContacts.add(0, Contact(
                    id = "c_${resolved.filter { it.isDigit() }}",
                    name = conversationTitle,
                    phone = resolved,
                    publicKey = "PUB_KEY_PENDING",
                    status = "Actif"
                ))
            }
            repository.saveContacts(mutableContacts)
            return@remember resolved
        }

        // 4. Fallback from conversation ID
        val extracted = conversationId.removePrefix("conv_")
        if (extracted.filter { it.isDigit() }.length >= 8) {
            ContactsPickerHelper.normalizePhoneNumber(extracted, dialCode)
        } else {
            extracted
        }
    }

    var messages by remember {
        mutableStateOf(
            EphemeralMessageManager.purgeExpiredMessages(
                context,
                conversationId,
                repository.loadMessages(conversationId)
            )
        )
    }
    var isRefreshingChat by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(conversationId, targetPhone) {
        com.sha.orbis.data.OrbisBadgeHub.markConversationRead(context, conversationId)
        if (targetPhone.isNotBlank()) {
            com.sha.orbis.call.OrbisMissedCallManager.cancelMissedCallNotification(context, targetPhone)
            com.sha.orbis.sync.scheduler.SovereignSyncScheduler.onConversationOpened(context, targetPhone)
        }
    }
    DisposableEffect(conversationId, targetPhone) {
        com.sha.orbis.notification.ActiveConversationTracker.setActiveConversation(conversationId, targetPhone)
        onDispose {
            com.sha.orbis.notification.ActiveConversationTracker.clearActiveConversation(conversationId)
        }
    }
    var selectedMessageForMenu by remember { mutableStateOf<Message?>(null) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var messageToShareFriends by remember { mutableStateOf<Message?>(null) }
    var messageToShareWall by remember { mutableStateOf<Message?>(null) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showDeleteChatDialog by remember { mutableStateOf(false) }
    var showBlockContactDialog by remember { mutableStateOf(false) }
    var showEphemeralMenu by remember { mutableStateOf(false) }
    var showAttachmentsMenu by remember { mutableStateOf(false) }
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var fullScreenImageSource by remember { mutableStateOf<String?>(null) }
    var fullScreenImageCaption by remember { mutableStateOf<String?>(null) }
    var fullScreenVideoSource by remember { mutableStateOf<String?>(null) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingAttachment by remember { mutableStateOf<PendingAttachment?>(null) }
    var isProcessingVideo by remember { mutableStateOf(false) }
    var videoProcessProgress by remember { mutableIntStateOf(0) }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var photoProcessProgress by remember { mutableIntStateOf(0) }

    var showOptionsDropdown by remember { mutableStateOf(false) }
    var showNotFriendDialog by remember { mutableStateOf(false) }
    var showInviteInstallSheet by remember { mutableStateOf(false) }
    var showCallRequirementDialog by remember { mutableStateOf(false) }
    var showPreCallDialog by remember { mutableStateOf(false) }
    var showE2eeInfoDialog by remember { mutableStateOf(false) }
    val currentCallSession by OrbisCallManager.callState.collectAsState()
    var ephemeralTimerMs by remember { mutableLongStateOf(EphemeralMessageManager.getTimerForConversation(context, conversationId)) }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var currentContactLang by remember(targetPhone) {
        mutableStateOf(com.sha.orbis.ai.affinity.OrbisPeerLanguageEngine.getPreferredLanguage(context, targetPhone))
    }

    LaunchedEffect(targetPhone, messages.size) {
        currentContactLang = com.sha.orbis.ai.affinity.OrbisPeerLanguageEngine.getPreferredLanguage(context, targetPhone)
    }

    val currentConversation = remember(conversationId) {
        repository.loadConversations().find { it.id == conversationId }
    }
    val isGroup = currentConversation?.isGroup == true
    var groupParticipants by remember(conversationId, currentConversation) {
        val initialParticipants = currentConversation?.participants ?: emptyList()
        if (conversationId == "group_family_circle") {
            val familyCircleRepo = com.sha.orbis.storage.FriendCircleRepository(context)
            val famMembers = familyCircleRepo.getFamilyMembers()
            mutableStateOf((listOf(sessionManager.userPhone) + famMembers).distinct())
        } else {
            mutableStateOf(initialParticipants)
        }
    }
    var showGroupMembersDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    val friendRequestRepo = remember { FriendRequestRepository(context) }
    val cleanTargetDigits = remember(targetPhone) { targetPhone.filter { it.isDigit() } }

    var pendingReceivedRequest by remember(targetPhone, conversationId) {
        mutableStateOf(friendRequestRepo.getPendingReceivedForPhone(targetPhone))
    }

    var pendingSentRequest by remember(targetPhone, conversationId) {
        mutableStateOf(friendRequestRepo.getPendingSentForPhone(targetPhone))
    }

    var isFriend by remember(targetPhone, conversationTitle) {
        val c = repository.loadContacts().find {
            FriendRequestRepository.isSamePhone(it.phone, targetPhone) || it.name.equals(conversationTitle, ignoreCase = true)
        }
        mutableStateOf(
            (c != null && c.publicKey.isNotBlank() && c.publicKey.length > 30 && c.status.contains("Connecté")) ||
            friendRequestRepo.isFriend(targetPhone)
        )
    }

    val isChatLockedForSender = !isGroup && !isFriend && pendingSentRequest != null

    var isRecordingAudio by remember { mutableStateOf(false) }
    var playingAudioId by remember { mutableStateOf<String?>(null) }
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }

    val audioPermissionLauncher = com.sha.orbis.permissions.rememberOrbisPermissionLauncher(
        permission = com.sha.orbis.permissions.OrbisPermission.RECORD_AUDIO,
        onGranted = {
            val file = AudioVoiceHelper.startRecording(context)
            if (file != null) {
                isRecordingAudio = true
            } else {
                Toast.makeText(context, "Impossible de démarrer l'enregistrement", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // Auto-scroll on new message and clear unread badge
    LaunchedEffect(conversationId) {
        val convs = repository.loadConversations().toMutableList()
        val idx = convs.indexOfFirst { it.id == conversationId }
        if (idx >= 0 && convs[idx].unreadCount > 0) {
            convs[idx] = convs[idx].copy(unreadCount = 0)
            repository.saveConversations(convs)
            com.sha.orbis.sms.LauncherBadgeManager.updateBadge(context)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // ORBIS Reply-LLM Smart Reply State
    val latestIncomingMessage = remember(messages) {
        messages.lastOrNull { it.senderId != "me" && it.text.isNotBlank() && !it.text.startsWith("[VOICE:") && !it.text.startsWith("[GPS:") }
    }

    val smartReplies = remember(latestIncomingMessage?.text) {
        val text = latestIncomingMessage?.text
        if (!text.isNullOrBlank()) {
            OrbisReplyEngine.generateReplies(context, text).suggestions
        } else emptyList()
    }

    fun resolveRecipientNostrKey(): String? {
        val effectivePhone = if (targetPhone.filter { it.isDigit() }.length >= 6 || targetPhone.startsWith("npub1")) {
            targetPhone
        } else {
            val contact = repository.loadContacts().find { it.name.equals(conversationTitle, ignoreCase = true) }
            contact?.phone ?: targetPhone
        }

        if (FriendRequestRepository.isValidNostrKey(effectivePhone)) {
            return effectivePhone
        }

        val c = repository.loadContacts().find { FriendRequestRepository.isSamePhone(it.phone, effectivePhone) || it.name.equals(conversationTitle, ignoreCase = true) }
        val r = friendRequestRepo.loadRequests().find { FriendRequestRepository.isSamePhone(it.senderPhone, effectivePhone) || it.senderName.equals(conversationTitle, ignoreCase = true) }
        val fromContact = c?.publicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
        val fromRequest = r?.senderPublicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
        val fromParticipants = groupParticipants.firstOrNull { FriendRequestRepository.isValidNostrKey(it) }
        val fromMessages = messages.lastOrNull { it.senderId != "me" && FriendRequestRepository.isValidNostrKey(it.senderId) }?.senderId

        return fromContact ?: fromRequest ?: fromParticipants ?: fromMessages
    }

    fun sendMessage(plainText: String) {
        if (plainText.isBlank()) return

        // Auto-train ORBIS Reply-LLM with user response
        val incoming = latestIncomingMessage?.text ?: ""
        if (incoming.isNotBlank()) {
            OrbisReplyEngine.learnFromUserSentMessage(context, incoming, plainText)
        }

        // Télémétrie autonome messagerie (texte, note audio, photo, doc, vidéo)
        com.sha.orbis.telemetry.MessagingTelemetryTracker.trackMessagePayload(context, plainText)

        val identity = sessionManager.getOrCreateIdentity()
        val defaultKey = friendRequestRepo.getGroupKeyForPhone(targetPhone)
            ?: com.sha.orbis.security.AesCipher.generateKeyBase64()
        val expiresAt = if (ephemeralTimerMs > 0L) System.currentTimeMillis() + ephemeralTimerMs else null
        val msgId = "msg_${System.currentTimeMillis()}"

        val newMessage = Message(
            id = msgId,
            conversationId = conversationId,
            senderId = "me",
            text = plainText,
            timestamp = System.currentTimeMillis(),
            encrypted = true,
            status = MessageDeliveryStatus.SENDING,
            expiresAt = expiresAt
        )
        messages = messages + newMessage
        repository.addMessage(conversationId, newMessage)
        draft = ""

        if (isGroup) {
            // Group Chat broadcast via Nostr E2EE fan-out and GSM SMS fallback
            val recipients = (groupParticipants - sessionManager.userPhone).filter { it.isNotBlank() }
            if (recipients.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.group_no_recipients), Toast.LENGTH_SHORT).show()
                return
            }
            val contacts = repository.loadContacts()
            val nostrSync = try { com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context) } catch (_: Exception) { null }

            recipients.forEach { memberPhone ->
                val memberTarget = memberPhone.trim()
                val isDirectNostr = FriendRequestRepository.isValidNostrKey(memberTarget)
                val resolvedNostrKey = if (isDirectNostr) {
                    memberTarget
                } else {
                    val c = contacts.find { FriendRequestRepository.isSamePhone(it.phone, memberTarget) || it.name.equals(memberTarget, ignoreCase = true) }
                    val r = friendRequestRepo.loadRequests().find { FriendRequestRepository.isSamePhone(it.senderPhone, memberTarget) }
                    c?.publicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
                        ?: r?.senderPublicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
                }

                if (nostrSync != null && resolvedNostrKey != null) {
                    try {
                        val myAvatarThumb = com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)
                        nostrSync.sendDirectMessage(
                            recipientNpubOrHex = resolvedNostrKey,
                            conversationId = conversationId,
                            text = plainText,
                            messageId = msgId,
                            ephemeralTimerMs = ephemeralTimerMs,
                            senderAvatarBase64 = myAvatarThumb,
                            senderName = sessionManager.userName
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("GroupChat", "Erreur transmission Nostr à $memberTarget: ${e.message}")
                    }
                }
            }
            repository.updateMessageStatus(conversationId, msgId, MessageDeliveryStatus.SENT)
        } else {
            // 1-to-1 Chat: 100% Sovereign Internet Delivery via Nostr (Zero GSM SMS)
            val resolvedNostrKey = resolveRecipientNostrKey()

            if (resolvedNostrKey != null) {
                try {
                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                    val myAvatarThumb = com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)
                    val event = nostrSync.sendDirectMessage(
                        recipientNpubOrHex = resolvedNostrKey,
                        conversationId = conversationId,
                        text = plainText,
                        messageId = msgId,
                        ephemeralTimerMs = ephemeralTimerMs,
                        senderAvatarBase64 = myAvatarThumb,
                        senderName = sessionManager.userName
                    )
                    repository.updateMessageNetworkEventId(conversationId, msgId, event.id)
                    repository.updateMessageStatus(conversationId, msgId, MessageDeliveryStatus.SENT)
                    messages = repository.loadMessages(conversationId)
                    return
                } catch (e: Exception) {
                    android.util.Log.w("ConversationScreen", "Erreur transmission Nostr: ${e.message}")

                    repository.updateMessageStatus(conversationId, msgId, MessageDeliveryStatus.FAILED)
                }
            } else {
                // Key not yet available: Inform user cleanly via Toast in their language (ZERO SMS fallback)
                repository.updateMessageStatus(conversationId, msgId, MessageDeliveryStatus.SENDING)
                Toast.makeText(context, context.getString(R.string.chat_nostr_waiting_friend_key), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun performGpsShare() {
        if (!LocationGpsHelper.isLocationEnabled(context)) {
            Toast.makeText(context, context.getString(R.string.media_gps_enable_settings_toast), Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
            return
        }

        Toast.makeText(context, context.getString(R.string.media_gps_searching_signal), Toast.LENGTH_SHORT).show()
        LocationGpsHelper.getOfflineLocation(
            context = context,
            onLocationResult = { lat, lon, _ ->
                val gpsPayload = LocationGpsHelper.formatGpsPayload(lat, lon)
                sendMessage(gpsPayload)
            },
            onError = { err ->
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            }
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val isGranted = permissionsMap.values.any { it }
        if (isGranted) {
            performGpsShare()
        } else {
            Toast.makeText(context, context.getString(R.string.media_gps_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    fun requestAndShareGpsLocation() {
        if (LocationGpsHelper.hasLocationPermission(context)) {
            performGpsShare()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isProcessingPhoto = true
                photoProcessProgress = 0
                val totalCount = uris.size
                if (totalCount == 1) {
                    val imgId = "img_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"
                    val (destFile, base64) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        MediaAttachmentHelper.processImageUri(
                            context = context,
                            uri = uris[0],
                            customId = imgId,
                            targetMaxBytes = MediaAttachmentHelper.TARGET_SINGLE_IMAGE_BYTES,
                            maxDimension = MediaAttachmentHelper.MAX_SINGLE_IMAGE_DIM
                        )
                    }
                    photoProcessProgress = 100
                    isProcessingPhoto = false
                    if (base64 != null) {
                        pendingAttachment = PendingAttachment.Image(
                            base64 = base64,
                            bitmap = com.sha.orbis.cache.MediaMemoryCache.get(base64),
                            file = destFile
                        )
                    } else {
                        Toast.makeText(context, context.getString(R.string.chat_photo_too_large_nostr), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val targetPerImage = (MediaAttachmentHelper.TARGET_ALBUM_TOTAL_BYTES / totalCount).coerceAtLeast(6 * 1024L)
                    val processedImages = ArrayList<PendingAttachment.PendingImageItem>()
                    val albumBatchId = "album_${System.currentTimeMillis()}"
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        uris.forEachIndexed { index, u ->
                            val subId = "${albumBatchId}_$index"
                            val (destFile, base64) = MediaAttachmentHelper.processImageUri(
                                context = context,
                                uri = u,
                                customId = subId,
                                targetMaxBytes = targetPerImage,
                                maxDimension = MediaAttachmentHelper.MAX_ALBUM_IMAGE_DIM
                            )
                            if (base64 != null) {
                                processedImages.add(
                                    PendingAttachment.PendingImageItem(
                                        base64 = base64,
                                        file = destFile,
                                        bitmap = com.sha.orbis.cache.MediaMemoryCache.get(base64)
                                    )
                                )
                            }
                            photoProcessProgress = ((index + 1) * 100 / totalCount)
                        }
                    }
                    isProcessingPhoto = false
                    if (processedImages.isNotEmpty()) {
                        pendingAttachment = PendingAttachment.MultiImage(
                            images = processedImages
                        )
                    } else {
                        Toast.makeText(context, context.getString(R.string.chat_photo_too_large_nostr), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isProcessingVideo = true
                videoProcessProgress = 0
                val result = VideoMediaHelper.processVideoUri(context, uri)
                if (result != null) {
                    // 1. Compression matérielle automatique (0% -> 50%)
                    val compressedFile = VideoMediaHelper.compressVideo(context, result.localFile) { pct ->
                        videoProcessProgress = (pct * 0.5f).toInt()
                    }
                    if (compressedFile.length() > VideoMediaHelper.MAX_VIDEO_SIZE_BYTES) {
                        isProcessingVideo = false
                        Toast.makeText(context, context.getString(R.string.video_size_too_large), Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    // 2. Envoi direct vers serveur média Blossom décentralisé (BUD-01/02/11) : 50% -> 100%
                    val uploadResult = com.sha.orbis.nostr.media.BlossomMediaManager.uploadVideo(context, compressedFile) { pct ->
                        videoProcessProgress = 50 + (pct * 0.5f).toInt()
                    }
                    val thumbBmp = VideoMediaHelper.loadThumbnailBitmap(context, result.id)
                    isProcessingVideo = false
                    if (uploadResult != null) {
                        pendingAttachment = PendingAttachment.Video(
                            videoId = result.id,
                            localFile = compressedFile,
                            thumbnailBitmap = thumbBmp,
                            durationMs = result.durationMs,
                            width = result.width,
                            height = result.height,
                            base64 = null,
                            url = uploadResult.url
                        )
                    } else {
                        Toast.makeText(context, "Échec de l'envoi de la vidéo vers le serveur média", Toast.LENGTH_LONG).show()
                    }
                } else {
                    isProcessingVideo = false
                    Toast.makeText(context, context.getString(R.string.video_process_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists()) {
            val uri = Uri.fromFile(tempCameraFile)
            val (destFile, base64) = MediaAttachmentHelper.processImageUri(context, uri)
            if (base64 != null) {
                pendingAttachment = PendingAttachment.Image(
                    base64 = base64,
                    file = destFile ?: tempCameraFile
                )
            }
        }
    }

    val cameraPermissionLauncher = com.sha.orbis.permissions.rememberOrbisPermissionLauncher(
        permission = com.sha.orbis.permissions.OrbisPermission.CAMERA,
        onGranted = {
            val cacheImagesDir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
            val file = File(cacheImagesDir, "capture_${System.currentTimeMillis()}.jpg")
            tempCameraFile = file
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            cameraLauncher.launch(uri)
        }
    )

    val callAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Audio permission result handled */ }

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Call permissions result handled */ }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = MediaAttachmentHelper.processDocumentUri(context, uri)
            if (result != null && result.first != null) {
                val (file, displayName, fileSize) = result
                val base64 = MediaAttachmentHelper.encodeDocToBase64(file!!)
                if (base64.isNotEmpty()) {
                    val sizeFormatted = MediaAttachmentHelper.formatFileSize(fileSize)
                    pendingAttachment = PendingAttachment.Document(
                        file = file,
                        displayName = displayName,
                        fileSizeFormatted = sizeFormatted,
                        base64 = base64
                    )
                } else {
                    Toast.makeText(context, context.getString(R.string.chat_error_file_too_large), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, context.getString(R.string.chat_error_file_too_large), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Listen for incoming SMS in real time
    DisposableEffect(conversationId) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val updatedConvId = intent?.getStringExtra(OrbisEventBus.EXTRA_CONV_ID)
                if (updatedConvId == null || updatedConvId == conversationId || updatedConvId == "all_convs" || (cleanTargetDigits.length >= 8 && updatedConvId.contains(cleanTargetDigits.takeLast(8)))) {
                    messages = EphemeralMessageManager.purgeExpiredMessages(
                        context,
                        conversationId,
                        repository.loadMessages(conversationId)
                    )
                    pendingReceivedRequest = friendRequestRepo.getPendingReceivedForPhone(targetPhone)
                    pendingSentRequest = friendRequestRepo.getPendingSentForPhone(targetPhone)
                    val c = repository.loadContacts().find {
                        FriendRequestRepository.isSamePhone(it.phone, targetPhone) || it.name.equals(conversationTitle, ignoreCase = true)
                    }
                    isFriend = (c != null && c.publicKey.isNotBlank() && c.publicKey.length > 30 && c.status.contains("Connecté")) || friendRequestRepo.isFriend(targetPhone)
                }
            }
        }

        val filter = IntentFilter(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
            AudioVoiceHelper.stopPlaying()
        }
    }

    fun sendReaction(msg: Message, emoji: String) {
        repository.addReactionToMessage(conversationId, msg.id, sessionManager.userPhone, emoji)
        messages = EphemeralMessageManager.purgeExpiredMessages(
            context,
            conversationId,
            repository.loadMessages(conversationId)
        )

        val effectivePhone = if (targetPhone.filter { it.isDigit() }.length >= 6 || targetPhone.startsWith("npub1")) {
            targetPhone
        } else {
            val contact = repository.loadContacts().find { it.name.equals(conversationTitle, ignoreCase = true) }
            contact?.phone ?: targetPhone
        }

        val c = repository.loadContacts().find { FriendRequestRepository.isSamePhone(it.phone, effectivePhone) }
        val r = friendRequestRepo.loadRequests().find { FriendRequestRepository.isSamePhone(it.senderPhone, effectivePhone) }
        val resolvedKey = c?.publicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
            ?: r?.senderPublicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }

        if (resolvedKey != null) {
            try {
                val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                nostrSync.sendDirectMessage(
                    recipientNpubOrHex = resolvedKey,
                    conversationId = conversationId,
                    text = "[REACTION:${msg.id}:$emoji]"
                )
            } catch (_: Exception) {}
        }
    }

    if (currentCallSession != null) {
        val session = currentCallSession!!
        if (session.isVideoCall) {
            com.sha.orbis.ui.call.OrbisVideoCallScreen(
                session = session,
                onAcceptVideoCall = {
                    val needed = mutableListOf<String>()
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        needed.add(android.Manifest.permission.RECORD_AUDIO)
                    }
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        needed.add(android.Manifest.permission.CAMERA)
                    }
                    if (needed.isNotEmpty()) {
                        callPermissionsLauncher.launch(needed.toTypedArray())
                    }
                    OrbisCallManager.acceptCall(withVideo = true)
                },
                onAcceptVoiceOnly = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        callAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                    OrbisCallManager.acceptCall(withVideo = false)
                },
                onDeclineCall = { OrbisCallManager.rejectCall() },
                onToggleCamera = { OrbisCallManager.toggleCamera() },
                onSwitchCamera = { OrbisCallManager.switchCamera() },
                onToggleMute = { OrbisCallManager.toggleMute() },
                onToggleSpeaker = { OrbisCallManager.toggleSpeaker() },
                onEndCall = { OrbisCallManager.endCall() }
            )
        } else {
            OrbisVoiceCallScreen(
                session = session,
                onAcceptCall = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        callAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                    OrbisCallManager.acceptCall(withVideo = false)
                },
                onDeclineCall = { OrbisCallManager.rejectCall() },
                onToggleMute = { OrbisCallManager.toggleMute() },
                onToggleSpeaker = { OrbisCallManager.toggleSpeaker() },
                onEndCall = { OrbisCallManager.endCall() }
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // WhatsApp Style Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_to_settings),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (isGroup) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    val contact = remember(conversationTitle, targetPhone) {
                        repository.loadContacts().find {
                            com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it.phone, targetPhone) || it.name.equals(conversationTitle, ignoreCase = true)
                        }
                    }
                    val convAvatar = remember(conversationId, conversationTitle, targetPhone, contact?.avatarPath) {
                        contact?.avatarPath ?: com.sha.orbis.ui.components.AvatarManager.resolveConversationAvatar(
                            context = context,
                            conversation = com.sha.orbis.model.Conversation(
                                id = conversationId,
                                title = conversationTitle,
                                participants = listOf("me", targetPhone)
                            )
                        )
                    }
                    OrbisAvatar(
                        avatarPath = convAvatar,
                        name = conversationTitle,
                        size = 40.dp
                    )
                }

                val contact = remember(conversationTitle, targetPhone) {
                    repository.loadContacts().find {
                        com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it.phone, targetPhone) || it.name.equals(conversationTitle, ignoreCase = true)
                    }
                }

                val isOnline = remember(contact, messages.size, sessionManager.isPresenceHidden) {
                    val lastIncomingMsg = messages.lastOrNull {
                        it.senderId != "me" && it.senderId != sessionManager.userPhone
                    }?.timestamp
                    PresenceHelper.isContactOnline(
                        peerPhone = targetPhone,
                        peerPubkey = contact?.publicKey,
                        lastIncomingMessageTimestamp = lastIncomingMsg,
                        context = context
                    )
                }

                LaunchedEffect(conversationId, sessionManager.isPresenceHidden) {
                    if (!sessionManager.isPresenceHidden && !isGroup) {
                        val peerKey = contact?.publicKey?.takeIf { com.sha.orbis.storage.FriendRequestRepository.isValidNostrKey(it) }
                            ?: resolveRecipientNostrKey()
                        if (!peerKey.isNullOrBlank()) {
                            try {
                                com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).sendDeliveryReceipt(
                                    recipientPubKeyHex = peerKey,
                                    conversationId = conversationId,
                                    messageId = "presence_${System.currentTimeMillis()}",
                                    status = "PRESENCE"
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }

                // Accusé de lecture automatique (Accusé lu : passage de DELIVERED ✓✓ gris à READ ✓✓ bleu)
                LaunchedEffect(messages.size, conversationId, sessionManager.isPresenceHidden) {
                    val myDigits = sessionManager.userPhone.filter { it.isDigit() }
                    val unreadPeerMessages = messages.filter { msg ->
                        val isSenderMe = msg.senderId == "me" ||
                                         msg.senderId.isBlank() ||
                                         (myDigits.length >= 8 && FriendRequestRepository.isSamePhone(msg.senderId, sessionManager.userPhone))
                        !isSenderMe && msg.status != MessageDeliveryStatus.READ
                    }
                    if (unreadPeerMessages.isNotEmpty()) {
                        val peerKey = contact?.publicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
                            ?: resolveRecipientNostrKey()
                        val nostrSync = try { NostrSyncManager.getInstance(context) } catch (_: Exception) { null }

                        unreadPeerMessages.forEach { msg ->
                            repository.updateMessageStatus(conversationId, msg.id, MessageDeliveryStatus.READ)
                            if (!peerKey.isNullOrBlank() && nostrSync != null && !sessionManager.isPresenceHidden && !isGroup) {
                                try {
                                    nostrSync.sendDeliveryReceipt(
                                        recipientPubKeyHex = peerKey,
                                        conversationId = conversationId,
                                        messageId = msg.id,
                                        status = "READ"
                                    )
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }


                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.clickable {
                        if (isGroup) showGroupMembersDialog = true
                    }
                ) {
                    val isVerifiedConversation = remember(targetPhone) {
                        !isGroup && (com.sha.orbis.admin.AdminSecurityHelper.isAdmin(targetPhone) ||
                            com.sha.orbis.security.OrbisTrustVerificationEngine.isAutomatedVerified(context, targetPhone))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = conversationTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (isVerifiedConversation) {
                            com.sha.orbis.ui.social.BlueVerifiedBadge(size = 15.dp)
                        }
                    }
                    if (isGroup) {
                        Text(
                            text = stringResource(R.string.group_header_subtitle, groupParticipants.size),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline) OrbisColorPalette.StatusActive else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                )
                                Text(
                                    text = if (isOnline) stringResource(R.string.presence_online) else stringResource(R.string.presence_offline),
                                    fontSize = 11.sp,
                                    color = if (isOnline) OrbisColorPalette.StatusActive else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Pastille Langue Interlocuteur (Affinité & Forçage Manuel)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showLanguageDialog = true }
                            ) {
                                Text(
                                    text = com.sha.orbis.ai.affinity.OrbisPeerLanguageEngine.getLanguageBadgeText(currentContactLang),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // E2EE Voice Call Handset Button
                if (!isGroup && !isChatLockedForSender) {
                    IconButton(
                        onClick = {
                            if (isFriend) {
                                showPreCallDialog = true
                            } else {
                                showCallRequirementDialog = true
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = stringResource(R.string.chat_call_btn_desc),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Ephemeral Timer Pill
                Box {
                    IconButton(
                        onClick = { showEphemeralMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = stringResource(R.string.ephemeral_title),
                            tint = if (ephemeralTimerMs > 0L) OrbisColorPalette.StatusActive else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showEphemeralMenu,
                        onDismissRequest = { showEphemeralMenu = false }
                    ) {
                        listOf(
                            0L to stringResource(R.string.ephemeral_off),
                            30_000L to stringResource(R.string.ephemeral_30s),
                            300_000L to stringResource(R.string.ephemeral_5m),
                            3_600_000L to stringResource(R.string.ephemeral_1h),
                            86_400_000L to stringResource(R.string.ephemeral_24h)
                        ).forEach { (timerMs, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontWeight = if (ephemeralTimerMs == timerMs) FontWeight.Bold else FontWeight.Normal,
                                        color = if (ephemeralTimerMs == timerMs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    ephemeralTimerMs = timerMs
                                    EphemeralMessageManager.setTimerForConversation(context, conversationId, timerMs)
                                    showEphemeralMenu = false
                                }
                            )
                        }
                    }
                }

                // Sleek Compact E2EE Lock Icon Pill
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { showE2eeInfoDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.chat_status_encrypted),
                        tint = OrbisColorPalette.StatusActive,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // 3-Dots Options Menu
                Box {
                    IconButton(
                        onClick = { showOptionsDropdown = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showOptionsDropdown,
                        onDismissRequest = { showOptionsDropdown = false }
                    ) {
                        if (isGroup) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.group_members_dialog_title, groupParticipants.size), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showOptionsDropdown = false
                                    showGroupMembersDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.group_add_member_btn), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showOptionsDropdown = false
                                    showAddMemberDialog = true
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.not_friend_send_standard_sms), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showOptionsDropdown = false
                                    val standardText = context.getString(R.string.standard_invite_text)
                                    try {
                                        val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$targetPhone")).apply {
                                            putExtra("sms_body", standardText)
                                        }
                                        context.startActivity(smsIntent)
                                    } catch (e: Exception) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, standardText)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, null))
                                    }
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_menu_clear), fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showOptionsDropdown = false
                                showClearChatDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_menu_delete_conversation), fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showOptionsDropdown = false
                                showDeleteChatDialog = true
                            }
                        )

                        if (!isGroup) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_menu_block_contact), fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showOptionsDropdown = false
                                    showBlockContactDialog = true
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.install_warning_tooltip), fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { showOptionsDropdown = false }
                        )
                    }
                }
            }
        }

        // Security Banner / Modular Invitation Decision Component (Only for 1-to-1 chats)
        if (!isGroup && !isChatLockedForSender) {
            com.sha.orbis.ui.components.InvitationDecisionCard(
                senderName = conversationTitle,
                senderPhone = targetPhone,
                pendingReceivedRequest = pendingReceivedRequest,
                pendingSentRequest = pendingSentRequest,
                isFriend = isFriend,
                onAccept = {
                    val req = pendingReceivedRequest
                    if (req != null) {
                        friendRequestRepo.acceptRequest(req.id)
                    }
                    friendRequestRepo.acceptRequestForPhone(targetPhone)

                    val contacts = repository.loadContacts().toMutableList()
                    val contactIdx = contacts.indexOfFirst {
                        FriendRequestRepository.isSamePhone(it.phone, targetPhone) || it.name.equals(conversationTitle, ignoreCase = true)
                    }
                    val senderPubKey = req?.senderPublicKey ?: ""
                    val senderAvatar = req?.senderAvatarPath
                    if (contactIdx >= 0) {
                        contacts[contactIdx] = contacts[contactIdx].copy(
                            status = "Connecté 🛡️",
                            publicKey = senderPubKey.ifBlank { contacts[contactIdx].publicKey },
                            avatarPath = senderAvatar ?: contacts[contactIdx].avatarPath
                        )
                    } else {
                        contacts.add(0, Contact(
                            id = "c_$cleanTargetDigits",
                            name = conversationTitle,
                            phone = targetPhone,
                            publicKey = senderPubKey,
                            status = "Connecté 🛡️",
                            avatarPath = senderAvatar
                        ))
                    }
                    repository.saveContacts(contacts)

                    // Send Handshake ACK via Nostr + GSM SMS back to sender
                    val myIdentity = sessionManager.getOrCreateIdentity()
                    val myAvatarThumb = com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)
                    val recipientToSend = if (req != null && req.senderPhone.isNotBlank()) req.senderPhone else targetPhone
                    val normalizedTarget = ContactsPickerHelper.normalizePhoneNumber(recipientToSend)

                    // 1. Publish Nostr Sovereign Handshake ACK (Direct Internet delivery)
                    try {
                        val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                        nostrSync.publishFriendInvitationAck(
                            recipientPhone = normalizedTarget,
                            recipientPubkeyHex = senderPubKey.takeIf { it.isNotBlank() },
                            avatarBase64 = myAvatarThumb
                        )
                        nostrSync.refreshSubscriptions()
                    } catch (e: Exception) {
                        android.util.Log.w("ConversationScreen", "Erreur émission ACK Nostr: ${e.message}")
                    }

                    repository.ensureConversationForFriend(
                        phone = normalizedTarget,
                        name = conversationTitle,
                        initialMessage = context.getString(R.string.friends_connected_last_msg)
                    )
                    context.sendBroadcast(Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS))

                    messages = repository.loadMessages(conversationId)

                    pendingReceivedRequest = null
                    pendingSentRequest = null
                    isFriend = true
                    Toast.makeText(context, context.getString(R.string.invitation_accepted_toast, conversationTitle), Toast.LENGTH_SHORT).show()
                },
                onReject = {
                    if (pendingReceivedRequest != null) {
                        friendRequestRepo.rejectRequest(pendingReceivedRequest!!.id)
                    }
                    friendRequestRepo.rejectRequestForPhone(targetPhone)
                    pendingReceivedRequest = null
                    Toast.makeText(context, context.getString(R.string.invitation_rejected_toast), Toast.LENGTH_SHORT).show()
                },
                onBlock = {
                    if (pendingReceivedRequest != null) {
                        friendRequestRepo.rejectRequest(pendingReceivedRequest!!.id)
                    }
                    friendRequestRepo.rejectRequestForPhone(targetPhone)
                    com.sha.orbis.storage.BlockedContactsRepository(context).blockContact(targetPhone, conversationTitle)
                    Toast.makeText(context, context.getString(R.string.chat_toast_contact_blocked), Toast.LENGTH_SHORT).show()
                    onBack?.invoke()
                },
                onSendInviteSms = { showNotFriendDialog = true },
                onShareInvite = { showInviteInstallSheet = true },
                onForceUnlock = {
                    friendRequestRepo.acceptRequestForPhone(targetPhone)
                    val req = pendingReceivedRequest
                    friendRequestRepo.ensureAcceptedFriend(
                        phone = targetPhone,
                        name = conversationTitle,
                        publicKey = req?.senderPublicKey ?: "",
                        avatarPath = req?.senderAvatarPath
                    )
                    val contacts = repository.loadContacts().toMutableList()
                    val contactIdx = contacts.indexOfFirst {
                        FriendRequestRepository.isSamePhone(it.phone, targetPhone) || it.name.equals(conversationTitle, ignoreCase = true)
                    }
                    if (contactIdx >= 0) {
                        contacts[contactIdx] = contacts[contactIdx].copy(
                            status = "Connecté 🛡️"
                        )
                    } else {
                        contacts.add(0, Contact(
                            id = "c_$cleanTargetDigits",
                            name = conversationTitle,
                            phone = targetPhone,
                            publicKey = "DIRECT_VERIFIED",
                            status = "Connecté 🛡️"
                        ))
                    }
                    repository.saveContacts(contacts)
                    pendingSentRequest = null
                    isFriend = true
                    try {
                        com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).refreshSubscriptions()
                    } catch (_: Exception) {}
                    Toast.makeText(context, context.getString(R.string.invitation_direct_unlocked_toast, conversationTitle), Toast.LENGTH_SHORT).show()
                },
                onShareValidationCode = {
                    val identity = sessionManager.getOrCreateIdentity()
                    val shareText = "🔐 Validation Orbis pour $conversationTitle :\nORBIS_ACK:${identity.publicKeyBase64.take(30)}..."
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.invitation_share_key_btn)))
                }
            )
        }

        // Message List
        val chatBgColor = if (androidx.compose.foundation.isSystemInDarkTheme()) {
            androidx.compose.ui.graphics.Color(0xFF0F172A)
        } else {
            androidx.compose.ui.graphics.Color(0xFFF1F5F9)
        }

        val distinctMessages = remember(messages) { messages.distinctBy { it.id } }

        // Préchargement asynchrone sécurisé des aperçus de liens pour la discussion
        LaunchedEffect(distinctMessages) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                for (msg in distinctMessages.takeLast(25)) {
                    val url = LinkPreviewHelper.extractFirstUrl(msg.text)
                    if (!url.isNullOrBlank()) {
                        LinkPreviewHelper.prefetch(context, url)
                    }
                }
            }
        }

        if (isChatLockedForSender) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.chat_locked_pending_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.chat_locked_pending_desc, conversationTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onBack?.invoke() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.chat_locked_pending_btn))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(chatBgColor)
            ) {
            PullToRefreshBox(
                isRefreshing = isRefreshingChat,
                onRefresh = {
                    if (!isRefreshingChat) {
                        isRefreshingChat = true
                        messages = EphemeralMessageManager.purgeExpiredMessages(
                            context,
                            conversationId,
                            repository.loadMessages(conversationId)
                        )
                        coroutineScope.launch {
                            try {
                                NostrSyncManager.getInstance(context).reconnect(force = false)
                                kotlinx.coroutines.delay(800)
                                messages = EphemeralMessageManager.purgeExpiredMessages(
                                    context,
                                    conversationId,
                                    repository.loadMessages(conversationId)
                                )
                            } catch (_: Exception) {
                            } finally {
                                isRefreshingChat = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(items = distinctMessages, key = { _, it -> it.id }) { index, message ->
                    val showDateHeader = index == 0 || !isSameDay(message.timestamp, distinctMessages[index - 1].timestamp)
                    val myPhone = sessionManager.userPhone
                    val myDigits = myPhone.filter { it.isDigit() }
                    val isMine = message.senderId == "me" ||
                                 message.senderId.isBlank() ||
                                 (myDigits.length >= 8 && FriendRequestRepository.isSamePhone(message.senderId, myPhone))
                    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

                    val bubbleShapeSender = androidx.compose.foundation.shape.RoundedCornerShape(
                        topStart = 22.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp
                    )
                    val bubbleShapeReceiver = androidx.compose.foundation.shape.RoundedCornerShape(
                        topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp
                    )

                    // WhatsApp Premium palette (sender light-blue, receiver white/dark)
                    val bubbleBg = when {
                        isMine && isDark -> androidx.compose.ui.graphics.Color(0xFF1E3A5F)
                        isMine -> androidx.compose.ui.graphics.Color(0xFFDCF0FA)
                        isDark -> androidx.compose.ui.graphics.Color(0xFF1E293B)
                        else -> androidx.compose.ui.graphics.Color(0xFFFFFFFF)
                    }
                    val bubbleBorder = when {
                        isMine && isDark -> androidx.compose.ui.graphics.Color(0xFF2B4C7E)
                        isMine -> androidx.compose.ui.graphics.Color(0xFFBAE6FD)
                        isDark -> androidx.compose.ui.graphics.Color(0xFF334155)
                        else -> androidx.compose.ui.graphics.Color(0xFFE2E8F0)
                    }
                    val bubbleTextColor = when {
                        isMine && isDark -> androidx.compose.ui.graphics.Color(0xFFFFFFFF)
                        isMine -> androidx.compose.ui.graphics.Color(0xFF0F172A)
                        isDark -> androidx.compose.ui.graphics.Color(0xFFF1F5F9)
                        else -> androidx.compose.ui.graphics.Color(0xFF2D3748)
                    }
                    val bubbleSubtextColor = when {
                        isMine && isDark -> androidx.compose.ui.graphics.Color(0xFFFFFFFF).copy(alpha = 0.75f)
                        isMine -> androidx.compose.ui.graphics.Color(0xFF64748B)
                        isDark -> androidx.compose.ui.graphics.Color(0xFF94A3B8)
                        else -> androidx.compose.ui.graphics.Color(0xFF64748B)
                    }

                    val innerBubbleShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    val innerBubbleBg = bubbleBg.copy(alpha = if (isMine) 0.75f else 0.92f)
                    val innerBubbleBorder = bubbleBorder.copy(alpha = 0.6f)
                    val quoteCardBg = bubbleBorder.copy(alpha = if (isMine) 0.18f else 0.28f)
                    val quoteAccent = if (isMine) androidx.compose.ui.graphics.Color(0xFF0284C7) else bubbleTextColor.copy(alpha = 0.9f)
                    val linkPreviewBg = innerBubbleBg
                    val linkPreviewBorder = innerBubbleBorder

                    val timeFormatted = remember(message.timestamp) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (showDateHeader) {
                            ChatDateDivider(timestamp = message.timestamp)
                        }

                        // Strict Left / Right Alignment Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier.padding(
                                    bottom = if (message.reactions.isNotEmpty()) 12.dp else 0.dp
                                )
                            ) {
                                if (isMine) {
                                    Card(
                                        shape = bubbleShapeSender,
                                        colors = CardDefaults.cardColors(
                                            containerColor = bubbleBg
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, bubbleBorder),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 0.6.dp),
                                        modifier = Modifier
                                            .widthIn(min = 54.dp, max = 310.dp)
                                            .clickable { selectedMessageForMenu = message }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                    val text = message.text

                                    when {
                                        message.isDeletedForEveryone -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Block,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.chat_msg_deleted_by_you),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        // 1. Audio Voice Note (Ultra-Premium WhatsApp/Telegram Style)
                                        text.startsWith("[AUDIO:") -> {
                                            val parts = text.removePrefix("[AUDIO:").removeSuffix("]").split(":")
                                            val base64 = parts.getOrNull(0) ?: ""
                                            val durSec = parts.getOrNull(1)?.toIntOrNull() ?: 3
                                            val isPlaying = playingAudioId == message.id

                                            VoiceMessageBubble(
                                                messageId = message.id,
                                                base64Audio = base64,
                                                durSec = durSec,
                                                isMsgMine = true,
                                                isPlaying = isPlaying,
                                                onTogglePlay = {
                                                    if (isPlaying) {
                                                        AudioVoiceHelper.stopPlaying()
                                                        playingAudioId = null
                                                    } else {
                                                        val file = AudioVoiceHelper.base64ToFile(context, base64, message.id)
                                                        if (file != null) {
                                                            playingAudioId = message.id
                                                            AudioVoiceHelper.playAudio(file) {
                                                                playingAudioId = null
                                                            }
                                                        }
                                                    }
                                                },
                                                timestamp = message.timestamp,
                                                deliveryStatus = message.status
                                            )
                                        }

                                        // Album (Multi-Photo) Message Bubble
                                        MediaAttachmentHelper.isAlbumPayload(text) -> {
                                            val albumPayload = remember(text) { MediaAttachmentHelper.parseAlbumPayload(text) }
                                            if (albumPayload != null) {
                                                val resolvedImages = remember(albumPayload.id, albumPayload.images) {
                                                    albumPayload.images.mapIndexed { idx, b64 ->
                                                        val f = File(context.filesDir, "media/images/${albumPayload.id}_$idx.jpg")
                                                        if (f.exists() && f.length() > 0L) f.absolutePath else b64
                                                    }
                                                }
                                                AlbumMessageBubble(
                                                    images = resolvedImages,
                                                    caption = albumPayload.caption,
                                                    timestamp = message.timestamp,
                                                    isMsgMine = true,
                                                    deliveryStatus = message.status,
                                                    onImageClick = { _, imgBase64 ->
                                                        fullScreenImageSource = imgBase64
                                                        fullScreenImageCaption = albumPayload.caption.ifBlank { null }
                                                    }
                                                )
                                            }
                                        }

                                        // Image Message Bubble
                                        MediaAttachmentHelper.isImagePayload(text) -> {
                                            val imgPayload = remember(text) { MediaAttachmentHelper.parseImagePayload(text) }
                                            if (imgPayload != null) {
                                                val localImageFile = remember(imgPayload.id) {
                                                    val f = File(context.filesDir, "media/images/${imgPayload.id}.jpg")
                                                    if (f.exists() && f.length() > 0L) f.absolutePath else null
                                                }
                                                ImageMessageBubble(
                                                    base64OrPath = localImageFile ?: imgPayload.base64Data,
                                                    caption = imgPayload.caption,
                                                    timestamp = message.timestamp,
                                                    isMsgMine = true,
                                                    deliveryStatus = message.status,
                                                    onClick = {
                                                        selectedMessageForMenu = message
                                                    }
                                                )
                                            }
                                        }

                                        // Document Message Bubble
                                        MediaAttachmentHelper.isDocPayload(text) -> {
                                            val docPayload = remember(text) { MediaAttachmentHelper.parseDocPayload(text) }
                                            if (docPayload != null) {
                                                DocMessageBubble(
                                                    id = docPayload.id,
                                                    fileName = docPayload.fileName,
                                                    fileSizeFormatted = docPayload.fileSizeFormatted,
                                                    base64Data = docPayload.base64Data,
                                                    caption = docPayload.caption,
                                                    timestamp = message.timestamp,
                                                    isMsgMine = true,
                                                    deliveryStatus = message.status,
                                                    onClick = {
                                                        selectedMessageForMenu = message
                                                    }
                                                )
                                            }
                                        }

                                        // Video Message Bubble
                                        VideoMediaHelper.isVideoPayload(text) -> {
                                            val vidPayload = remember(text) { VideoMediaHelper.parseVideoPayload(text) }
                                            if (vidPayload != null) {
                                                VideoMessageBubble(
                                                    videoPayload = vidPayload,
                                                    timestamp = message.timestamp,
                                                    isMsgMine = true,
                                                    deliveryStatus = message.status,
                                                    onVideoClick = {
                                                        val localFile = VideoMediaHelper.getVideoFile(context, vidPayload.id)
                                                        fullScreenVideoSource = localFile?.takeIf { it.exists() }?.absolutePath ?: vidPayload.url ?: vidPayload.id
                                                    }
                                                )
                                            }
                                        }

                                        // 2. GPS Location Card Bubble
                                        LocationGpsHelper.parseGpsPayload(text) != null -> {
                                            val coords = LocationGpsHelper.parseGpsPayload(text)
                                            if (coords != null) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(innerBubbleShape)
                                                        .background(innerBubbleBg)
                                                        .border(1.dp, innerBubbleBorder, innerBubbleShape)
                                                        .padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.LocationOn,
                                                            contentDescription = null,
                                                            tint = quoteAccent,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.media_gps_label),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = bubbleTextColor
                                                        )
                                                    }

                                                    Text(
                                                        text = "Lat: ${"%.5f".format(coords.first)}, Lon: ${"%.5f".format(coords.second)}",
                                                        fontSize = 11.sp,
                                                        color = bubbleSubtextColor
                                                    )

                                                    androidx.compose.material3.Button(
                                                        onClick = {
                                                            LocationGpsHelper.openInMaps(context, coords.first, coords.second)
                                                        },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(32.dp),
                                                        shape = innerBubbleShape,
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = quoteCardBg,
                                                            contentColor = quoteAccent
                                                        )
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.media_gps_open_maps),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 3. Shared P2P Post (Facebook / Messenger style)
                                        SharedPostPayload.parse(text) != null -> {
                                            val shared = SharedPostPayload.parse(text)!!
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.widthIn(min = 180.dp, max = 270.dp)
                                            ) {
                                                if (!shared.note.isNullOrBlank()) {
                                                    Text(
                                                        text = shared.note,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = bubbleTextColor,
                                                        lineHeight = 18.sp
                                                    )
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                }

                                                Card(
                                                    shape = innerBubbleShape,
                                                    colors = CardDefaults.cardColors(containerColor = innerBubbleBg),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, innerBubbleBorder),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            OrbisAvatar(
                                                                avatarPath = shared.authorAvatarPath,
                                                                name = shared.authorName,
                                                                size = 28.dp
                                                            )
                                                            Column {
                                                                Text(
                                                                    text = shared.authorName,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = bubbleTextColor,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = stringResource(R.string.social_share_bubble_header),
                                                                    fontSize = 10.sp,
                                                                    color = quoteAccent,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                            }
                                                        }

                                                        Text(
                                                            text = shared.contentSnippet,
                                                            fontSize = 11.sp,
                                                            color = bubbleSubtextColor,
                                                            lineHeight = 16.sp,
                                                            maxLines = 3,
                                                            overflow = TextOverflow.Ellipsis
                                                        )

                                                        if (shared.hashtags.isNotEmpty()) {
                                                            Text(
                                                                text = shared.hashtags.joinToString(" ") { "#$it" },
                                                                fontSize = 10.sp,
                                                                color = quoteAccent,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(innerBubbleShape)
                                                                .background(quoteCardBg)
                                                                .clickable {
                                                                    val role = com.sha.orbis.admin.AdminSecurityHelper.getUserSocialRole(shared.authorPhone, context)
                                                                    onOpenWall?.invoke(shared.authorPhone, shared.authorName, shared.authorAvatarPath, role)
                                                                }
                                                                .padding(vertical = 5.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.social_share_view_wall_btn),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = quoteAccent
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 4. Quoted Reply Message
                                        text.startsWith("[QUOTE:") -> {
                                            val quoteEndIdx = text.indexOf("]\n")
                                            if (quoteEndIdx > 0) {
                                                val quoteMeta = text.substring(7, quoteEndIdx).split(":")
                                                val quoteAuthor = quoteMeta.getOrNull(0) ?: ""
                                                val quoteContent = quoteMeta.drop(1).joinToString(":")
                                                val actualMsg = text.substring(quoteEndIdx + 2)

                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Card(
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = quoteCardBg),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(3.dp)
                                                                    .height(26.dp)
                                                                    .clip(RoundedCornerShape(2.dp))
                                                                    .background(quoteAccent)
                                                            )
                                                            Column {
                                                                Text(
                                                                    text = quoteAuthor,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = quoteAccent
                                                                )
                                                                Text(
                                                                    text = quoteContent,
                                                                    fontSize = 11.sp,
                                                                    color = bubbleSubtextColor,
                                                                    maxLines = 1,
                                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                    val quotedUrl = remember(actualMsg) { LinkPreviewHelper.extractFirstUrl(actualMsg) }
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        LinkifiedText(
                                                            text = actualMsg,
                                                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                                            color = bubbleTextColor,
                                                            linkColor = quoteAccent
                                                        )
                                                        if (!quotedUrl.isNullOrBlank()) {
                                                            SocialLinkPreviewCard(
                                                                url = quotedUrl,
                                                                isMsgMine = true,
                                                                bubbleBgOverride = linkPreviewBg,
                                                                bubbleBorderOverride = linkPreviewBorder
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                val detectedUrl = remember(text) { LinkPreviewHelper.extractFirstUrl(text) }
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    LinkifiedText(
                                                        text = text,
                                                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                                        color = bubbleTextColor,
                                                        linkColor = quoteAccent
                                                    )
                                                    if (!detectedUrl.isNullOrBlank()) {
                                                        SocialLinkPreviewCard(
                                                            url = detectedUrl,
                                                            isMsgMine = true,
                                                            bubbleBgOverride = linkPreviewBg,
                                                            bubbleBorderOverride = linkPreviewBorder
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 5. Regular Text Message
                                        else -> {
                                            val detectedUrl = remember(text) { LinkPreviewHelper.extractFirstUrl(text) }
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                LinkifiedText(
                                                    text = text,
                                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                                    color = bubbleTextColor,
                                                    linkColor = quoteAccent
                                                )
                                                if (!detectedUrl.isNullOrBlank()) {
                                                    SocialLinkPreviewCard(
                                                        url = detectedUrl,
                                                        isMsgMine = true,
                                                        bubbleBgOverride = linkPreviewBg,
                                                        bubbleBorderOverride = linkPreviewBorder
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (message.status == MessageDeliveryStatus.FAILED) {
                                        Text(
                                            text = "⚠️ " + stringResource(R.string.msg_failed_hint),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    if (!MediaAttachmentHelper.isImagePayload(text) && !MediaAttachmentHelper.isDocPayload(text) && !MediaAttachmentHelper.isAlbumPayload(text) && !VideoMediaHelper.isVideoPayload(text) && !text.startsWith("[AUDIO:")) {
                                        Row(
                                            modifier = Modifier.align(Alignment.End),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = timeFormatted,
                                                fontSize = 10.sp,
                                                color = bubbleSubtextColor
                                            )
                                            val (checkIcon, checkTint) = when (message.status) {
                                                MessageDeliveryStatus.READ -> Icons.Default.DoneAll to androidx.compose.ui.graphics.Color(0xFF38BDF8) // Double check BLEU
                                                MessageDeliveryStatus.DELIVERED -> Icons.Default.DoneAll to androidx.compose.ui.graphics.Color(0xFFCBD5E1) // Double check GRIS
                                                MessageDeliveryStatus.SENT -> Icons.Default.Check to androidx.compose.ui.graphics.Color(0xFFCBD5E1) // Simple check GRIS
                                                MessageDeliveryStatus.SENDING -> Icons.Default.Schedule to bubbleSubtextColor.copy(alpha = 0.6f)
                                                MessageDeliveryStatus.FAILED -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
                                            }
                                            Icon(
                                                imageVector = checkIcon,
                                                contentDescription = null,
                                                tint = checkTint,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Card(
                                shape = bubbleShapeReceiver,
                                colors = CardDefaults.cardColors(
                                    containerColor = bubbleBg
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, bubbleBorder),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 0.6.dp),
                                modifier = Modifier
                                    .widthIn(min = 54.dp, max = 310.dp)
                                    .clickable { selectedMessageForMenu = message }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (isGroup && message.senderId != "me") {
                                        val senderContact = repository.loadContacts().find { FriendRequestRepository.isSamePhone(it.phone, message.senderId) || it.id == message.senderId }
                                        val displayName = senderContact?.name?.ifBlank { message.senderId } ?: message.senderId
                                        Text(
                                            text = displayName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                    val text = message.text

                                    when {
                                        message.isDeletedForEveryone -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Block,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.chat_msg_deleted_by_sender),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        // 1. Audio Voice Note Bubble (Ultra-Premium WhatsApp/Telegram Style)
                                        text.startsWith("[AUDIO:") -> {
                                            val parts = text.removePrefix("[AUDIO:").removeSuffix("]").split(":")
                                            val base64 = parts.getOrNull(0) ?: ""
                                            val durSec = parts.getOrNull(1)?.toIntOrNull() ?: 3
                                            val isPlaying = playingAudioId == message.id

                                            VoiceMessageBubble(
                                                messageId = message.id,
                                                base64Audio = base64,
                                                durSec = durSec,
                                                isMsgMine = false,
                                                isPlaying = isPlaying,
                                                onTogglePlay = {
                                                    if (isPlaying) {
                                                        AudioVoiceHelper.stopPlaying()
                                                        playingAudioId = null
                                                    } else {
                                                        val file = AudioVoiceHelper.base64ToFile(context, base64, message.id)
                                                        if (file != null) {
                                                            playingAudioId = message.id
                                                            AudioVoiceHelper.playAudio(file) {
                                                                playingAudioId = null
                                                            }
                                                        }
                                                    }
                                                },
                                                timestamp = message.timestamp,
                                                deliveryStatus = message.status
                                            )
                                        }

                                        // Album (Multi-Photo) Message Bubble
                                        MediaAttachmentHelper.isAlbumPayload(text) -> {
                                            val albumPayload = remember(text) { MediaAttachmentHelper.parseAlbumPayload(text) }
                                            if (albumPayload != null) {
                                                val resolvedImages = remember(albumPayload.id, albumPayload.images) {
                                                    albumPayload.images.mapIndexed { idx, b64 ->
                                                        val f = File(context.filesDir, "media/images/${albumPayload.id}_$idx.jpg")
                                                        if (f.exists() && f.length() > 0L) f.absolutePath else b64
                                                    }
                                                }
                                                AlbumMessageBubble(
                                                    images = resolvedImages,
                                                    caption = albumPayload.caption,
                                                    timestamp = message.timestamp,
                                                    isMsgMine = false,
                                                    deliveryStatus = message.status,
                                                    onImageClick = { _, imgBase64 ->
                                                        fullScreenImageSource = imgBase64
                                                        fullScreenImageCaption = albumPayload.caption.ifBlank { null }
                                                    }
                                                )
                                            }
                                        }

                                        // Image Message Bubble
                                        MediaAttachmentHelper.isImagePayload(text) -> {
                                            val imgPayload = remember(text) { MediaAttachmentHelper.parseImagePayload(text) }
                                            if (imgPayload != null) {
                                                val localImageFile = remember(imgPayload.id) {
                                                    val f = File(context.filesDir, "media/images/${imgPayload.id}.jpg")
                                                    if (f.exists() && f.length() > 0L) f.absolutePath else null
                                                }
                                                ImageMessageBubble(
                                                    base64OrPath = localImageFile ?: imgPayload.base64Data,
                                                    caption = imgPayload.caption,
                                                    timestamp = message.timestamp,
                                                    isMsgMine = false,
                                                    deliveryStatus = message.status,
                                                    onClick = {
                                                        selectedMessageForMenu = message
                                                    }
                                                )
                                            }
                                        }

                                        // Document Message Bubble
                                        MediaAttachmentHelper.isDocPayload(text) -> {
                                            val docPayload = remember(text) { MediaAttachmentHelper.parseDocPayload(text) }
                                            if (docPayload != null) {
                                                DocMessageBubble(
                                                    id = docPayload.id,
                                                    fileName = docPayload.fileName,
                                                    fileSizeFormatted = docPayload.fileSizeFormatted,
                                                    base64Data = docPayload.base64Data,
                                                    caption = docPayload.caption,
                                                    timestamp = message.timestamp,
                                                    isMsgMine = false,
                                                    deliveryStatus = message.status,
                                                    onClick = {
                                                        selectedMessageForMenu = message
                                                    }
                                                )
                                            }
                                        }

                                        // Video Message Bubble
                                        VideoMediaHelper.isVideoPayload(text) -> {
                                            val vidPayload = remember(text) { VideoMediaHelper.parseVideoPayload(text) }
                                            if (vidPayload != null) {
                                                VideoMessageBubble(
                                                    videoPayload = vidPayload,
                                                    timestamp = message.timestamp,
                                                    isMsgMine = false,
                                                    deliveryStatus = message.status,
                                                    onVideoClick = {
                                                        val localFile = VideoMediaHelper.getVideoFile(context, vidPayload.id)
                                                        fullScreenVideoSource = localFile?.takeIf { it.exists() }?.absolutePath ?: vidPayload.url ?: vidPayload.id
                                                    }
                                                )
                                            }
                                        }

                                        // 2. GPS Location Card Bubble
                                        LocationGpsHelper.parseGpsPayload(text) != null -> {
                                            val coords = LocationGpsHelper.parseGpsPayload(text)
                                            if (coords != null) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(innerBubbleShape)
                                                        .background(innerBubbleBg)
                                                        .border(1.dp, innerBubbleBorder, innerBubbleShape)
                                                        .padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.LocationOn,
                                                            contentDescription = null,
                                                            tint = quoteAccent,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.media_gps_label),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = bubbleTextColor
                                                        )
                                                    }
                                                    Text(
                                                        text = "Lat: %.4f, Lon: %.4f".format(coords.first, coords.second),
                                                        fontSize = 11.sp,
                                                        color = bubbleSubtextColor
                                                    )
                                                    androidx.compose.material3.Button(
                                                        onClick = { LocationGpsHelper.openInMaps(context, coords.first, coords.second) },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(32.dp),
                                                        shape = innerBubbleShape,
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = quoteCardBg,
                                                            contentColor = quoteAccent
                                                        )
                                                    ) {
                                                        Text(
                                                            text = "🗺️ ${stringResource(R.string.media_gps_open_maps)}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 3. Shared P2P Post (Facebook / Messenger style)
                                        SharedPostPayload.parse(text) != null -> {
                                            val shared = SharedPostPayload.parse(text)!!
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.widthIn(min = 180.dp, max = 270.dp)
                                            ) {
                                                if (!shared.note.isNullOrBlank()) {
                                                    Text(
                                                        text = shared.note,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = bubbleTextColor,
                                                        lineHeight = 18.sp
                                                    )
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                }

                                                Card(
                                                    shape = innerBubbleShape,
                                                    colors = CardDefaults.cardColors(containerColor = innerBubbleBg),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, innerBubbleBorder),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            OrbisAvatar(
                                                                avatarPath = shared.authorAvatarPath,
                                                                name = shared.authorName,
                                                                size = 28.dp
                                                            )
                                                            Column {
                                                                Text(
                                                                    text = shared.authorName,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = bubbleTextColor,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = stringResource(R.string.social_share_bubble_header),
                                                                    fontSize = 10.sp,
                                                                    color = quoteAccent,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                            }
                                                        }

                                                        Text(
                                                            text = shared.contentSnippet,
                                                            fontSize = 11.sp,
                                                            color = bubbleSubtextColor,
                                                            lineHeight = 16.sp,
                                                            maxLines = 3,
                                                            overflow = TextOverflow.Ellipsis
                                                        )

                                                        if (shared.hashtags.isNotEmpty()) {
                                                            Text(
                                                                text = shared.hashtags.joinToString(" ") { "#$it" },
                                                                fontSize = 10.sp,
                                                                color = quoteAccent,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(innerBubbleShape)
                                                                .background(quoteCardBg)
                                                                .clickable {
                                                                    val role = com.sha.orbis.admin.AdminSecurityHelper.getUserSocialRole(shared.authorPhone, context)
                                                                    onOpenWall?.invoke(shared.authorPhone, shared.authorName, shared.authorAvatarPath, role)
                                                                }
                                                                .padding(vertical = 5.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.social_share_view_wall_btn),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = quoteAccent
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 4. Quoted Reply Message
                                        text.startsWith("[QUOTE:") -> {
                                            val quoteEndIdx = text.indexOf("]\n")
                                            if (quoteEndIdx > 0) {
                                                val quoteMeta = text.substring(7, quoteEndIdx).split(":")
                                                val quoteAuthor = quoteMeta.getOrNull(0) ?: ""
                                                val quoteContent = quoteMeta.drop(1).joinToString(":")
                                                val actualMsg = text.substring(quoteEndIdx + 2)

                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Card(
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = quoteCardBg),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(3.dp)
                                                                    .height(26.dp)
                                                                    .clip(RoundedCornerShape(2.dp))
                                                                    .background(quoteAccent)
                                                            )
                                                            Column {
                                                                Text(
                                                                    text = quoteAuthor,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = quoteAccent
                                                                )
                                                                Text(
                                                                    text = quoteContent,
                                                                    fontSize = 11.sp,
                                                                    color = bubbleSubtextColor,
                                                                    maxLines = 1,
                                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                    val quotedUrl = remember(actualMsg) { LinkPreviewHelper.extractFirstUrl(actualMsg) }
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        LinkifiedText(
                                                            text = actualMsg,
                                                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                                            color = bubbleTextColor,
                                                            linkColor = quoteAccent
                                                        )
                                                        if (!quotedUrl.isNullOrBlank()) {
                                                            SocialLinkPreviewCard(
                                                                url = quotedUrl,
                                                                isMsgMine = false,
                                                                bubbleBgOverride = linkPreviewBg,
                                                                bubbleBorderOverride = linkPreviewBorder
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                val detectedUrl = remember(text) { LinkPreviewHelper.extractFirstUrl(text) }
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    LinkifiedText(
                                                        text = text,
                                                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                                        color = bubbleTextColor,
                                                        linkColor = quoteAccent
                                                    )
                                                    if (!detectedUrl.isNullOrBlank()) {
                                                        SocialLinkPreviewCard(
                                                            url = detectedUrl,
                                                            isMsgMine = false,
                                                            bubbleBgOverride = linkPreviewBg,
                                                            bubbleBorderOverride = linkPreviewBorder
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 5. Regular Text Message
                                        else -> {
                                            val detectedUrl = remember(text) { LinkPreviewHelper.extractFirstUrl(text) }
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                LinkifiedText(
                                                    text = text,
                                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                                    color = bubbleTextColor,
                                                    linkColor = quoteAccent
                                                )
                                                if (!detectedUrl.isNullOrBlank()) {
                                                    SocialLinkPreviewCard(
                                                        url = detectedUrl,
                                                        isMsgMine = false,
                                                        bubbleBgOverride = linkPreviewBg,
                                                        bubbleBorderOverride = linkPreviewBorder
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (!MediaAttachmentHelper.isImagePayload(text) && !MediaAttachmentHelper.isDocPayload(text) && !MediaAttachmentHelper.isAlbumPayload(text) && !VideoMediaHelper.isVideoPayload(text) && !text.startsWith("[AUDIO:")) {
                                        Row(
                                            modifier = Modifier.align(Alignment.End),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = timeFormatted,
                                                fontSize = 10.sp,
                                                color = bubbleSubtextColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (message.reactions.isNotEmpty()) {
                            WhatsAppReactionPill(
                                reactions = message.reactions,
                                isMine = isMine,
                                onClick = { selectedMessageForMenu = message },
                                modifier = Modifier
                                    .align(if (isMine) Alignment.BottomEnd else Alignment.BottomStart)
                                    .offset(
                                        x = if (isMine) (-12).dp else 12.dp,
                                        y = 10.dp
                                    )
                            )
                        }

                        // Context Menu
                        DropdownMenu(
                            expanded = selectedMessageForMenu?.id == message.id,
                            onDismissRequest = { selectedMessageForMenu = null }
                        ) {
                            // Quick Emoji Reaction Bar (8 Emojis)
                            val quickChatEmojis = listOf("❤️", "👍", "😂", "🔥", "😮", "😢", "👏", "💡")
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                quickChatEmojis.forEach { emoji ->
                                    val isReacted = message.reactions.entries.any {
                                        it.value == emoji && FriendRequestRepository.isSamePhone(it.key, sessionManager.userPhone)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isReacted) quoteCardBg
                                                else bubbleBorder.copy(alpha = 0.12f)
                                            )
                                            .clickable {
                                                sendReaction(message, emoji)
                                                selectedMessageForMenu = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 18.sp)
                                    }
                                }
                            }
                            HorizontalDivider(color = bubbleBorder.copy(alpha = 0.4f))

                            if (MediaAttachmentHelper.isImagePayload(message.text)) {
                                DropdownMenuItem(
                                    text = {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                stringResource(R.string.chat_action_open_image),
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        val img = MediaAttachmentHelper.parseImagePayload(message.text)
                                        if (img != null) {
                                            fullScreenImageSource = img.base64Data
                                            fullScreenImageCaption = img.caption
                                        }
                                        selectedMessageForMenu = null
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                                )
                                DropdownMenuItem(
                                    text = {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                stringResource(R.string.media_download_image),
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.FileDownload,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        val img = MediaAttachmentHelper.parseImagePayload(message.text)
                                        if (img != null) {
                                            MediaDownloadManager.saveImageAsync(context, img.base64Data, img.caption)
                                        }
                                        selectedMessageForMenu = null
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                                )
                            }
                            if (MediaAttachmentHelper.isDocPayload(message.text)) {
                                DropdownMenuItem(
                                    text = {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                stringResource(R.string.chat_action_open_doc),
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        val doc = MediaAttachmentHelper.parseDocPayload(message.text)
                                        if (doc != null) {
                                            val file = MediaAttachmentHelper.base64ToDocFile(context, doc.base64Data, doc.fileName, doc.id)
                                            if (file != null) {
                                                MediaAttachmentHelper.openDocument(context, file)
                                            }
                                        }
                                        selectedMessageForMenu = null
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                                )
                                DropdownMenuItem(
                                    text = {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                stringResource(R.string.media_download_doc),
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.FileDownload,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        val doc = MediaAttachmentHelper.parseDocPayload(message.text)
                                        if (doc != null) {
                                            MediaDownloadManager.saveDocAsync(context, doc.base64Data, doc.fileName)
                                        }
                                        selectedMessageForMenu = null
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                                )
                            }

                            DropdownMenuItem(
                                text = {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.chat_action_reply),
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    replyingToMessage = message
                                    selectedMessageForMenu = null
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                )
                            )
                            if (isMine && message.status == MessageDeliveryStatus.FAILED) {
                                DropdownMenuItem(
                                    text = {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                stringResource(R.string.msg_retry_btn),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        val textToRetry = message.text
                                        messages = messages.filterNot { it.id == message.id }
                                        repository.saveMessages(conversationId, messages)
                                        sendMessage(textToRetry)
                                        selectedMessageForMenu = null
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.chat_action_copy),
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val textToCopy = when {
                                        MediaAttachmentHelper.isImagePayload(message.text) -> {
                                            val img = MediaAttachmentHelper.parseImagePayload(message.text)
                                            img?.caption?.ifBlank { null } ?: message.text
                                        }
                                        MediaAttachmentHelper.isDocPayload(message.text) -> {
                                            val doc = MediaAttachmentHelper.parseDocPayload(message.text)
                                            doc?.caption?.ifBlank { null } ?: doc?.fileName ?: message.text
                                        }
                                        else -> message.text
                                    }
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Orbis Message", textToCopy))
                                    Toast.makeText(context, context.getString(R.string.chat_copied_toast), Toast.LENGTH_SHORT).show()
                                    selectedMessageForMenu = null
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                )
                            )
                            DropdownMenuItem(
                                text = {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.chat_action_share_friends),
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    messageToShareFriends = message
                                    selectedMessageForMenu = null
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                )
                            )
                            DropdownMenuItem(
                                text = {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.chat_action_share_wall),
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DynamicFeed,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    messageToShareWall = message
                                    selectedMessageForMenu = null
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                )
                            )
                            DropdownMenuItem(
                                text = {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.chat_action_delete),
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    messageToDelete = message
                                    selectedMessageForMenu = null
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                )
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

        // WhatsApp Style Input Bar / Voice Recording Bar
        if (isRecordingAudio) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                VoiceRecordingBar(
                    onCancel = {
                        AudioVoiceHelper.cancelRecording()
                        isRecordingAudio = false
                        Toast.makeText(context, context.getString(R.string.media_voice_cancelled), Toast.LENGTH_SHORT).show()
                    },
                    onSend = {
                        val (file, dur) = AudioVoiceHelper.stopRecording()
                        isRecordingAudio = false
                        if (file != null) {
                            if (dur > AudioVoiceHelper.MAX_VOICE_DURATION_SECONDS) {
                                Toast.makeText(context, context.getString(R.string.chat_voice_too_long_nostr), Toast.LENGTH_LONG).show()
                                return@VoiceRecordingBar
                            }
                            val base64 = AudioVoiceHelper.fileToBase64(file)
                            if (base64.isNotEmpty()) {
                                if (!AudioVoiceHelper.isPayloadWithinNostrLimit(base64)) {
                                    Toast.makeText(context, context.getString(R.string.chat_voice_too_long_nostr), Toast.LENGTH_LONG).show()
                                    return@VoiceRecordingBar
                                }
                                val audioPayload = "[AUDIO:$base64:$dur]"
                                sendMessage(audioPayload)
                            }
                        }
                    }
                )
            }
        } else {
            val inputIsDark = androidx.compose.foundation.isSystemInDarkTheme()
            val inputBarBg = if (inputIsDark) androidx.compose.ui.graphics.Color(0xFF1E293B) else androidx.compose.ui.graphics.Color(0xFFFFFFFF)
            val inputBarBorder = if (inputIsDark) androidx.compose.ui.graphics.Color(0xFF334155) else androidx.compose.ui.graphics.Color(0xFFE2E8F0)
            val inputTextColor = if (inputIsDark) androidx.compose.ui.graphics.Color(0xFFF1F5F9) else androidx.compose.ui.graphics.Color(0xFF2D3748)
            val inputSubtextColor = if (inputIsDark) androidx.compose.ui.graphics.Color(0xFF94A3B8) else androidx.compose.ui.graphics.Color(0xFF64748B)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            ) {
                // Quoted Message Banner Preview
                if (replyingToMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Column {
                                val authorName = if (replyingToMessage?.senderId == "me" || replyingToMessage?.senderId.isNullOrBlank()) "Moi" else conversationTitle
                                Text(
                                    text = "Répondre à $authorName",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val replySnippet = remember(replyingToMessage) {
                                    val t = replyingToMessage?.text.orEmpty()
                                    when {
                                        MediaAttachmentHelper.isImagePayload(t) -> {
                                            val img = MediaAttachmentHelper.parseImagePayload(t)
                                            if (!img?.caption.isNullOrBlank()) "📷 ${img?.caption}" else "📷 Photo"
                                        }
                                        MediaAttachmentHelper.isDocPayload(t) -> {
                                            val doc = MediaAttachmentHelper.parseDocPayload(t)
                                            if (!doc?.caption.isNullOrBlank()) "📄 ${doc?.caption} (${doc?.fileName})" else "📄 ${doc?.fileName ?: "Document"}"
                                        }
                                        VideoMediaHelper.isVideoPayload(t) -> {
                                            val vid = VideoMediaHelper.parseVideoPayload(t)
                                            if (!vid?.caption.isNullOrBlank()) "🎥 ${vid?.caption}" else "🎥 Vidéo"
                                        }
                                        t.startsWith("[AUDIO:") -> "🎵 Note vocale"
                                        LocationGpsHelper.parseGpsPayload(t) != null -> "📍 Localisation GPS"
                                        else -> t.take(60)
                                    }
                                }
                                Text(
                                    text = replySnippet,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { replyingToMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Annuler", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // ORBIS Reply-LLM Smart Reply Row
                OrbisSmartReplyRow(
                    suggestions = smartReplies,
                    onSelectReply = { suggestionText ->
                        draft = suggestionText
                        val incoming = latestIncomingMessage?.text ?: ""
                        if (incoming.isNotBlank()) {
                            OrbisReplyEngine.learnFromUserSentMessage(context, incoming, suggestionText)
                        }
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Attachment Menu Trigger (+)
                    IconButton(
                        onClick = { showAttachmentPicker = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.chat_attach_title),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Text Input Box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(inputBarBg)
                            .border(1.dp, inputBarBorder, RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (draft.isEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_input_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = inputSubtextColor
                            )
                        }

                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = TextStyle(
                                color = inputTextColor,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Action Button: Send OR Mic for Voice Note
                    if (draft.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val textToSend = if (replyingToMessage != null) {
                                    val quoteAuthor = if (replyingToMessage?.senderId == "me" || replyingToMessage?.senderId.isNullOrBlank()) "Moi" else conversationTitle
                                    val quoteSnippet = replyingToMessage?.text?.take(40)?.replace("\n", " ") ?: ""
                                    val formatted = "[QUOTE:$quoteAuthor:$quoteSnippet]\n${draft.trim()}"
                                    replyingToMessage = null
                                    formatted
                                } else {
                                    draft.trim()
                                }
                                sendMessage(textToSend)
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.send_sms),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                audioPermissionLauncher.launch()
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Note Vocale",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modular Dialog: Choose between Case 1 (Already has OrbisNet) and Case 2 (Invite to Install)
    if (showNotFriendDialog) {
        com.sha.orbis.ui.components.ChooseInviteTypeDialog(
            targetName = conversationTitle,
            onChooseHasApp = {
                val cleanPhone = targetPhone.trim()
                if (cleanPhone.filter { it.isDigit() }.length < 8) {
                    Toast.makeText(context, "Numéro de contact introuvable ou invalide ($targetPhone)", Toast.LENGTH_LONG).show()
                    return@ChooseInviteTypeDialog
                }
                val identity = sessionManager.getOrCreateIdentity()
                val freshKey = com.sha.orbis.security.AesCipher.generateKeyBase64()
                val myAvatarThumb = com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)

                // 1. Direct Internet Delivery: Publish Nostr Sovereign Friend Invitation
                try {
                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                    nostrSync.publishFriendInvitation(
                        recipientPhone = cleanPhone,
                        groupKey = freshKey,
                        avatarBase64 = myAvatarThumb
                    )
                } catch (e: Exception) {
                    android.util.Log.w("ConversationScreen", "Erreur émission invite Nostr: ${e.message}")
                }

                friendRequestRepo.addOrUpdate(
                    com.sha.orbis.model.FriendRequest(
                        id = "req_sent_${System.currentTimeMillis()}",
                        senderPhone = cleanPhone,
                        senderName = conversationTitle,
                        senderPublicKey = "",
                        direction = com.sha.orbis.model.RequestDirection.SENT,
                        groupKey = freshKey
                    )
                )
                pendingSentRequest = friendRequestRepo.getPendingSentForPhone(targetPhone)
                com.sha.orbis.data.OrbisBadgeHub.refresh(context)
                Toast.makeText(context, context.getString(R.string.invite_sent_success), Toast.LENGTH_SHORT).show()
                showNotFriendDialog = false
            },
            onChooseNoApp = {
                showInviteInstallSheet = true
                showNotFriendDialog = false
            },
            onDismiss = { showNotFriendDialog = false }
        )
    }

    if (showInviteInstallSheet) {
        com.sha.orbis.ui.components.InviteToInstallBottomSheet(
            targetName = conversationTitle,
            targetPhone = targetPhone,
            onDismiss = { showInviteInstallSheet = false }
        )
    }

    // 1. WhatsApp-Style Delete Message Dialog
    if (messageToDelete != null) {
        val msg = messageToDelete!!
        val myPhone = sessionManager.userPhone
        val myDigits = myPhone.filter { it.isDigit() }
        val isMsgMine = msg.senderId == "me" ||
                        msg.senderId.isBlank() ||
                        (myDigits.length >= 8 && FriendRequestRepository.isSamePhone(msg.senderId, myPhone))
        val canDeleteForEveryone = isMsgMine && !msg.isDeletedForEveryone

        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = {
                Text(
                    text = stringResource(R.string.delete_dialog_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (canDeleteForEveryone) {
                        Button(
                            onClick = {
                                val revokePacket = com.sha.orbis.model.MessageRevocation.encode(msg)
                                val revokeTarget = com.sha.orbis.model.MessageRevocation.fromMessage(msg)

                                // Mark locally as deleted for everyone
                                repository.deleteMessageForEveryone(conversationId, revokeTarget)
                                messages = repository.loadMessages(conversationId)

                                // Send sovereign Nostr DM revocation packet to peer
                                val revokeRecipients = if (isGroup) {
                                    val contacts = repository.loadContacts()
                                    (groupParticipants - sessionManager.userPhone).mapNotNull { memberPhone ->
                                        val memberTarget = memberPhone.trim()
                                        if (FriendRequestRepository.isValidNostrKey(memberTarget)) {
                                            memberTarget
                                        } else {
                                            val c = contacts.find {
                                                FriendRequestRepository.isSamePhone(it.phone, memberTarget) ||
                                                    it.name.equals(memberTarget, ignoreCase = true)
                                            }
                                            val r = friendRequestRepo.loadRequests().find {
                                                FriendRequestRepository.isSamePhone(it.senderPhone, memberTarget)
                                            }
                                            c?.publicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
                                                ?: r?.senderPublicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }
                                        }
                                    }.distinct()
                                } else {
                                    listOfNotNull(resolveRecipientNostrKey())
                                }

                                if (revokeRecipients.isNotEmpty()) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                        revokeRecipients.forEach { recipientKey ->
                                            try {
                                                nostrSync.sendDirectMessage(
                                                    recipientNpubOrHex = recipientKey,
                                                    conversationId = conversationId,
                                                    text = revokePacket
                                                )
                                            } catch (e: Exception) {
                                                android.util.Log.w("ConversationScreen", "Error sending Nostr revoke: ${e.message}")
                                            }
                                        }
                                    }
                                }

                                messageToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.delete_for_everyone),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            repository.deleteMessageForMe(conversationId, msg.id)
                            messages = repository.loadMessages(conversationId)
                            messageToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.delete_for_me),
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Share Message Dialogs (Friends multi-forward and Wall publish)
    if (messageToShareFriends != null) {
        ShareChatMessageToFriendsDialog(
            message = messageToShareFriends!!,
            onDismiss = { messageToShareFriends = null }
        )
    }

    if (messageToShareWall != null) {
        ShareChatMessageToWallDialog(
            message = messageToShareWall!!,
            onDismiss = { messageToShareWall = null }
        )
    }

    // 2. Clear Chat Dialog
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.chat_menu_clear_confirm_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.chat_menu_clear_confirm_desc),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.clearConversation(conversationId)
                        messages = emptyList()
                        showClearChatDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.chat_menu_clear),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 3. Delete Conversation Dialog
    if (showDeleteChatDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteChatDialog = false },
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
                        repository.deleteConversation(conversationId)
                        showDeleteChatDialog = false
                        onBack?.invoke()
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
                TextButton(onClick = { showDeleteChatDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 4. Block Contact Confirmation Dialog
    if (showBlockContactDialog) {
        AlertDialog(
            onDismissRequest = { showBlockContactDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.block_contact_confirm_title, conversationTitle),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.block_contact_confirm_desc),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val blockedRepo = com.sha.orbis.storage.BlockedContactsRepository(context)
                        blockedRepo.blockContact(targetPhone, conversationTitle)
                        showBlockContactDialog = false
                        Toast.makeText(context, context.getString(R.string.chat_toast_contact_blocked), Toast.LENGTH_SHORT).show()
                        onBack?.invoke()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.block_contact_btn),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockContactDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 5. Contact Language Selection / Manual Override Dialog
    if (showLanguageDialog) {
        val availableLangs = listOf(
            "dz" to stringResource(R.string.contact_lang_option_dz),
            "fr" to stringResource(R.string.contact_lang_option_fr),
            "en" to stringResource(R.string.contact_lang_option_en),
            "ar" to stringResource(R.string.contact_lang_option_ar)
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.contact_lang_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.contact_lang_dialog_desc),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    availableLangs.forEach { (code, label) ->
                        val isSelected = currentContactLang.equals(code, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showLanguageDialog = false
                                    currentContactLang = code
                                    com.sha.orbis.ai.affinity.OrbisPeerLanguageEngine.setManualLanguage(context, targetPhone, code)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.contact_lang_updated_toast, label),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showGroupMembersDialog) {
        GroupMembersDialog(
            groupTitle = conversationTitle,
            participants = groupParticipants,
            currentPhone = sessionManager.userPhone,
            contacts = repository.loadContacts(),
            onAddMemberClick = { showAddMemberDialog = true },
            onDismiss = { showGroupMembersDialog = false }
        )
    }

    if (showAddMemberDialog) {
        AddGroupMemberDialog(
            existingParticipants = groupParticipants,
            allContacts = repository.loadContacts(),
            onMemberSelected = { newContact ->
                showAddMemberDialog = false
                val updated = (groupParticipants + newContact.phone).distinct()
                groupParticipants = updated
                val convs = repository.loadConversations().toMutableList()
                val idx = convs.indexOfFirst { it.id == conversationId }
                if (idx >= 0) {
                    convs[idx] = convs[idx].copy(participants = updated)
                    repository.saveConversations(convs)
                }
                // Send invitation via Nostr Direct Message (100% sovereign Nostr)
                val memberTarget = newContact.phone.trim()
                val targetNpubOrHex = newContact.publicKey.takeIf { FriendRequestRepository.isValidNostrKey(it) } ?: memberTarget
                val nostrSync = try { com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context) } catch (_: Exception) { null }
                val isNostr = FriendRequestRepository.isValidNostrKey(targetNpubOrHex)
                val resolvedKey: String? = if (isNostr) targetNpubOrHex else friendRequestRepo.resolveNostrPubkeyForPhone(memberTarget)
                if (nostrSync != null && !resolvedKey.isNullOrBlank()) {
                    try {
                        nostrSync.sendDirectMessage(
                            recipientNpubOrHex = resolvedKey,
                            conversationId = conversationId,
                            text = context.getString(R.string.group_invite_sms_text, conversationTitle)
                        )
                    } catch (_: Exception) {}
                }
                Toast.makeText(context, context.getString(R.string.group_member_added_toast, newContact.name), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAddMemberDialog = false }
        )
    }

    // Pre-Call Choice Dialog (Voice vs Video Call)
    if (showPreCallDialog) {
        OrbisPreCallDialog(
            contactName = conversationTitle,
            onDismiss = { showPreCallDialog = false },
            onConfirmVoiceCall = {
                showPreCallDialog = false
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    callAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
                val contact = repository.loadContacts().find { it.phone == targetPhone || it.name.equals(conversationTitle, ignoreCase = true) }
                val peerNostrKey = resolveRecipientNostrKey() ?: contact?.publicKey
                OrbisCallManager.startOutgoingCall(
                    context = context,
                    peerPhone = targetPhone,
                    peerName = conversationTitle,
                    peerAvatar = contact?.avatarPath,
                    myPhone = sessionManager.userPhone,
                    peerNostrKey = peerNostrKey,
                    isVideoCall = false
                )
            },
            onConfirmVideoCall = {
                showPreCallDialog = false
                val needed = mutableListOf<String>()
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    needed.add(android.Manifest.permission.RECORD_AUDIO)
                }
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.CAMERA
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    needed.add(android.Manifest.permission.CAMERA)
                }
                if (needed.isNotEmpty()) {
                    callPermissionsLauncher.launch(needed.toTypedArray())
                }
                val contact = repository.loadContacts().find { it.phone == targetPhone || it.name.equals(conversationTitle, ignoreCase = true) }
                val peerNostrKey = resolveRecipientNostrKey() ?: contact?.publicKey
                OrbisCallManager.startOutgoingCall(
                    context = context,
                    peerPhone = targetPhone,
                    peerName = conversationTitle,
                    peerAvatar = contact?.avatarPath,
                    myPhone = sessionManager.userPhone,
                    peerNostrKey = peerNostrKey,
                    isVideoCall = true
                )
            }
        )
    }


    // Call Requirement Dialog (Peer Not on Orbis)
    if (showCallRequirementDialog) {
        OrbisCallRequirementDialog(
            contactName = conversationTitle,
            onDismiss = { showCallRequirementDialog = false },
            onInviteSms = {
                showCallRequirementDialog = false
                val inviteText = context.getString(R.string.call_req_invite_sms_text)
                try {
                    val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$targetPhone")).apply {
                        putExtra("sms_body", inviteText)
                    }
                    context.startActivity(smsIntent)
                } catch (e: Exception) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, inviteText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                }
            }
        )
    }

    // E2EE Info Dialog (When tapping the compact lock icon)
    if (showE2eeInfoDialog) {
        AlertDialog(
            onDismissRequest = { showE2eeInfoDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = OrbisColorPalette.StatusActive,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.chat_status_encrypted),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = stringResource(R.string.chat_e2ee_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(onClick = { showE2eeInfoDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    // Attachment Picker Bottom Sheet (WhatsApp/Telegram Style)
    if (showAttachmentPicker) {
        AttachmentPickerBottomSheet(
            onDismiss = { showAttachmentPicker = false },
            onPickGallery = {
                showAttachmentPicker = false
                photoPickerLauncher.launch("image/*")
            },
            onPickVideo = {
                showAttachmentPicker = false
                videoPickerLauncher.launch("video/*")
            },
            onPickCamera = {
                showAttachmentPicker = false
                cameraPermissionLauncher.launch()
            },
            onPickDocument = {
                showAttachmentPicker = false
                docPickerLauncher.launch("*/*")
            },
            onPickGps = {
                showAttachmentPicker = false
                requestAndShareGpsLocation()
            }
        )
    }

    // Full Screen Lightbox Zoom Viewer
    if (fullScreenImageSource != null) {
        FullScreenImageViewerDialog(
            imagePathOrBase64 = fullScreenImageSource!!,
            title = fullScreenImageCaption ?: stringResource(R.string.chat_image_viewer_title),
            onDismiss = {
                fullScreenImageSource = null
                fullScreenImageCaption = null
            }
        )
    }

    // Full Screen Video Player
    if (fullScreenVideoSource != null) {
        FullScreenVideoDialog(
            videoPathOrId = fullScreenVideoSource!!,
            showDownloadButton = true,
            onDismiss = { _, _ ->
                fullScreenVideoSource = null
            }
        )
    }

    // Attachment Preview & Caption Dialog (WhatsApp / Telegram style)
    if (pendingAttachment != null) {
        AttachmentPreviewDialog(
            attachment = pendingAttachment!!,
            onDismiss = { pendingAttachment = null },
            onSend = { caption ->
                val current = pendingAttachment
                pendingAttachment = null
                when (current) {
                    is PendingAttachment.Image -> {
                        val imgId = current.file?.nameWithoutExtension ?: "img_${System.currentTimeMillis()}"
                        val payload = MediaAttachmentHelper.buildImagePayload(
                            id = imgId,
                            base64 = current.base64,
                            caption = caption
                        )
                        sendMessage(payload)
                    }
                    is PendingAttachment.MultiImage -> {
                        val payload = MediaAttachmentHelper.buildAlbumPayload(
                            id = "album_${System.currentTimeMillis()}",
                            images = current.images.map { it.base64 },
                            caption = caption
                        )
                        sendMessage(payload)
                    }
                    is PendingAttachment.Document -> {
                        val id = "doc_${System.currentTimeMillis()}"
                        val payload = MediaAttachmentHelper.buildDocPayload(
                            id = id,
                            fileName = current.displayName,
                            fileSizeFormatted = current.fileSizeFormatted,
                            base64 = current.base64,
                            caption = caption
                        )
                        sendMessage(payload)
                    }
                    is PendingAttachment.Video -> {
                        val payload = VideoMediaHelper.buildVideoPayload(
                            id = current.videoId,
                            durationMs = current.durationMs,
                            width = current.width,
                            height = current.height,
                            caption = caption,
                            url = current.url,
                            base64Data = current.base64
                        )
                        sendMessage(payload)
                    }
                    null -> {}
                }
            }
        )
    }

    if (isProcessingVideo) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { (videoProcessProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.size(52.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = stringResource(R.string.video_compressing_progress, videoProcessProgress),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (isProcessingPhoto) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { (photoProcessProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.size(52.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = stringResource(R.string.chat_photo_compressing_progress, photoProcessProgress),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupMembersDialog(
    groupTitle: String,
    participants: List<String>,
    currentPhone: String,
    contacts: List<Contact>,
    onAddMemberClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = stringResource(R.string.group_members_dialog_title, participants.size), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.group_members_dialog_header, groupTitle),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(participants) { phone ->
                        val isMe = FriendRequestRepository.isSamePhone(phone, currentPhone)
                        val contact = contacts.find { FriendRequestRepository.isSamePhone(it.phone, phone) }
                        val displayName = if (isMe) stringResource(R.string.group_role_me_admin) else contact?.name?.ifBlank { phone } ?: phone

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OrbisAvatar(avatarPath = contact?.avatarPath, name = displayName, size = 32.dp)
                                Column {
                                    Text(text = displayName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(text = phone, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isMe) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = stringResource(R.string.group_role_admin), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onAddMemberClick()
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.group_add_member_btn), fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun AddGroupMemberDialog(
    existingParticipants: List<String>,
    allContacts: List<Contact>,
    onMemberSelected: (Contact) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val candidates = remember(allContacts, existingParticipants, query) {
        allContacts.filter { c ->
            existingParticipants.none { FriendRequestRepository.isSamePhone(it, c.phone) } &&
            (query.isBlank() || c.name.contains(query, ignoreCase = true) || c.phone.contains(query))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_add_member_dialog_title), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.group_search_contact_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (candidates.isEmpty()) {
                    Text(stringResource(R.string.group_add_member_empty), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(candidates) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onMemberSelected(c) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OrbisAvatar(avatarPath = c.avatarPath, name = c.name, size = 32.dp)
                                    Column {
                                        Text(text = c.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(text = c.phone, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun WhatsAppReactionPill(
    reactions: Map<String, String>,
    isMine: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (reactions.isEmpty()) return

    val grouped = remember(reactions) {
        reactions.values
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
    }
    val totalCount = reactions.size
    val topEmojis = grouped.take(3).map { it.key }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val containerBg = if (isDark) androidx.compose.ui.graphics.Color(0xFF1E293B) else androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val borderColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF334155) else androidx.compose.ui.graphics.Color(0xFFE2E8F0)
    val numberColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF94A3B8) else androidx.compose.ui.graphics.Color(0xFF64748B)

    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            topEmojis.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 13.5.sp,
                    lineHeight = 15.sp
                )
            }
            if (totalCount > 1) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = totalCount.toString(),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = numberColor,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    if (t1 <= 0L || t2 <= 0L) return false
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
           c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

private fun formatChatDateHeader(timestamp: Long, context: Context): String {
    if (timestamp <= 0L) return ""
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val isSameYear = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
    val isSameDay = isSameYear && now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                      yesterday.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)

    val diffMillis = now.timeInMillis - timestamp
    return when {
        isSameDay -> context.getString(R.string.date_today)
        isYesterday -> context.getString(R.string.date_yesterday)
        diffMillis in 0 until (7 * 24 * 60 * 60 * 1000L) && diffMillis > 0 -> {
            val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
            dayName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        isSameYear -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
private fun ChatDateDivider(
    timestamp: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateText = remember(timestamp) {
        formatChatDateHeader(timestamp, context)
    }
    if (dateText.isBlank()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            thickness = 0.5.dp
        )
        Text(
            text = dateText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            thickness = 0.5.dp
        )
    }
}
