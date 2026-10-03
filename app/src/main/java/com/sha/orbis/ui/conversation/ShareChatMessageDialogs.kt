package com.sha.orbis.ui.conversation

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.sha.orbis.media.MediaAttachmentHelper
import com.sha.orbis.model.Message
import com.sha.orbis.model.MessageDeliveryStatus
import com.sha.orbis.nostr.service.NostrSyncManager
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.storage.SocialRepository
import com.sha.orbis.ui.share.CollapsibleFriendsSelector
import com.sha.orbis.ui.share.ExternalShareWallCard
import com.sha.orbis.ui.social.PostExclusionPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Aperçu compact et élégant du message à partager (texte ou média).
 */
@Composable
fun ChatMessagePreviewCard(
    message: Message,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                MediaAttachmentHelper.isImagePayload(message.text) -> {
                    val img = remember(message.text) { MediaAttachmentHelper.parseImagePayload(message.text) }
                    val bitmap = remember(img) {
                        try {
                            img?.base64Data?.let {
                                val bytes = Base64.decode(it, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                        } catch (_: Throwable) { null }
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.chat_share_media_preview),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val caption = img?.caption
                        if (!caption.isNullOrBlank()) {
                            Text(
                                text = caption,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                MediaAttachmentHelper.isDocPayload(message.text) -> {
                    val doc = remember(message.text) { MediaAttachmentHelper.parseDocPayload(message.text) }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc?.fileName ?: "Document",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!doc?.caption.isNullOrBlank()) {
                            Text(
                                text = doc!!.caption,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = message.text,
                        fontSize = 12.5.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Boîte de dialogue pour partager un message de chat avec des amis (Forward multi-contacts).
 * Intègre le sélecteur d'amis pliable (CollapsibleFriendsSelector) avec flèche flottante.
 */
@Composable
fun ShareChatMessageToFriendsDialog(
    message: Message,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val convRepo = remember(context) { ConversationRepository(context) }
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()

    val contacts = remember { convRepo.loadContacts().filter { it.phone.isNotBlank() } }
    val selectedPhones = remember { mutableStateListOf<String>() }
    var noteText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                                text = stringResource(R.string.chat_share_to_friends_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.chat_share_to_friends_subtitle),
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

                // 2. Aperçu du message
                ChatMessagePreviewCard(message = message)

                // 3. Note optionnelle accompagnante
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

                // 4. Sélecteur modulaire pliable d'amis avec flèche flottante en survol
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
                    initiallyExpanded = true,
                    enabled = !isSending,
                    maxListHeightDp = 240
                )

                // 5. Bouton d'action principal fixe au bas du dialogue
                Button(
                    onClick = {
                        if (selectedPhones.isEmpty() || isSending) return@Button
                        isSending = true

                        scope.launch(Dispatchers.IO) {
                            try {
                                val nostrSync = NostrSyncManager.getInstance(context)
                                val friendRequestRepo = FriendRequestRepository(context)

                                val contentToSend = if (noteText.isNotBlank()) {
                                    if (!MediaAttachmentHelper.isImagePayload(message.text) && !MediaAttachmentHelper.isDocPayload(message.text)) {
                                        "${noteText.trim()}\n\n${message.text}"
                                    } else {
                                        message.text
                                    }
                                } else {
                                    message.text
                                }

                                for (phone in selectedPhones) {
                                    val contact = contacts.find { it.phone == phone } ?: continue
                                    val targetConv = convRepo.ensureConversationForContact(
                                        phone = contact.phone,
                                        name = contact.name,
                                        publicKey = contact.publicKey
                                    )

                                    // Si note séparée pour média
                                    if (noteText.isNotBlank() && (MediaAttachmentHelper.isImagePayload(message.text) || MediaAttachmentHelper.isDocPayload(message.text))) {
                                        val noteMsg = Message(
                                            id = "msg_${System.currentTimeMillis()}_note",
                                            conversationId = targetConv.id,
                                            senderId = "me",
                                            text = noteText.trim(),
                                            timestamp = System.currentTimeMillis() - 100,
                                            encrypted = true,
                                            status = MessageDeliveryStatus.SENT
                                        )
                                        convRepo.addMessage(targetConv.id, noteMsg)
                                    }

                                    val forwardMsgId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
                                    val forwardMessage = Message(
                                        id = forwardMsgId,
                                        conversationId = targetConv.id,
                                        senderId = "me",
                                        text = contentToSend,
                                        timestamp = System.currentTimeMillis(),
                                        encrypted = true,
                                        status = MessageDeliveryStatus.SENT
                                    )
                                    convRepo.addMessage(targetConv.id, forwardMessage)

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
                                                text = contentToSend
                                            )
                                        } catch (e: Exception) {
                                            Log.w("ShareChatMessage", "Erreur Nostr vers ${contact.name}: ${e.message}")
                                        }
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.chat_share_success_friends, selectedPhones.size),
                                        Toast.LENGTH_SHORT
                                    ).show()
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
                    enabled = !isSending && selectedPhones.isNotEmpty()
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                text = if (selectedPhones.isNotEmpty())
                                    stringResource(R.string.external_share_action_send_friends, selectedPhones.size)
                                else
                                    stringResource(R.string.external_share_send_btn),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Boîte de dialogue pour publier un message de chat sur le mur (Timeline souveraine).
 */
@Composable
fun ShareChatMessageToWallDialog(
    message: Message,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()

    var commentaryText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    var shareToWall by remember { mutableStateOf(true) }
    var selectedCircleId by remember { mutableStateOf<String?>(null) }
    var excludeFamily by remember(selectedCircleId) { mutableStateOf(selectedCircleId == "circle_work") }
    val excludedPhones = remember { mutableStateListOf<String>() }
    var showExcludePicker by remember { mutableStateOf(false) }

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
                .padding(horizontal = 14.dp, vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                                imageVector = Icons.Default.DynamicFeed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.chat_share_to_wall_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.chat_share_to_wall_subtitle),
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

                // 2. Aperçu du message
                ChatMessagePreviewCard(message = message)

                // 3. Commentaire optionnel
                OutlinedTextField(
                    value = commentaryText,
                    onValueChange = { commentaryText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.external_share_add_note_hint), fontSize = 12.sp) },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    enabled = !isSending
                )

                // 4. Configuration souveraine du Mur (Cercles & Exclusions)
                ExternalShareWallCard(
                    shareToWall = shareToWall,
                    onShareToWallChange = { shareToWall = it },
                    selectedCircleId = selectedCircleId,
                    onSelectedCircleChange = { selectedCircleId = it },
                    excludeFamily = excludeFamily,
                    onExcludeFamilyChange = { excludeFamily = it },
                    excludedPhonesCount = excludedPhones.size,
                    onOpenExcludePicker = { showExcludePicker = true },
                    onOpenAdvancedOptions = { onDismiss() },
                    enabled = !isSending
                )

                // 5. Bouton d'action principal fixe au bas du dialogue
                Button(
                    onClick = {
                        if (!shareToWall || isSending) return@Button
                        isSending = true

                        scope.launch(Dispatchers.IO) {
                            try {
                                val nostrSync = NostrSyncManager.getInstance(context)

                                var postMediaType: String? = null
                                var postMediaData: String? = null

                                val postContent = when {
                                    MediaAttachmentHelper.isImagePayload(message.text) -> {
                                        val img = MediaAttachmentHelper.parseImagePayload(message.text)
                                        postMediaType = "image"
                                        postMediaData = img?.base64Data
                                        if (commentaryText.isNotBlank()) commentaryText.trim()
                                        else img?.caption ?: ""
                                    }
                                    MediaAttachmentHelper.isDocPayload(message.text) -> {
                                        val doc = MediaAttachmentHelper.parseDocPayload(message.text)
                                        postMediaType = "doc"
                                        postMediaData = doc?.base64Data
                                        if (commentaryText.isNotBlank()) "${commentaryText.trim()}\n[Document: ${doc?.fileName}]"
                                        else "[Document: ${doc?.fileName}]"
                                    }
                                    else -> {
                                        if (commentaryText.isNotBlank()) "${commentaryText.trim()}\n\n${message.text}"
                                        else message.text
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
                                    content = postContent,
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
                                    mediaPath = null,
                                    mediaData = postMediaData,
                                    mediaUrl = null
                                )

                                val socialRepo = SocialRepository(context)
                                socialRepo.addPost(newPost)
                                try {
                                    nostrSync.publishPost(newPost)
                                } catch (e: Exception) {
                                    Log.w("ShareChatMessage", "Erreur diffusion Nostr Mur: ${e.message}")
                                }

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.chat_share_success_wall),
                                        Toast.LENGTH_SHORT
                                    ).show()
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
                    enabled = !isSending && shareToWall
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.DynamicFeed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.external_share_publish_btn),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
