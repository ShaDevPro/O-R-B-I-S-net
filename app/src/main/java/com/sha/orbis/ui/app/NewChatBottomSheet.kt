package com.sha.orbis.ui.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.data.ContactsPickerHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Contact
import com.sha.orbis.model.Conversation
import com.sha.orbis.model.CountryCode
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.auth.CountryCodePickerDialog
import com.sha.orbis.ui.security.QrCodeDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatBottomSheet(
    onDismiss: () -> Unit,
    onStartDirectChat: (Conversation) -> Unit,
    onCreateGroupClick: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sessionManager = remember { SessionManager(context) }
    val repository = remember { ConversationRepository(context) }

    var isAddingContact by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf(CountryCode.defaultCountry(context)) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    var contactName by remember { mutableStateOf("") }
    var nationalPhone by remember { mutableStateOf("") }
    var contactAvatarPath by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var showInviteInstallSheet by remember { mutableStateOf(false) }

    // Native Android Contact Picker Launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            val picked = ContactsPickerHelper.extractContact(context, uri)
            if (picked != null) {
                contactName = picked.name
                val digits = picked.phoneNumber.filter { it.isDigit() }
                nationalPhone = digits
                contactAvatarPath = picked.avatarPath
                isAddingContact = true
            }
        }
    }

    if (showCountryPicker) {
        CountryCodePickerDialog(
            selectedCountry = selectedCountry,
            onCountrySelected = { selectedCountry = it },
            onDismissRequest = { showCountryPicker = false }
        )
    }

    if (showQrDialog) {
        QrCodeDialog(
            onDismiss = { showQrDialog = false },
            onContactVerified = { verifiedContact ->
                val contacts = repository.loadContacts().toMutableList()
                if (contacts.none { it.id == verifiedContact.id }) {
                    contacts.add(0, verifiedContact)
                    repository.saveContacts(contacts)
                }

                val conv = Conversation(
                    id = "conv_${verifiedContact.phone.filter { it.isDigit() }}",
                    title = verifiedContact.name,
                    participants = listOf("me", verifiedContact.phone),
                    lastMessage = "Contact certifié par QR Code.",
                    updatedAt = System.currentTimeMillis(),
                    unreadCount = 0
                )
                onDismiss()
                onStartDirectChat(conv)
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isAddingContact) stringResource(R.string.new_chat_invite_contact) else stringResource(R.string.new_chat),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!isAddingContact) {
                // Quick Actions (WhatsApp Style)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionRow(
                        icon = Icons.Default.Contacts,
                        title = stringResource(R.string.new_chat_action_contacts),
                        subtitle = stringResource(R.string.new_chat_action_contacts_sub),
                        onClick = {
                            contactPickerLauncher.launch(null)
                        }
                    )

                    QuickActionRow(
                        icon = Icons.Default.PersonAdd,
                        title = stringResource(R.string.new_chat_action_invite),
                        subtitle = stringResource(R.string.new_chat_action_invite_sub),
                        onClick = { isAddingContact = true }
                    )

                    QuickActionRow(
                        icon = Icons.Default.QrCodeScanner,
                        title = stringResource(R.string.new_chat_action_qr),
                        subtitle = stringResource(R.string.new_chat_action_qr_sub),
                        onClick = { showQrDialog = true }
                    )

                    QuickActionRow(
                        icon = Icons.Default.GroupAdd,
                        title = stringResource(R.string.new_chat_action_group),
                        subtitle = stringResource(R.string.new_chat_action_group_sub),
                        onClick = {
                            onDismiss()
                            onCreateGroupClick()
                        }
                    )
                }
            } else {
                // Add Contact Form
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.new_chat_form_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    OutlinedButton(
                        onClick = { contactPickerLauncher.launch(null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.new_chat_btn_phonebook), fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text(stringResource(R.string.new_chat_contact_name_label)) },
                        placeholder = { Text(stringResource(R.string.new_chat_contact_name_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable { showCountryPicker = true }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(selectedCountry.flagEmoji, fontSize = 16.sp)
                                Text(selectedCountry.dialCode, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }

                        OutlinedTextField(
                            value = nationalPhone,
                            onValueChange = { nationalPhone = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(R.string.new_chat_phone_label)) },
                            placeholder = { Text("550123456") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val cleanName = contactName.trim().ifBlank { "Contact" }
                            val cleanNational = nationalPhone.filter { it.isDigit() }.trimStart('0')
                            if (cleanNational.length < 7) {
                                Toast.makeText(context, context.getString(R.string.auth_error_phone), Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val fullPhone = "${selectedCountry.dialCode}$cleanNational"
                            val identity = sessionManager.getOrCreateIdentity()
                            val dynamicKey = com.sha.orbis.security.AesCipher.generateKeyBase64()

                            isSending = true
                            try {
                                val myAvatarThumb = com.sha.orbis.ui.components.AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, 96)

                                // 1. Direct Internet Delivery: Publish Nostr Sovereign Friend Invitation
                                try {
                                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                    nostrSync.publishFriendInvitation(
                                        recipientPhone = fullPhone,
                                        groupKey = dynamicKey,
                                        avatarBase64 = myAvatarThumb
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.w("NewChatBottomSheet", "Erreur émission invite Nostr: ${e.message}")
                                }

                                val friendRepo = com.sha.orbis.storage.FriendRequestRepository(context)
                                friendRepo.addOrUpdate(
                                    com.sha.orbis.model.FriendRequest(
                                        id = "sent_${cleanNational}",
                                        senderPhone = fullPhone,
                                        senderName = cleanName,
                                        senderAvatarPath = contactAvatarPath,
                                        senderPublicKey = "",
                                        direction = com.sha.orbis.model.RequestDirection.SENT,
                                        groupKey = dynamicKey
                                    )
                                )

                                val contacts = repository.loadContacts().toMutableList()
                                val existingIdx = contacts.indexOfFirst { FriendRequestRepository.isSamePhone(it.phone, fullPhone) }
                                if (existingIdx >= 0) {
                                    contacts[existingIdx] = contacts[existingIdx].copy(status = "En attente ⏳")
                                } else {
                                    contacts.add(0, Contact(id = "c_${cleanNational}", name = cleanName, phone = fullPhone, publicKey = "PUB_KEY_PENDING", status = "En attente ⏳", avatarPath = contactAvatarPath))
                                }
                                repository.saveContacts(contacts)

                                Toast.makeText(context, context.getString(R.string.new_chat_invite_success), Toast.LENGTH_LONG).show()
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erreur : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            } finally {
                                isSending = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isSending && nationalPhone.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(if (isSending) stringResource(R.string.new_chat_sending_invite) else stringResource(R.string.new_chat_btn_send_invite), fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            showInviteInstallSheet = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isSending
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.friends_btn_invite_install), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showInviteInstallSheet) {
        val cleanNational = nationalPhone.filter { it.isDigit() }.trimStart('0')
        val fullPhone = if (cleanNational.isNotBlank()) "${selectedCountry.dialCode}$cleanNational" else null
        val cleanName = contactName.trim().ifBlank { null }
        com.sha.orbis.ui.components.InviteToInstallBottomSheet(
            targetName = cleanName,
            targetPhone = fullPhone,
            onDismiss = { showInviteInstallSheet = false }
        )
    }
}

@Composable
private fun QuickActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
