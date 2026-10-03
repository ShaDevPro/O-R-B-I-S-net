package com.sha.orbis.ui.share

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sha.orbis.R
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.data.ContactsPickerHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.media.LinkPreviewHelper
import com.sha.orbis.media.MediaAttachmentHelper
import com.sha.orbis.model.Contact
import com.sha.orbis.model.Message
import com.sha.orbis.model.MessageDeliveryStatus
import com.sha.orbis.nostr.service.NostrSyncManager
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.storage.SocialRepository
import com.sha.orbis.ui.components.AvatarManager
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.conversation.SocialLinkPreviewCard
import com.sha.orbis.ui.social.PostExclusionPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Données extraites d'un partage externe (Facebook, Instagram, TikTok, etc.)
 */
data class ExternalSharePayload(
    val text: String? = null,
    val subject: String? = null,
    val singleMediaUri: Uri? = null,
    val multipleMediaUris: List<Uri> = emptyList(),
    val mimeType: String? = null
) {
    val allMediaUris: List<Uri>
        get() = if (singleMediaUri != null) listOf(singleMediaUri) + multipleMediaUris else multipleMediaUris

    val isLink: Boolean
        get() = text != null && (text.contains("http://") || text.contains("https://"))
}

/**
 * Boîte de dialogue unifiée de partage externe :
 * Permet d'envoyer dans des discussions (sélection multi-amis)
 * ET/OU de publier sur le mur souverain avec gestion stricte de la confidentialité (cercles + exclusions).
 */
@Composable
fun ExternalShareTargetDialog(
    payload: ExternalSharePayload,
    onDismiss: () -> Unit,
    onNavigateToConversation: (convId: String, phone: String, displayName: String) -> Unit,
    onOpenCreatePost: (initialText: String, mediaUris: List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val convRepo = remember(context) { ConversationRepository(context) }
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()

    var noteText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // ── État du partage sur le mur ──
    var shareToWall by remember { mutableStateOf(false) }
    var selectedCircleId by remember { mutableStateOf<String?>(null) } // null = Mon Mur (Tous les amis)
    var excludeFamily by remember(selectedCircleId) { mutableStateOf(selectedCircleId == "circle_work") }
    val excludedPhones = remember { mutableStateListOf<String>() }
    var showExcludePicker by remember { mutableStateOf(false) }

    // ── État de la sélection d'amis pour les discussions ──
    val contacts = remember { convRepo.loadContacts().filter { it.phone.isNotBlank() } }
    val selectedPhones = remember { mutableStateListOf<String>() }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery, ignoreCase = true)
        }
    }

    if (showExcludePicker) {
        PostExclusionPickerDialog(
            initialExcludedPhones = excludedPhones.toList(),
            onDismiss = { showExcludePicker = false },
            onConfirmed = { selected ->
                excludedPhones.clear()
                excludedPhones.addAll(selected)
            }
        )
    }

    Dialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. En-tête
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.external_share_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.external_share_subtitle),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                        enabled = !isSending
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                // 2. Zone défilante : Aperçu, Note, Mur et Sélecteur d'amis pliable
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Aperçu du contenu externe partagé (lien TikTok, Instagram, FB, ou média)
                    val sharePreviewUrl = remember(payload.text) { payload.text?.let { LinkPreviewHelper.extractFirstUrl(it) } }
                    if (!sharePreviewUrl.isNullOrBlank()) {
                        SocialLinkPreviewCard(url = sharePreviewUrl, isMsgMine = false)
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (payload.isLink) Icons.Default.Link else Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    if (!payload.text.isNullOrBlank()) {
                                        Text(
                                            text = payload.text.take(300),
                                            fontSize = 12.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    if (payload.allMediaUris.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.external_share_media_preview, payload.allMediaUris.size),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Champ optionnel pour ajouter une note d'accompagnement
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.external_share_add_note_hint), fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        enabled = !isSending
                    )

                    // Case à cocher "Partager sur mon mur" + Règles de confidentialité
                    ExternalShareWallCard(
                        shareToWall = shareToWall,
                        onShareToWallChange = { shareToWall = it },
                        selectedCircleId = selectedCircleId,
                        onSelectedCircleChange = { selectedCircleId = it },
                        excludeFamily = excludeFamily,
                        onExcludeFamilyChange = { excludeFamily = it },
                        excludedPhonesCount = excludedPhones.size,
                        onOpenExcludePicker = { showExcludePicker = true },
                        onOpenAdvancedOptions = {
                            val contentText = (payload.text ?: "").take(4096)
                            val note = noteText.trim().take(1000)
                            val fullContent = if (note.isNotBlank()) {
                                "$note\n\n$contentText".trim()
                            } else {
                                contentText
                            }
                            onOpenCreatePost(fullContent, payload.allMediaUris)
                            onDismiss()
                        },
                        enabled = !isSending
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Sélecteur d'amis modulaire emballé/déballé avec flèche flottante en survol
                    CollapsibleFriendsSelector(
                        contacts = contacts,
                        selectedPhones = selectedPhones.toSet(),
                        onToggleSelect = { phone ->
                            if (selectedPhones.contains(phone)) selectedPhones.remove(phone)
                            else selectedPhones.add(phone)
                        },
                        onSelectAll = {
                            selectedPhones.clear()
                            selectedPhones.addAll(contacts.map { it.phone })
                        },
                        onClearAll = { selectedPhones.clear() },
                        initiallyExpanded = false,
                        enabled = !isSending,
                        maxListHeightDp = 240
                    )
                }

                // 3. Bouton d'action principal Partager fixe en bas (toujours accessible)
                val canShare = !isSending && (shareToWall || selectedPhones.isNotEmpty())
                val buttonText = when {
                    shareToWall && selectedPhones.isNotEmpty() ->
                        stringResource(R.string.external_share_action_share_both, selectedPhones.size)
                    shareToWall ->
                        stringResource(R.string.external_share_publish_btn)
                    selectedPhones.isNotEmpty() ->
                        stringResource(R.string.external_share_action_send_friends, selectedPhones.size)
                    else ->
                        stringResource(R.string.external_share_action_share)
                }

                    Button(
                        onClick = {
                            if (!canShare) return@Button
                            isSending = true

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val contentText = (payload.text ?: "").take(4096)
                                    val note = noteText.trim().take(1000)
                                    val fullContent = if (note.isNotBlank()) {
                                        "$note\n\n$contentText".trim()
                                    } else {
                                        contentText
                                    }

                                    val nostrSync = NostrSyncManager.getInstance(context)

                                    // ── A. Publication sur le mur si cochée ──
                                    if (shareToWall) {
                                        var postMediaType: String? = null
                                        var postMediaPath: String? = null
                                        var postMediaData: String? = null

                                        val firstUri = payload.allMediaUris.firstOrNull()
                                        if (firstUri != null) {
                                            try {
                                                val (file, b64) = MediaAttachmentHelper.processImageUri(context, firstUri)
                                                if (file != null) {
                                                    postMediaPath = file.absolutePath
                                                    postMediaData = b64
                                                    postMediaType = "image"
                                                }
                                            } catch (e: Exception) {
                                                Log.w("ExternalShare", "Error processing media: ${e.message}")
                                            }
                                        }

                                        val normalizedAuthorPhone = ContactsPickerHelper.normalizePhoneNumber(sessionManager.userPhone)
                                        val role = if (AdminSecurityHelper.isAdmin(sessionManager.userPhone)) {
                                            UserSocialRole.FOUNDER_DEV
                                        } else {
                                            UserSocialRole.STANDARD
                                        }

                                        val newPost = SocialPost(
                                            id = "post_${UUID.randomUUID().toString().take(8)}",
                                            authorPhone = normalizedAuthorPhone,
                                            authorName = sessionManager.userName.ifBlank { "Moi" },
                                            authorAvatarPath = sessionManager.userAvatarPath,
                                            content = fullContent,
                                            hashtags = emptyList(),
                                            timestamp = System.currentTimeMillis(),
                                            targetCircleId = selectedCircleId,
                                            excludedCircleIds = if (excludeFamily && selectedCircleId != "circle_family") listOf("circle_family") else emptyList(),
                                            excludedPhones = if (selectedCircleId == "circle_family") emptyList() else excludedPhones.toList(),
                                            rsaSignature = "sig_rsa_valid",
                                            poll = null,
                                            reactions = emptyList(),
                                            comments = emptyList(),
                                            isPinned = false,
                                            isOfficialAnnouncement = false,
                                            authorRole = role,
                                            mediaType = postMediaType,
                                            mediaPath = postMediaPath,
                                            mediaData = postMediaData,
                                            mediaUrl = null
                                        )

                                        val socialRepo = SocialRepository(context)
                                        socialRepo.addPost(newPost)
                                        try {
                                            nostrSync.publishPost(newPost)
                                        } catch (e: Exception) {
                                            Log.w("ExternalShare", "Erreur diffusion Nostr Mur: ${e.message}")
                                        }
                                    }

                                    // ── B. Envoi dans les discussions sélectionnées ──
                                    if (selectedPhones.isNotEmpty()) {
                                        val friendRequestRepo = FriendRequestRepository(context)
                                        val myAvatarThumb = AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)

                                        val chatMessageText = when {
                                            payload.allMediaUris.size == 1 -> {
                                                val (imgFile, imgB64) = MediaAttachmentHelper.processImageUri(context, payload.allMediaUris[0])
                                                if (imgB64 != null) {
                                                    MediaAttachmentHelper.buildImagePayload(
                                                        id = imgFile?.nameWithoutExtension ?: "img_${System.currentTimeMillis()}",
                                                        base64 = imgB64,
                                                        caption = fullContent
                                                    )
                                                } else {
                                                    fullContent
                                                }
                                            }
                                            payload.allMediaUris.size > 1 -> {
                                                val processedB64s = mutableListOf<String>()
                                                for (uri in payload.allMediaUris) {
                                                    val (_, b64) = MediaAttachmentHelper.processImageUri(context, uri)
                                                    if (b64 != null) processedB64s.add(b64)
                                                }
                                                if (processedB64s.isNotEmpty()) {
                                                    MediaAttachmentHelper.buildAlbumPayload(
                                                        id = "album_${System.currentTimeMillis()}",
                                                        images = processedB64s,
                                                        caption = fullContent
                                                    )
                                                } else {
                                                    fullContent
                                                }
                                            }
                                            else -> fullContent
                                        }

                                        for (phone in selectedPhones) {
                                            val contact = contacts.find { it.phone == phone } ?: continue
                                            val targetConv = convRepo.ensureConversationForContact(
                                                phone = contact.phone,
                                                name = contact.name,
                                                publicKey = contact.publicKey
                                            )

                                            val msgId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
                                            val newMessage = Message(
                                                id = msgId,
                                                conversationId = targetConv.id,
                                                senderId = "me",
                                                text = chatMessageText,
                                                timestamp = System.currentTimeMillis(),
                                                encrypted = true,
                                                status = MessageDeliveryStatus.SENT
                                            )
                                            convRepo.addMessage(targetConv.id, newMessage)

                                            val refreshIntent = android.content.Intent(com.sha.orbis.notification.OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                                                putExtra(com.sha.orbis.notification.OrbisEventBus.EXTRA_CONV_ID, targetConv.id)
                                                setPackage(context.packageName)
                                            }
                                            context.sendBroadcast(refreshIntent)

                                            val recipientKey = contact.publicKey.takeIf { FriendRequestRepository.isValidNostrKey(it) }
                                                ?: friendRequestRepo.resolveNostrPubkeyForPhone(contact.phone)

                                            if (!recipientKey.isNullOrBlank()) {
                                                try {
                                                    nostrSync.sendDirectMessage(
                                                        recipientNpubOrHex = recipientKey,
                                                        conversationId = targetConv.id,
                                                        text = chatMessageText,
                                                        senderPhone = sessionManager.userPhone,
                                                        senderName = sessionManager.userName,
                                                        senderAvatarBase64 = myAvatarThumb
                                                    )
                                                    com.sha.orbis.telemetry.MessagingTelemetryTracker.trackMessagePayload(context, chatMessageText)
                                                } catch (e: Throwable) {
                                                    Log.e("ExternalShare", "Nostr send DM error: ${e.message}")
                                                }
                                            }
                                        }
                                    }

                                    withContext(Dispatchers.Main) {
                                        val toastMsg = when {
                                            shareToWall && selectedPhones.isNotEmpty() ->
                                                context.getString(R.string.external_share_success_both)
                                            shareToWall ->
                                                context.getString(R.string.external_share_publish_success)
                                            else ->
                                                context.getString(R.string.external_share_success_friends, selectedPhones.size)
                                        }
                                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()

                                        if (!shareToWall && selectedPhones.size == 1) {
                                            val singlePhone = selectedPhones.first()
                                            val singleContact = contacts.find { it.phone == singlePhone }
                                            if (singleContact != null) {
                                                val targetConv = convRepo.ensureConversationForContact(
                                                    singleContact.phone,
                                                    singleContact.name,
                                                    singleContact.publicKey
                                                )
                                                onNavigateToConversation(targetConv.id, singleContact.phone, singleContact.name)
                                            }
                                        }
                                        onDismiss()
                                    }
                                } catch (e: Throwable) {
                                    withContext(Dispatchers.Main) {
                                        isSending = false
                                        Toast.makeText(context, "Erreur : ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = canShare
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (shareToWall) Icons.Default.DynamicFeed else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = buttonText,
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

/**
 * Composant modulaire pour l'option "Partager sur mon mur" avec configuration souveraine de l'audience et des exclusions.
 */
@Composable
fun ExternalShareWallCard(
    shareToWall: Boolean,
    onShareToWallChange: (Boolean) -> Unit,
    selectedCircleId: String?,
    onSelectedCircleChange: (String?) -> Unit,
    excludeFamily: Boolean,
    onExcludeFamilyChange: (Boolean) -> Unit,
    excludedPhonesCount: Int,
    onOpenExcludePicker: () -> Unit,
    onOpenAdvancedOptions: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onShareToWallChange(!shareToWall) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (shareToWall)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (shareToWall) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Ligne principale : Checkbox + Intitulé
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = shareToWall,
                        onCheckedChange = { onShareToWallChange(it) },
                        enabled = enabled,
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Icon(
                        imageVector = Icons.Default.DynamicFeed,
                        contentDescription = null,
                        tint = if (shareToWall) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.external_share_wall_checkbox),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.external_share_wall_audience_title),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Tag feed
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (shareToWall) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Feed",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (shareToWall) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Options détaillées de diffusion & confidentialité souveraine (visibles si case cochée)
            AnimatedVisibility(
                visible = shareToWall,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sélecteur d'audience (Cercles)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            null to stringResource(R.string.external_share_circle_public),
                            "circle_family" to "👨‍👩‍👧‍👦 " + stringResource(R.string.social_circle_family),
                            "circle_close" to "⭐ " + stringResource(R.string.social_circle_close),
                            "circle_work" to "💼 " + stringResource(R.string.social_circle_work)
                        ).forEach { (id, label) ->
                            val isCircleSelected = selectedCircleId == id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isCircleSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .border(
                                        1.dp,
                                        if (isCircleSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(enabled = enabled) { onSelectedCircleChange(id) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isCircleSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCircleSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Règles d'exclusion (Famille & contacts spécifiques)
                    if (selectedCircleId != "circle_family") {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Toggle exclusion famille
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = enabled) { onExcludeFamilyChange(!excludeFamily) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = if (excludeFamily) Color(0xFF10B981) else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.social_post_exclude_family_title),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Switch(
                                        checked = excludeFamily,
                                        onCheckedChange = { onExcludeFamilyChange(it) },
                                        enabled = enabled,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                // Bouton d'exclusion de contacts ciblés
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(
                                        onClick = onOpenExcludePicker,
                                        enabled = enabled,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (excludedPhonesCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (excludedPhonesCount == 0)
                                                stringResource(R.string.social_post_exclude_contacts_action)
                                            else
                                                stringResource(R.string.social_post_excluded_contacts_count, excludedPhonesCount),
                                            fontSize = 11.sp,
                                            fontWeight = if (excludedPhonesCount > 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (excludedPhonesCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Lien vers éditeur complet si l'utilisateur souhaite des sondages etc.
                                    TextButton(
                                        onClick = onOpenAdvancedOptions,
                                        enabled = enabled,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = stringResource(R.string.external_share_advanced_options),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (excludeFamily || excludedPhonesCount > 0) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF10B981).copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color(0xFF059669),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.social_post_exclusion_summary),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF059669)
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

/**
 * Ligne de contact avec case à cocher pour la sélection multiple d'amis.
 */
@Composable
private fun ExternalShareContactItem(
    contact: Contact,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .clickable(enabled = enabled) { onToggleSelect() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                enabled = enabled,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            OrbisAvatar(
                avatarPath = contact.avatarPath,
                name = contact.name,
                size = 34.dp
            )
            Column {
                Text(
                    text = contact.name,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.phone,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
