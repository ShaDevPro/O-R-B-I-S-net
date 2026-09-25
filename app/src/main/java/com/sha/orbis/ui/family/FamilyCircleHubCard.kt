package com.sha.orbis.ui.family

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sha.orbis.R
import com.sha.orbis.model.Contact
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendCircleRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.components.OrbisAvatar

/**
 * Prominent, friendly Family Circle Hub Card.
 * Designed specifically for non-technical users to access their family circle,
 * share private souvenirs, start family chat, or trigger emergency alerts in 1 tap.
 */
@Composable
fun FamilyCircleHubCard(
    modifier: Modifier = Modifier,
    onOpenFamilyChat: () -> Unit,
    onOpenDirectChat: ((phone: String, name: String) -> Unit)? = null,
    onShareFamilyMemory: () -> Unit,
    onSendFamilyEmergency: () -> Unit,
    onFamilyCall: (phone: String, name: String, isVideo: Boolean) -> Unit
) {
    val context = LocalContext.current
    val circleRepo = remember { FriendCircleRepository(context) }
    val convRepo = remember { ConversationRepository(context) }

    var familyMembers by remember { mutableStateOf(circleRepo.getFamilyMembers()) }
    var allContacts by remember { mutableStateOf(convRepo.loadContacts()) }
    var showManagerDialog by remember { mutableStateOf(false) }
    var showCallChooserDialog by remember { mutableStateOf(false) }
    var showChatChooserDialog by remember { mutableStateOf(false) }
    var selectedMemberForAction by remember { mutableStateOf<Contact?>(null) }

    val familyContacts = remember(familyMembers, allContacts) {
        familyMembers.map { phone ->
            allContacts.find { FriendRequestRepository.isSamePhone(it.phone, phone) }
                ?: Contact(name = phone, phone = phone)
        }
    }

    fun recordFamilyInteraction() {
        try {
            com.sha.orbis.telemetry.TelemetryManager.getInstance(context).recordEvent(com.sha.orbis.telemetry.FeatureType.FAMILY_CIRCLE)
        } catch (_: Exception) {}
    }

    if (showManagerDialog) {
        FamilyCircleManagerDialog(
            onDismiss = { showManagerDialog = false },
            onCircleUpdated = {
                familyMembers = circleRepo.getFamilyMembers()
                allContacts = convRepo.loadContacts()
            }
        )
    }

    // Modal 1: Member Action Sheet (Triggered when tapping a member's avatar in the row)
    if (selectedMemberForAction != null) {
        val member = selectedMemberForAction!!
        Dialog(onDismissRequest = { selectedMemberForAction = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.family_member_actions_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(
                            onClick = { selectedMemberForAction = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OrbisAvatar(
                        avatarPath = member.avatarPath,
                        name = member.name.ifBlank { member.phone },
                        size = 64.dp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = member.name.ifBlank { member.phone },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = member.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 1. Direct Message Button
                    Button(
                        onClick = {
                            val phone = member.phone
                            val name = member.name.ifBlank { member.phone }
                            selectedMemberForAction = null
                            if (onOpenDirectChat != null) {
                                onOpenDirectChat(phone, name)
                            } else {
                                onOpenFamilyChat()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.family_action_send_message), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Voice Call Button
                    Button(
                        onClick = {
                            val phone = member.phone
                            val name = member.name.ifBlank { member.phone }
                            selectedMemberForAction = null
                            onFamilyCall(phone, name, false)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.family_action_voice_call), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Video Call Button
                    Button(
                        onClick = {
                            val phone = member.phone
                            val name = member.name.ifBlank { member.phone }
                            selectedMemberForAction = null
                            onFamilyCall(phone, name, true)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.family_action_video_call), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    // Modal 2: Family Chat Chooser (Triggered when tapping "Discussion")
    if (showChatChooserDialog) {
        Dialog(onDismissRequest = { showChatChooserDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.family_chat_picker_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = stringResource(R.string.family_chat_picker_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showChatChooserDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Option: Cocon Famille (Group chat)
                    Card(
                        onClick = {
                            showChatChooserDialog = false
                            onOpenFamilyChat()
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("👨‍👩‍👧‍👦", fontSize = 22.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.family_chat_group_option),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = stringResource(R.string.family_chat_group_desc, familyContacts.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (familyContacts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.family_chat_direct_section),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(familyContacts, key = { "chat_${it.phone}" }) { contact ->
                                Card(
                                    onClick = {
                                        showChatChooserDialog = false
                                        if (onOpenDirectChat != null) {
                                            onOpenDirectChat(contact.phone, contact.name.ifBlank { contact.phone })
                                        } else {
                                            onOpenFamilyChat()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OrbisAvatar(
                                            avatarPath = contact.avatarPath,
                                            name = contact.name.ifBlank { contact.phone },
                                            size = 40.dp
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
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
                                        Icon(
                                            imageVector = Icons.Default.ChatBubble,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(18.dp)
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

    // Modal 3: Family Direct Call Chooser (Triggered when tapping "Appel direct")
    if (showCallChooserDialog) {
        Dialog(onDismissRequest = { showCallChooserDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.family_call_picker_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = stringResource(R.string.family_call_picker_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showCallChooserDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(familyContacts, key = { "call_${it.phone}" }) { contact ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OrbisAvatar(
                                        avatarPath = contact.avatarPath,
                                        name = contact.name.ifBlank { contact.phone },
                                        size = 42.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.name.ifBlank { contact.phone },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = contact.phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Voice Call Button
                                    IconButton(
                                        onClick = {
                                            showCallChooserDialog = false
                                            onFamilyCall(contact.phone, contact.name.ifBlank { contact.phone }, false)
                                        },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = stringResource(R.string.family_action_voice_call),
                                            tint = Color(0xFF059669),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Video Call Button
                                    IconButton(
                                        onClick = {
                                            showCallChooserDialog = false
                                            onFamilyCall(contact.phone, contact.name.ifBlank { contact.phone }, true)
                                        },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF8B5CF6).copy(alpha = 0.15f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = stringResource(R.string.family_action_video_call),
                                            tint = Color(0xFF7C3AED),
                                            modifier = Modifier.size(22.dp)
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF10B981).copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👨‍👩‍👧‍👦", fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.family_hub_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = stringResource(R.string.family_hub_badge),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                TextButton(
                    onClick = { showManagerDialog = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.family_hub_manage),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (familyContacts.isEmpty()) {
                // Sleek compact banner for empty family circle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .clickable { showManagerDialog = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.family_hub_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.family_hub_add_members_btn),
                        color = Color(0xFF10B981),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Avatars Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    familyContacts.forEach { contact ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(56.dp)
                                .clickable {
                                    recordFamilyInteraction()
                                    selectedMemberForAction = contact
                                }
                        ) {
                            OrbisAvatar(
                                avatarPath = contact.avatarPath,
                                name = contact.name.ifBlank { contact.phone },
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = contact.name.ifBlank { contact.phone }.split(" ").firstOrNull() ?: "",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Plus Button to add more relatives
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(56.dp)
                            .clickable {
                                recordFamilyInteraction()
                                showManagerDialog = true
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Color(0xFF10B981), CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.common_add),
                            fontSize = 11.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4 One-Click Quick Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action 1: Discussion
                    FamilyActionButton(
                        icon = Icons.Default.ChatBubble,
                        label = stringResource(R.string.family_hub_action_chat),
                        containerColor = Color(0xFF3B82F6).copy(alpha = 0.12f),
                        iconColor = Color(0xFF2563EB),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            recordFamilyInteraction()
                            if (familyContacts.isEmpty()) {
                                showManagerDialog = true
                            } else {
                                showChatChooserDialog = true
                            }
                        }
                    )

                    // Action 2: Souvenir Privé
                    FamilyActionButton(
                        icon = Icons.Default.CameraAlt,
                        label = stringResource(R.string.family_hub_action_photo),
                        containerColor = Color(0xFF10B981).copy(alpha = 0.12f),
                        iconColor = Color(0xFF059669),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            recordFamilyInteraction()
                            onShareFamilyMemory()
                        }
                    )

                    // Action 3: SOS / Position
                    FamilyActionButton(
                        icon = Icons.Default.LocationOn,
                        label = stringResource(R.string.family_hub_action_sos),
                        containerColor = Color(0xFFEF4444).copy(alpha = 0.12f),
                        iconColor = Color(0xFFDC2626),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            recordFamilyInteraction()
                            onSendFamilyEmergency()
                        }
                    )

                    // Action 4: Appel Direct
                    FamilyActionButton(
                        icon = Icons.Default.Call,
                        label = stringResource(R.string.family_hub_action_call),
                        containerColor = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                        iconColor = Color(0xFF7C3AED),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            recordFamilyInteraction()
                            if (familyContacts.isEmpty()) {
                                showManagerDialog = true
                            } else {
                                showCallChooserDialog = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FamilyActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier.height(60.dp)
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = iconColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
