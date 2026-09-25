package com.sha.orbis.ui.social

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sha.orbis.R
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Conversation
import com.sha.orbis.model.Message
import com.sha.orbis.model.MessageDeliveryStatus
import com.sha.orbis.social.SharedPostPayload
import com.sha.orbis.social.SocialPost
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.theme.OrbisColorPalette
import com.sha.orbis.ui.social.feed.popups.FeedFullscreenPopup
import com.sha.orbis.ui.social.feed.popups.FeedModalTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SharePostToChatDialog(
    post: SocialPost,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val convRepo = remember(context) { ConversationRepository(context) }
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()

    val contacts = remember { convRepo.loadContacts().filter { it.phone.isNotBlank() } }
    var noteText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var sentPhones by remember { mutableStateOf(setOf<String>()) }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery, ignoreCase = true)
        }
    }

    FeedFullscreenPopup(onDismiss = onDismiss, heightFraction = 0.88f) {
        FeedModalTopBar(
            title = stringResource(R.string.social_share_to_chat_title),
            subtitle = stringResource(R.string.social_share_to_chat_subtitle),
            onClose = onDismiss
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                // Post Preview Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OrbisAvatar(
                            avatarPath = post.authorAvatarPath,
                            name = post.authorName,
                            size = 36.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = post.authorName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = post.content,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3. Optional Note TextField
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.social_share_note_hint), fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                // 4. Contact Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.social_share_search_hint), fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = if (searchQuery.isNotBlank()) {
                        {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 5. Contacts List
                if (filteredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.contacts_empty_title),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredContacts, key = { it.phone }) { contact ->
                            val isSent = sentPhones.contains(contact.phone)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OrbisAvatar(
                                        avatarPath = contact.avatarPath,
                                        name = contact.name,
                                        size = 36.dp
                                    )
                                    Column {
                                        Text(
                                            text = contact.name,
                                            fontWeight = FontWeight.Bold,
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

                                Button(
                                    onClick = {
                                        if (isSent) return@Button

                                        // Find or create conversation with this contact
                                        val existingConvs = convRepo.loadConversations().toMutableList()
                                        var targetConv = existingConvs.find { conv ->
                                            !conv.isGroup && conv.participants.any { FriendRequestRepository.isSamePhone(it, contact.phone) }
                                        }

                                        if (targetConv == null) {
                                            targetConv = Conversation(
                                                id = "conv_${UUID.randomUUID().toString().take(8)}",
                                                title = contact.name,
                                                participants = listOf(contact.phone),
                                                lastMessage = "",
                                                updatedAt = System.currentTimeMillis()
                                            )
                                            existingConvs.add(0, targetConv)
                                            convRepo.saveConversations(existingConvs)
                                        }

                                        // Build Shared Post Payload
                                        val payload = SharedPostPayload(
                                            postId = post.id,
                                            authorPhone = post.authorPhone,
                                            authorName = post.authorName,
                                            authorAvatarPath = post.authorAvatarPath,
                                            contentSnippet = post.content.take(220),
                                            hashtags = post.hashtags,
                                            note = noteText.ifBlank { null }
                                        )
                                        val messageText = payload.encode()

                                        val friendRequestRepo = com.sha.orbis.storage.FriendRequestRepository(context)
                                        val recipientKey = contact.publicKey.takeIf { com.sha.orbis.storage.FriendRequestRepository.isValidNostrKey(it) }
                                            ?: friendRequestRepo.resolveNostrPubkeyForPhone(contact.phone)

                                        try {
                                            val msgId = "msg_${System.currentTimeMillis()}"

                                            val newMessage = Message(
                                                id = msgId,
                                                conversationId = targetConv.id,
                                                senderId = "me",
                                                text = messageText,
                                                timestamp = System.currentTimeMillis(),
                                                encrypted = true,
                                                status = MessageDeliveryStatus.SENT
                                            )
                                            convRepo.addMessage(targetConv.id, newMessage)

                                            // Send via 100% sovereign Nostr DM
                                            if (!recipientKey.isNullOrBlank()) {
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                                        val myAvatarThumb = com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)
                                                        nostrSync.sendDirectMessage(
                                                            recipientNpubOrHex = recipientKey,
                                                            conversationId = targetConv.id,
                                                            text = messageText,
                                                            messageId = msgId,
                                                            senderAvatarBase64 = myAvatarThumb,
                                                            senderName = sessionManager.userName.ifBlank { "Moi" }
                                                        )

                                                    } catch (e: Exception) {
                                                        android.util.Log.w("SharePostToChatDialog", "Erreur envoi Nostr: ${e.message}")
                                                    }
                                                }
                                            }

                                            sentPhones = sentPhones + contact.phone
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.social_share_toast_success, contact.name),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Erreur lors de l'envoi : ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSent) OrbisColorPalette.StatusActive else MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSent) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (isSent) stringResource(R.string.social_share_sent_btn) else stringResource(R.string.social_share_send_btn),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
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
