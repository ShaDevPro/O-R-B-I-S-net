package com.sha.orbis.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sha.orbis.R
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.social.feed.popups.FeedCenteredPopup
import com.sha.orbis.ui.social.feed.popups.FeedModalTopBar

/**
 * Clean, reassuring Contact Exclusion Picker Dialog.
 * Enables users to exclude specific contacts from viewing a post.
 */
@Composable
fun PostExclusionPickerDialog(
    initialExcludedPhones: List<String>,
    onDismiss: () -> Unit,
    onConfirmed: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val convRepo = remember { ConversationRepository(context) }
    val allContacts = remember { convRepo.loadContacts() }

    val excludedList = remember {
        mutableStateListOf<String>().apply {
            addAll(initialExcludedPhones)
        }
    }
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(allContacts, searchQuery) {
        if (searchQuery.isBlank()) allContacts else {
            allContacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery)
            }
        }
    }

    FeedCenteredPopup(onDismiss = onDismiss) {
        FeedModalTopBar(
            title = stringResource(R.string.social_post_exclusion_dialog_title),
            subtitle = stringResource(R.string.social_post_exclusion_dialog_desc),
            onClose = onDismiss
        )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {

                Spacer(modifier = Modifier.height(4.dp))

                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.common_search), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Contact List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredContacts, key = { it.phone }) { contact ->
                        val isExcluded = excludedList.any { FriendRequestRepository.isSamePhone(it, contact.phone) }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isExcluded) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isExcluded) {
                                        excludedList.removeAll { FriendRequestRepository.isSamePhone(it, contact.phone) }
                                    } else {
                                        excludedList.add(contact.phone)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                        name = contact.name.ifBlank { contact.phone },
                                        size = 36.dp
                                    )
                                    Column {
                                        Text(
                                            text = contact.name.ifBlank { contact.phone },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = contact.phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Checkbox(
                                    checked = isExcluded,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (!isExcluded) excludedList.add(contact.phone)
                                        } else {
                                            excludedList.removeAll { FriendRequestRepository.isSamePhone(it, contact.phone) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Confirm Button
                Button(
                    onClick = {
                        onConfirmed(excludedList.toList())
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (excludedList.isEmpty()) stringResource(R.string.common_save)
                        else "${stringResource(R.string.common_save)} (${excludedList.size})",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
    }
}
