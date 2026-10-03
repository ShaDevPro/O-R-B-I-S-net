package com.sha.orbis.ui.security

import android.graphics.Bitmap
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sha.orbis.R
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Contact
import com.sha.orbis.security.QrCodeHelper
import com.sha.orbis.ui.components.AvatarManager

@Composable
fun QrCodeDialog(
    onDismiss: () -> Unit,
    onContactVerified: (Contact) -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val sessionManager = remember { SessionManager(context) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var rawQrInput by remember { mutableStateOf("") }

    val myQrPayload = remember(sessionManager.userName, sessionManager.userPhone, sessionManager.publicKey, sessionManager.userAvatarPath) {
        val thumbBase64 = AvatarManager.getAvatarAsBase64Thumbnail(sessionManager.userAvatarPath, sizePx = 64)
        QrCodeHelper.QrContactPayload(
            name = sessionManager.userName.ifBlank { "Moi" },
            phone = sessionManager.userPhone,
            publicKey = sessionManager.publicKey,
            avatarBase64 = thumbBase64
        ).encodeToString()
    }

    val qrBitmap = remember(myQrPayload) {
        try {
            QrCodeHelper.generateQrBitmap(myQrPayload, 480)
        } catch (_: Exception) {
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.qr_dialog_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                // Tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text(stringResource(R.string.qr_tab_my_qr), fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text(stringResource(R.string.qr_tab_validate), fontWeight = FontWeight.Bold) }
                    )
                }

                if (selectedTabIndex == 0) {
                    // Display My QR Code
                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(androidx.compose.ui.graphics.Color.White)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.qr_tab_my_qr),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.qr_my_desc),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(myQrPayload))
                            Toast.makeText(context, context.getString(R.string.qr_toast_copied), Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.qr_btn_copy_payload), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                } else {
                    // Import / Validate Partner QR Code
                    Text(
                        text = stringResource(R.string.qr_validate_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = rawQrInput,
                        onValueChange = { rawQrInput = it },
                        placeholder = { Text(stringResource(R.string.qr_input_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Button(
                        onClick = {
                            val parsed = QrCodeHelper.QrContactPayload.decodeFromString(rawQrInput.trim())
                            if (parsed != null) {
                                val cleanDigits = parsed.phone.filter { it.isDigit() }
                                val localAvatarPath = if (!parsed.avatarBase64.isNullOrBlank()) {
                                    AvatarManager.saveAvatarFromBase64(
                                        context = context,
                                        base64Data = parsed.avatarBase64,
                                        identifier = "contact_${cleanDigits}"
                                    )
                                } else {
                                    null
                                }

                                val verifiedContact = Contact(
                                    id = "c_${cleanDigits}",
                                    name = parsed.name,
                                    phone = parsed.phone,
                                    publicKey = parsed.publicKey,
                                    status = context.getString(R.string.contact_verified_badge),
                                    avatarPath = localAvatarPath
                                )
                                try {
                                    com.sha.orbis.storage.FriendRequestRepository(context).certifyFriendInPerson(parsed.phone)
                                } catch (_: Exception) {}
                                Toast.makeText(context, context.getString(R.string.qr_toast_success), Toast.LENGTH_SHORT).show()
                                onContactVerified(verifiedContact)
                                onDismiss()
                            } else {
                                Toast.makeText(context, context.getString(R.string.qr_toast_invalid), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = rawQrInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.qr_btn_certify), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
