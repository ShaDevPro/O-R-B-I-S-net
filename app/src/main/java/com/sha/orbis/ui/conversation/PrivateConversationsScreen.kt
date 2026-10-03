package com.sha.orbis.ui.conversation

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.model.Conversation
import com.sha.orbis.storage.PrivateConversationRepository
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.components.OrbisTopHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrivateConversationsScreen(
    currentAccountId: String?,
    conversations: List<Conversation>,
    onBack: () -> Unit,
    onOpenConversation: (Conversation) -> Unit
) {
    val context = LocalContext.current
    val privateRepo = remember(context, currentAccountId) {
        PrivateConversationRepository(context, currentAccountId)
    }
    var refreshKey by remember { mutableIntStateOf(0) }
    val lockedConversations = remember(conversations, refreshKey) {
        conversations.filter { privateRepo.isLocked(it.id) }
    }
    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }
    var actionConversation by remember { mutableStateOf<Conversation?>(null) }
    var conversationToRemoveLock by remember { mutableStateOf<Conversation?>(null) }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var removeLockError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        OrbisTopHeader(
            title = stringResource(R.string.private_chat_title),
            subtitle = stringResource(R.string.private_chat_subtitle),
            onBack = onBack
        )

        if (lockedConversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.private_chat_empty_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.private_chat_empty_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(lockedConversations, key = { it.id }) { conversation ->
                    PrivateConversationRow(
                        conversation = conversation,
                        onClick = {
                            unlockError = null
                            selectedConversation = conversation
                        },
                        onLongClick = {
                            actionConversation = conversation
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 74.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    val target = selectedConversation
    if (target != null) {
        PrivateChatUnlockDialog(
            conversationTitle = target.title,
            errorMessage = unlockError,
            onDismiss = {
                selectedConversation = null
                unlockError = null
            },
            onUnlock = { password ->
                if (privateRepo.verifyPassword(target.id, password)) {
                    selectedConversation = null
                    unlockError = null
                    onOpenConversation(target)
                    refreshKey++
                } else {
                    unlockError = context.getString(R.string.private_chat_error_wrong_pass)
                }
            }
        )
    }

    val actionTarget = actionConversation
    if (actionTarget != null) {
        AlertDialog(
            onDismissRequest = { actionConversation = null },
            title = { Text(stringResource(R.string.private_chat_actions_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.private_chat_actions_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    TextButton(
                        onClick = {
                            actionConversation = null
                            selectedConversation = actionTarget
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.private_chat_unlock_action), fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            actionConversation = null
                            removeLockError = null
                            conversationToRemoveLock = actionTarget
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.private_chat_remove_lock_action),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionConversation = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val removeTarget = conversationToRemoveLock
    if (removeTarget != null) {
        PrivateChatUnlockDialog(
            conversationTitle = removeTarget.title,
            errorMessage = removeLockError,
            onDismiss = {
                conversationToRemoveLock = null
                removeLockError = null
            },
            onUnlock = { password ->
                if (privateRepo.verifyPassword(removeTarget.id, password)) {
                    privateRepo.unlockConversation(removeTarget.id)
                    refreshKey++
                    conversationToRemoveLock = null
                    removeLockError = null
                            Toast.makeText(
                                context,
                                context.getString(R.string.private_chat_toast_unlocked, removeTarget.title),
                                Toast.LENGTH_SHORT
                            ).show()
                } else {
                    removeLockError = context.getString(R.string.private_chat_error_wrong_pass)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrivateConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val timeFormatted = remember(conversation.updatedAt) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(conversation.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OrbisAvatar(
                avatarPath = null,
                name = conversation.title,
                size = 48.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = timeFormatted,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.private_chat_locked_preview),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
