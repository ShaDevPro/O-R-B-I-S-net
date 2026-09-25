package com.sha.orbis.ui.social

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sha.orbis.ui.components.FullScreenImageViewerDialog
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import java.io.File
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sha.orbis.R
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.media.MediaAttachmentHelper
import com.sha.orbis.media.VideoMediaHelper
import com.sha.orbis.social.PollOption
import com.sha.orbis.social.SocialPoll
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.components.OrbisVideoPlayer
import com.sha.orbis.ui.theme.OrbisColorPalette
import com.sha.orbis.ui.social.feed.popups.FeedFullscreenPopup
import com.sha.orbis.ui.social.feed.popups.FeedModalTopBar
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import java.util.UUID

@Composable
fun CreatePostDialog(
    authorPhone: String,
    authorName: String = "Moi",
    authorAvatarPath: String? = null,
    initialCircleId: String? = null,
    initialContent: String = "",
    onDismiss: () -> Unit,
    onPostCreated: (
        content: String,
        hashtags: List<String>,
        poll: SocialPoll?,
        circleId: String?,
        excludedCircleIds: List<String>,
        excludedPhones: List<String>,
        isOfficial: Boolean,
        isPinned: Boolean,
        role: UserSocialRole,
        mediaType: String?,
        mediaPath: String?,
        mediaData: String?,
        mediaUrl: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val isAdmin = remember(authorPhone) { AdminSecurityHelper.isAdmin(authorPhone) }

    var content by remember { mutableStateOf(initialContent) }
    var hashtagInput by remember { mutableStateOf("") }
    var showHashtagInput by remember { mutableStateOf(false) }
    val hashtags = remember { mutableStateListOf<String>() }

    val coroutineScope = rememberCoroutineScope()
    var attachedImagePath by remember { mutableStateOf<String?>(null) }
    var attachedImageBase64 by remember { mutableStateOf<String?>(null) }
    var attachedMediaType by remember { mutableStateOf<String?>("image") }
    var showPhotoSourceChooser by remember { mutableStateOf(false) }
    var showVideoSourceChooser by remember { mutableStateOf(false) }
    var showPreviewFullScreen by remember { mutableStateOf(false) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var isCompressingVideo by remember { mutableStateOf(false) }
    var videoCompressionProgress by remember { mutableIntStateOf(0) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isCompressingVideo = true
                videoCompressionProgress = 0
                val result = VideoMediaHelper.processVideoUri(context, uri)
                if (result != null) {
                    // 1. Compression matérielle automatique (comme WhatsApp/Telegram) : 0% -> 60%
                    val compressedFile = VideoMediaHelper.compressVideo(context, result.localFile) { pct ->
                        videoCompressionProgress = (pct * 0.6f).toInt()
                    }
                    if (compressedFile.length() > VideoMediaHelper.MAX_VIDEO_SIZE_BYTES) {
                        isCompressingVideo = false
                        Toast.makeText(context, context.getString(R.string.video_size_too_large), Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    // 2. Encodage Base64 P2P progressif : 60% -> 100%
                    val b64 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        VideoMediaHelper.videoFileToBase64(compressedFile) { pct ->
                            videoCompressionProgress = 60 + (pct * 0.4f).toInt()
                        }
                    }
                    attachedImagePath = compressedFile.absolutePath
                    attachedImageBase64 = b64
                    attachedMediaType = "video"
                    isCompressingVideo = false
                } else {
                    isCompressingVideo = false
                    Toast.makeText(context, context.getString(R.string.video_process_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val (file, b64) = MediaAttachmentHelper.processImageUri(context, uri)
            if (file != null && !b64.isNullOrBlank()) {
                attachedImagePath = file.absolutePath
                attachedImageBase64 = b64
                attachedMediaType = "image"
            } else {
                Toast.makeText(context, context.getString(R.string.chat_error_file_too_large), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val (file, b64) = MediaAttachmentHelper.processImageUri(context, uri)
            if (file != null && !b64.isNullOrBlank()) {
                attachedImagePath = file.absolutePath
                attachedImageBase64 = b64
                attachedMediaType = "image"
            } else {
                Toast.makeText(context, context.getString(R.string.chat_error_file_too_large), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists()) {
            val (file, b64) = MediaAttachmentHelper.processImageFromFile(context, tempCameraFile!!)
            if (file != null && !b64.isNullOrBlank()) {
                attachedImagePath = file.absolutePath
                attachedImageBase64 = b64
                attachedMediaType = "image"
            } else {
                Toast.makeText(context, context.getString(R.string.chat_error_file_too_large), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = com.sha.orbis.permissions.rememberOrbisPermissionLauncher(
        permission = com.sha.orbis.permissions.OrbisPermission.CAMERA,
        onGranted = {
            val cacheDir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
            val file = File(cacheDir, "post_capture_${System.currentTimeMillis()}.jpg")
            tempCameraFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraLauncher.launch(uri)
        }
    )

    var isAddingPoll by remember { mutableStateOf(false) }
    var pollQuestion by remember { mutableStateOf("") }
    val pollOptions = remember { mutableStateListOf("", "") }

    var selectedCircleId by remember { mutableStateOf<String?>(initialCircleId) } // null = Public
    var excludeFamily by remember(selectedCircleId) { mutableStateOf(selectedCircleId == "circle_work") }
    val excludedPhones = remember { mutableStateListOf<String>() }
    var showExcludePicker by remember { mutableStateOf(false) }
    var isOfficialAnnouncement by remember { mutableStateOf(isAdmin) }
    var isPinned by remember { mutableStateOf(isAdmin) }

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

    FeedFullscreenPopup(onDismiss = onDismiss) {
        FeedModalTopBar(
            title = stringResource(R.string.timeline_new_post_title),
            onClose = onDismiss
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                    // Author Info Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OrbisAvatar(
                            avatarPath = authorAvatarPath,
                            name = authorName,
                            size = 46.dp
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = authorName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isAdmin) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ElectricBolt,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.badge_founder_dev),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = OrbisColorPalette.StatusActive,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "RSA-2048 E2EE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OrbisColorPalette.StatusActive
                                    )
                                }
                            }
                        }
                    }

                    // 3. Audience Selector (Dedicated Full-Width Row with Scroll)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            null to "👤 Mon Mur (Tous mes amis)",
                            "circle_family" to "👨‍👩‍👧‍👦 Cercle Famille",
                            "circle_close" to "⭐ Cercle Proches",
                            "circle_work" to "💼 Cercle Pro"
                        ).forEach { (id, label) ->
                            val isSelected = selectedCircleId == id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { selectedCircleId = id }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Exclusion Controls for Family & Specific Contacts (Only when NOT posting exclusively to family)
                    if (selectedCircleId != "circle_family") {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { excludeFamily = !excludeFamily },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = if (excludeFamily) Color(0xFF10B981) else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = stringResource(R.string.social_post_exclude_family_title),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = stringResource(R.string.social_post_exclude_family_subtitle),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = excludeFamily,
                                        onCheckedChange = { excludeFamily = it }
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { showExcludePicker = true },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (excludedPhones.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (excludedPhones.isEmpty())
                                                stringResource(R.string.social_post_exclude_contacts_action)
                                            else
                                                stringResource(R.string.social_post_excluded_contacts_count, excludedPhones.size),
                                            fontSize = 12.sp,
                                            fontWeight = if (excludedPhones.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                                            color = if (excludedPhones.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (excludeFamily || excludedPhones.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF10B981).copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color(0xFF059669),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.social_post_exclusion_summary),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = Color(0xFF059669),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Developer / Admin Privileges Row (Only visible for admin)
                    if (isAdmin) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Official Announcement Toggle Chip
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isOfficialAnnouncement) Color(0xFF38BDF8).copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (isOfficialAnnouncement) Color(0xFF38BDF8) else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { isOfficialAnnouncement = !isOfficialAnnouncement }
                                    .padding(vertical = 8.dp, horizontal = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ElectricBolt,
                                        contentDescription = null,
                                        tint = if (isOfficialAnnouncement) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.social_dialog_admin_official_toggle),
                                        fontSize = 11.sp,
                                        fontWeight = if (isOfficialAnnouncement) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isOfficialAnnouncement) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Pin to Top Toggle Chip
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { isPinned = !isPinned }
                                    .padding(vertical = 8.dp, horizontal = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.social_dialog_pin_toggle),
                                        fontSize = 11.sp,
                                        fontWeight = if (isPinned) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 4. Clean Post Text Area
                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.timeline_new_post_placeholder),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 160.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )

                    // Video Preparation Progress
                    if (isCompressingVideo) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { (videoCompressionProgress / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.size(40.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.5.dp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.video_compressing_progress, videoCompressionProgress),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Attached Media Preview (Photo or Video)
                    if (!attachedImagePath.isNullOrBlank()) {
                        if (attachedMediaType == "video") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            ) {
                                OrbisVideoPlayer(
                                    videoPathOrId = attachedImagePath!!,
                                    modifier = Modifier.fillMaxSize(),
                                    autoPlay = false,
                                    showFullScreenButton = true
                                )
                                IconButton(
                                    onClick = {
                                        attachedImagePath = null
                                        attachedImageBase64 = null
                                        attachedMediaType = "image"
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.social_post_remove_photo),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            val previewBitmap = remember(attachedImagePath) {
                                MediaAttachmentHelper.loadBitmap(attachedImagePath!!)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                    .clickable { showPreviewFullScreen = true }
                            ) {
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        attachedImagePath = null
                                        attachedImageBase64 = null
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.social_post_remove_photo),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 5. Hashtags List & Input
                    if (hashtags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            hashtags.forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .clickable { hashtags.remove(tag) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "#$tag ✕",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    if (showHashtagInput) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (hashtagInput.isBlank()) {
                                    Text(
                                        text = "#tag (ex: gsm, crypto)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = hashtagInput,
                                    onValueChange = { hashtagInput = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clean = hashtagInput.trim().removePrefix("#").lowercase()
                                    if (clean.isNotBlank() && !hashtags.contains(clean)) {
                                        hashtags.add(clean)
                                        hashtagInput = ""
                                        showHashtagInput = false
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // 6. Cryptographic Poll Section
                    if (isAddingPoll) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("📊 Sondage Cryptographique", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = { isAddingPoll = false }, modifier = Modifier.size(22.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }

                                OutlinedTextField(
                                    value = pollQuestion,
                                    onValueChange = { pollQuestion = it },
                                    placeholder = { Text(stringResource(R.string.social_composer_poll_question), fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                pollOptions.forEachIndexed { index, opt ->
                                    OutlinedTextField(
                                        value = opt,
                                        onValueChange = { pollOptions[index] = it },
                                        placeholder = { Text(stringResource(R.string.social_poll_option_hint, index + 1), fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }

                                if (pollOptions.size < 4) {
                                    Text(
                                        text = stringResource(R.string.social_add_poll_option),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable { pollOptions.add("") }
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 7. Action Toolbar (Photo / Video / Tag / Poll)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Photo Button (Gallery & Camera)
                        val isPhotoAttached = attachedImagePath != null && attachedMediaType != "video"
                        Box {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isPhotoAttached) Color(0xFF10B981).copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (isPhotoAttached) Color(0xFF10B981)
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { showPhotoSourceChooser = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = if (isPhotoAttached) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isPhotoAttached) stringResource(R.string.social_post_photo_attached)
                                               else stringResource(R.string.social_post_action_photo),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isPhotoAttached) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showPhotoSourceChooser,
                                onDismissRequest = { showPhotoSourceChooser = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_attach_gallery)) },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showPhotoSourceChooser = false
                                        try {
                                            pickVisualMediaLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        } catch (_: Exception) {
                                            getContentLauncher.launch("image/*")
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_attach_camera)) },
                                    leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showPhotoSourceChooser = false
                                        cameraPermissionLauncher.launch()
                                    }
                                )
                                if (isPhotoAttached) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.social_post_remove_photo), color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showPhotoSourceChooser = false
                                            attachedImagePath = null
                                            attachedImageBase64 = null
                                            attachedMediaType = "image"
                                        }
                                    )
                                }
                            }
                        }

                        // 2. Dedicated Video Button (Direct 1-tap import)
                        val isVideoAttached = attachedImagePath != null && attachedMediaType == "video"
                        Box {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isVideoAttached) Color(0xFFEF4444).copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (isVideoAttached) Color(0xFFEF4444)
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        if (isVideoAttached) {
                                            showVideoSourceChooser = true
                                        } else {
                                            videoPickerLauncher.launch("video/*")
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = if (isVideoAttached) Color(0xFFEF4444) else Color(0xFFEF4444),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isVideoAttached) stringResource(R.string.social_post_video_attached)
                                               else stringResource(R.string.social_post_action_video),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isVideoAttached) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showVideoSourceChooser,
                                onDismissRequest = { showVideoSourceChooser = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.social_post_action_video)) },
                                    leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFFEF4444)) },
                                    onClick = {
                                        showVideoSourceChooser = false
                                        videoPickerLauncher.launch("video/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.social_post_remove_photo), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showVideoSourceChooser = false
                                        attachedImagePath = null
                                        attachedImageBase64 = null
                                        attachedMediaType = "image"
                                    }
                                )
                            }
                        }

                        // Tag Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showHashtagInput = !showHashtagInput }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Text("Tag", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        // Poll Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { isAddingPoll = !isAddingPoll }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Poll, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Text("Sondage", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                    // 8. Publish Button (Premium style)
                    Button(
                        onClick = {
                            if (content.isNotBlank() || !attachedImagePath.isNullOrBlank()) {
                                val poll = if (isAddingPoll && pollQuestion.isNotBlank()) {
                                    val validOptions = pollOptions.filter { it.isNotBlank() }
                                    if (validOptions.size >= 2) {
                                        SocialPoll(
                                            id = UUID.randomUUID().toString(),
                                            question = pollQuestion.trim(),
                                            options = validOptions.mapIndexed { idx, title ->
                                                PollOption("opt_${UUID.randomUUID().toString().take(6)}", title.trim(), 0)
                                            },
                                            totalVotes = 0
                                        )
                                    } else null
                                } else null

                                val role = if (isAdmin) UserSocialRole.FOUNDER_DEV else UserSocialRole.STANDARD

                                onPostCreated(
                                    content.trim(),
                                    hashtags.toList(),
                                    poll,
                                    selectedCircleId,
                                    if (excludeFamily && selectedCircleId != "circle_family") listOf("circle_family") else emptyList(),
                                    excludedPhones.toList(),
                                    isOfficialAnnouncement,
                                    isPinned,
                                    role,
                                    if (!attachedImagePath.isNullOrBlank()) (attachedMediaType ?: "image") else null,
                                    attachedImagePath,
                                    attachedImageBase64,
                                    null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isCompressingVideo && (content.isNotBlank() || !attachedImagePath.isNullOrBlank()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.timeline_btn_publish),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
        }
    }

    if (showPreviewFullScreen && !attachedImagePath.isNullOrBlank()) {
        FullScreenImageViewerDialog(
            imagePathOrBase64 = attachedImagePath!!,
            title = stringResource(R.string.chat_image_viewer_title),
            onDismiss = { showPreviewFullScreen = false }
        )
    }
}
