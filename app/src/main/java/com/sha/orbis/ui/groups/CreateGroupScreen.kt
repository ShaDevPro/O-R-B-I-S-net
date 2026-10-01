package com.sha.orbis.ui.groups

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sha.orbis.R
import com.sha.orbis.data.ContactsPickerHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Contact
import com.sha.orbis.model.Conversation
import com.sha.orbis.model.CountryCode
import com.sha.orbis.model.Message
import com.sha.orbis.model.MessageDeliveryStatus
import com.sha.orbis.security.GroupKeyManager
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendCircleRepository
import com.sha.orbis.storage.LocalMessageStore
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.components.OrbisTopHeader
import com.sha.orbis.ui.theme.OrbisColorPalette

enum class GroupCircleCategory(val id: String, @param:StringRes val labelRes: Int, val emoji: String) {
    FAMILY("circle_family", R.string.group_circle_family, "👨‍👩‍👧‍👦"),
    FRIENDS("circle_close", R.string.group_circle_friends, "⭐"),
    WORK("circle_work", R.string.group_circle_work, "💼"),
    COMMUNITY("circle_community", R.string.group_circle_community, "🌐"),
    CUSTOM("circle_custom", R.string.group_circle_custom, "🛡️")
}

@Composable
fun CreateGroupScreen(
    onGroupCreated: ((Conversation?) -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val convRepo = remember { ConversationRepository(context) }
    val circleRepo = remember { FriendCircleRepository(context) }
    val messageStore = remember { LocalMessageStore(context) }
    val groupKeyManager = remember { GroupKeyManager() }

    var groupName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(GroupCircleCategory.FAMILY) }
    var groupKey by remember { mutableStateOf(groupKeyManager.generateGroupKey()) }
    var searchQuery by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    // Selected contacts for the group
    val selectedContacts = remember { mutableStateListOf<Contact>() }

    // Available contacts pool
    var availableContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
    }

    fun loadAllContacts() {
        val dialCode = CountryCode.defaultCountry(context).dialCode
        val savedContacts = convRepo.loadContacts()
        val combined = savedContacts.toMutableList()

        if (hasContactsPermission) {
            val deviceContacts = ContactsPickerHelper.fetchDeviceContacts(context)
            deviceContacts.forEach { dc ->
                val normalized = ContactsPickerHelper.normalizePhoneNumber(dc.phoneNumber, dialCode)
                if (combined.none { it.phone == normalized }) {
                    combined.add(
                        Contact(
                            id = "c_${normalized.filter { it.isDigit() }}",
                            name = dc.name,
                            phone = normalized,
                            publicKey = "PUB_KEY_PENDING",
                            status = context.getString(R.string.contact_local_directory)
                        )
                    )
                }
            }
        }
        availableContacts = combined.distinctBy { it.phone }
    }

    LaunchedEffect(hasContactsPermission) {
        loadAllContacts()
    }

    val filteredContacts = remember(availableContacts, searchQuery) {
        if (searchQuery.isBlank()) {
            availableContacts
        } else {
            availableContacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery)
            }
        }
    }

    fun handleCreateGroup() {
        if (groupName.isBlank()) {
            Toast.makeText(context, context.getString(R.string.group_error_name_empty), Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedContacts.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.group_error_members_empty), Toast.LENGTH_SHORT).show()
            return
        }

        isCreating = true
        val userPhone = sessionManager.userPhone
        val allMemberPhones = (selectedContacts.map { it.phone } + userPhone).filter { it.isNotBlank() }.distinct()

        val groupId = "conv_group_${System.currentTimeMillis()}"
        val newGroupConv = Conversation(
            id = groupId,
            title = groupName.trim(),
            participants = allMemberPhones,
            lastMessage = context.getString(R.string.group_encrypted_created_status),
            updatedAt = System.currentTimeMillis(),
            isGroup = true
        )

        // 1. Save Conversation
        convRepo.addConversation(newGroupConv)

        // 2. Add members to Circle Repository if applicable
        if (selectedCategory != GroupCircleCategory.CUSTOM) {
            selectedContacts.forEach { member ->
                circleRepo.addMemberToCircle(selectedCategory.id, member.phone)
            }
        }

        // 3. Store initial system message
        val welcomeMsg = Message(
            id = "msg_${System.currentTimeMillis()}",
            conversationId = groupId,
            senderId = "me",
            text = context.getString(R.string.group_initial_system_message, groupName.trim(), allMemberPhones.size),
            timestamp = System.currentTimeMillis(),
            status = MessageDeliveryStatus.DELIVERED
        )
        messageStore.addMessage(groupId, welcomeMsg)

        // 4. Send group invitation via Nostr E2EE to each remote member
        val inviteText = context.getString(R.string.group_invite_sms_text, groupName.trim())
        val nostrSync = try { com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context) } catch (_: Exception) { null }
        selectedContacts.forEach { member ->
            val targetNpubOrHex = member.publicKey.takeIf { it.isNotBlank() && it != "PUB_KEY_PENDING" } ?: member.phone
            if (nostrSync != null && (targetNpubOrHex.startsWith("npub1") || targetNpubOrHex.length == 64)) {
                try {
                    nostrSync.sendDirectMessage(
                        recipientNpubOrHex = targetNpubOrHex,
                        conversationId = groupId,
                        text = inviteText
                    )
                } catch (_: Exception) {}
            }
        }

        isCreating = false
        try {
            com.sha.orbis.telemetry.FeedTelemetryTracker.trackGroupAction(context)
        } catch (_: Exception) {}
        Toast.makeText(
            context,
            context.getString(R.string.group_created_toast_format, groupName.trim(), selectedContacts.size),
            Toast.LENGTH_SHORT
        ).show()
        onGroupCreated?.invoke(newGroupConv)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        // Top Header
        OrbisTopHeader(
            title = stringResource(R.string.group_create),
            subtitle = if (selectedContacts.isNotEmpty()) {
                stringResource(R.string.group_members_subtitle, selectedContacts.size)
            } else {
                stringResource(R.string.group_aes_default_subtitle)
            },
            onBack = onBack ?: {
                onGroupCreated?.invoke(null)
                Unit
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))

                // Group Name Input
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    placeholder = { Text(stringResource(R.string.group_name_hint)) },
                    leadingIcon = {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            // Circle Category Selector Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.group_circle_section_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(GroupCircleCategory.values()) { cat ->
                            val isSelected = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(cat.emoji, fontSize = 14.sp)
                                    Text(
                                        text = stringResource(cat.labelRes),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Group Key & Encryption Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = stringResource(R.string.group_aes_key_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { groupKey = groupKeyManager.generateGroupKey() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.group_regenerate_key),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = groupKey,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Selected Members Carousel
            if (selectedContacts.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.group_selected_members_count, selectedContacts.size),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(selectedContacts, key = { it.phone }) { contact ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                        .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = contact.name.ifBlank { contact.phone },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        IconButton(
                                            onClick = { selectedContacts.remove(contact) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.group_remove_member),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Contact Picker Header & Search
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.group_add_contacts_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!hasContactsPermission) {
                        Text(
                            text = stringResource(R.string.group_grant_contacts_perm),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.group_search_contact_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Contact List
            if (filteredContacts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) {
                                stringResource(R.string.group_no_contacts_available)
                            } else {
                                stringResource(R.string.group_no_contacts_match)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredContacts, key = { it.id + it.phone }) { contact ->
                    val isSelected = selectedContacts.any { it.phone == contact.phone }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) {
                                    selectedContacts.removeAll { it.phone == contact.phone }
                                } else {
                                    selectedContacts.add(contact)
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
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
                                    size = 38.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = contact.phone,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Create Button
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { handleCreateGroup() },
                    enabled = !isCreating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (isCreating) {
                                stringResource(R.string.group_creating_progress)
                            } else {
                                stringResource(R.string.group_btn_create_with_count, selectedContacts.size)
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

