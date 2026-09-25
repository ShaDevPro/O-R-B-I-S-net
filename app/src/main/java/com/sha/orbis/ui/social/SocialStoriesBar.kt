package com.sha.orbis.ui.social

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Videocam
import com.sha.orbis.media.MediaDownloadManager
import com.sha.orbis.media.VideoMediaHelper
import com.sha.orbis.ui.components.OrbisVideoPlayer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.sha.orbis.R
import com.sha.orbis.media.MediaAttachmentHelper
import com.sha.orbis.social.SocialReaction
import com.sha.orbis.social.SocialStory
import com.sha.orbis.social.UserStoryGroup
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.theme.OrbisColorPalette
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 6 dégradés modernes inspirés de Facebook et Instagram pour les stories textuelles.
 */
val STORY_GRADIENTS = listOf(
    listOf(Color(0xFFFF512F), Color(0xFFDD2476)), // 0: Sunset Glow
    listOf(Color(0xFF00C6FF), Color(0xFF0072FF)), // 1: Ocean Breeze
    listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)), // 2: Cyber Violet
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)), // 3: Emerald Mint
    listOf(Color(0xFF141E30), Color(0xFF243B55)), // 4: Midnight Galaxy
    listOf(Color(0xFFF7971E), Color(0xFFFFD200))  // 5: Solar Amber
)

private val STORY_REACTION_EMOJIS = listOf("❤️", "🔥", "👏", "💡", "🛡️")

private fun stableViewerKey(value: String): String {
    val digits = value.filter { it.isDigit() }
    return if (digits.length >= 8) digits.takeLast(10) else value.trim().lowercase(Locale.ROOT)
}

data class StoryViewerSession(
    val groups: List<UserStoryGroup>,
    val initialGroupIndex: Int,
    val initialStoryIndex: Int
)

@Composable
fun SocialStoriesBar(
    stories: List<SocialStory>,
    userAvatarPath: String?,
    userName: String,
    currentPhone: String = "",
    onAddStory: (
        content: String,
        mediaPath: String?,
        mediaBase64: String?,
        gradientIndex: Int,
        mediaType: String?,
        mediaUrl: String?,
        targetCircleId: String?,
        excludedCircleIds: List<String>,
        excludedPhones: List<String>
    ) -> Unit,
    onDeleteStory: ((String) -> Unit)? = null,
    onStorySeen: ((SocialStory) -> Unit)? = null,
    onStoryReact: ((SocialStory, String) -> Unit)? = null
) {
    var showCreateStoryDialog by remember { mutableStateOf(false) }
    var viewerSession by remember { mutableStateOf<StoryViewerSession?>(null) }

    val nonExpiredStories = remember(stories) {
        stories.filterNot { it.isExpired }
    }

    val myStories = remember(nonExpiredStories, currentPhone) {
        if (currentPhone.isBlank()) emptyList()
        else nonExpiredStories.filter { FriendRequestRepository.isSamePhone(it.authorPhone, currentPhone) }
            .sortedBy { it.createdAt }
    }

    val myGroup = remember(myStories, userAvatarPath, userName, currentPhone) {
        if (myStories.isEmpty()) null
        else UserStoryGroup(
            authorPhone = currentPhone,
            authorName = userName.ifBlank { "Moi" },
            authorAvatarPath = userAvatarPath,
            authorPubkey = null,
            stories = myStories
        )
    }

    val otherGroups = remember(nonExpiredStories, currentPhone) {
        val others = if (currentPhone.isBlank()) nonExpiredStories
        else nonExpiredStories.filterNot { FriendRequestRepository.isSamePhone(it.authorPhone, currentPhone) }

        others.groupBy {
            val phone = it.authorPhone.trim()
            if (phone.isNotBlank()) phone else (it.authorPubkey ?: it.authorName)
        }.values.map { authorStoryList ->
            val first = authorStoryList.first()
            UserStoryGroup(
                authorPhone = first.authorPhone,
                authorName = first.authorName,
                authorAvatarPath = first.authorAvatarPath,
                authorPubkey = first.authorPubkey,
                stories = authorStoryList.sortedBy { it.createdAt }
            )
        }.sortedByDescending { it.hasUnseen(currentPhone) }
    }

    if (showCreateStoryDialog) {
        CreateStoryDialog(
            onDismiss = { showCreateStoryDialog = false },
            onStoryCreated = { content, mediaPath, mediaBase64, gradientIndex, mediaType, mediaUrl, targetCircleId, excludedCircleIds, excludedPhones ->
                onAddStory(content, mediaPath, mediaBase64, gradientIndex, mediaType, mediaUrl, targetCircleId, excludedCircleIds, excludedPhones)
                showCreateStoryDialog = false
            }
        )
    }

    val context = LocalContext.current

    if (viewerSession != null) {
        StoryViewerDialog(
            groups = viewerSession!!.groups,
            initialGroupIndex = viewerSession!!.initialGroupIndex,
            initialStoryIndex = viewerSession!!.initialStoryIndex,
            currentPhone = currentPhone,
            onDismiss = { viewerSession = null },
            onDeleteStory = { storyId ->
                onDeleteStory?.invoke(storyId)
            },
            onStorySeen = { story ->
                onStorySeen?.invoke(story)
                try {
                    com.sha.orbis.telemetry.TelemetryManager.getInstance(context).recordEvent(com.sha.orbis.telemetry.FeatureType.STORY_VIEW)
                } catch (_: Exception) {}
            },
            onStoryReact = { story, emoji ->
                onStoryReact?.invoke(story, emoji)
            }
        )
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // "Ma Story" / Ajouter une Story (avec cumul)
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable {
                    if (myGroup != null) {
                        viewerSession = StoryViewerSession(
                            groups = listOf(myGroup),
                            initialGroupIndex = 0,
                            initialStoryIndex = 0
                        )
                    } else {
                        showCreateStoryDialog = true
                    }
                }
            ) {
                Box(
                    modifier = Modifier.size(62.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    if (myGroup != null) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            Color(0xFF38BDF8),
                                            MaterialTheme.colorScheme.primary
                                        )
                                    )
                                )
                                .padding(2.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OrbisAvatar(
                                avatarPath = userAvatarPath,
                                name = userName,
                                size = 52.dp
                            )
                        }

                        // Badge de cumul si plus d'une story active
                        if (myStories.size > 1) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${myStories.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    } else {
                        OrbisAvatar(
                            avatarPath = userAvatarPath,
                            name = userName,
                            size = 56.dp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Bouton '+' pour ajouter une nouvelle story au cumul
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable { showCreateStoryDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = if (myGroup != null) stringResource(R.string.social_story_my_story) else stringResource(R.string.social_add_story),
                    fontSize = 11.sp,
                    fontWeight = if (myGroup != null) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Stories des contacts / amis avec cumul et anneau dégradé dynamique
        items(otherGroups, key = { it.authorPhone.ifBlank { it.authorPubkey ?: it.authorName } }) { group ->
            val hasUnseen = group.hasUnseen(currentPhone)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable {
                    val groupIndex = otherGroups.indexOf(group).coerceAtLeast(0)
                    val startStoryIndex = group.getFirstUnseenIndex(currentPhone)
                    viewerSession = StoryViewerSession(
                        groups = otherGroups,
                        initialGroupIndex = groupIndex,
                        initialStoryIndex = startStoryIndex
                    )
                }
            ) {
                Box(
                    modifier = Modifier.size(62.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .then(
                                if (hasUnseen) {
                                    Modifier.background(
                                        Brush.sweepGradient(
                                            listOf(
                                                Color(0xFFFF512F),
                                                Color(0xFFDD2476),
                                                Color(0xFF8E2DE2),
                                                Color(0xFFFF512F)
                                            )
                                        )
                                    )
                                } else {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f), CircleShape)
                                }
                            )
                            .padding(2.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OrbisAvatar(
                            avatarPath = group.authorAvatarPath,
                            name = group.authorName,
                            size = 52.dp
                        )
                    }

                    // Badge de cumul (ex: 2, 3) pour indiquer plusieurs stories cumulées
                    if (group.stories.size > 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (hasUnseen) Color(0xFFDD2476) else MaterialTheme.colorScheme.secondary)
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${group.stories.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                Text(
                    text = group.authorName.split(" ").firstOrNull() ?: group.authorName,
                    fontSize = 11.sp,
                    fontWeight = if (hasUnseen) FontWeight.Bold else FontWeight.Normal,
                    color = if (hasUnseen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CreateStoryDialog(
    onDismiss: () -> Unit,
    onStoryCreated: (
        content: String,
        mediaPath: String?,
        mediaBase64: String?,
        gradientIndex: Int,
        mediaType: String?,
        mediaUrl: String?,
        targetCircleId: String?,
        excludedCircleIds: List<String>,
        excludedPhones: List<String>
    ) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPhotoMode by remember { mutableStateOf(false) }
    var textContent by remember { mutableStateOf("") }
    var captionContent by remember { mutableStateOf("") }
    var selectedGradientIndex by remember { mutableIntStateOf(0) }

    var attachedImagePath by remember { mutableStateOf<String?>(null) }
    var attachedImageBase64 by remember { mutableStateOf<String?>(null) }
    var attachedMediaUrl by remember { mutableStateOf<String?>(null) }
    var attachedMediaType by remember { mutableStateOf<String?>("image") }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var isCompressingVideo by remember { mutableStateOf(false) }
    var videoCompressionProgress by remember { mutableIntStateOf(0) }
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

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isCompressingVideo = true
                videoCompressionProgress = 0
                val result = VideoMediaHelper.processVideoUri(context, uri, maxDurationSec = 60)
                if (result != null) {
                    // 1. Compression matérielle automatique (0% -> 50%)
                    val compressedFile = VideoMediaHelper.compressVideo(context, result.localFile) { pct ->
                        videoCompressionProgress = (pct * 0.5f).toInt()
                    }
                    if (compressedFile.length() > VideoMediaHelper.MAX_VIDEO_SIZE_BYTES) {
                        isCompressingVideo = false
                        Toast.makeText(context, context.getString(R.string.video_size_too_large), Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    // 2. Envoi direct vers serveur média Blossom décentralisé (BUD-01/02/11) : 50% -> 100%
                    val uploadResult = com.sha.orbis.nostr.media.BlossomMediaManager.uploadVideo(context, compressedFile) { pct ->
                        videoCompressionProgress = 50 + (pct * 0.5f).toInt()
                    }
                    if (uploadResult != null) {
                        attachedImagePath = compressedFile.absolutePath
                        attachedMediaUrl = uploadResult.url
                        attachedImageBase64 = null
                        attachedMediaType = "video"
                    } else {
                        Toast.makeText(context, context.getString(R.string.social_story_upload_failed), Toast.LENGTH_LONG).show()
                    }
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
            } else {
                Toast.makeText(context, context.getString(R.string.chat_error_file_too_large), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = com.sha.orbis.permissions.rememberOrbisPermissionLauncher(
        permission = com.sha.orbis.permissions.OrbisPermission.CAMERA,
        onGranted = {
            try {
                val cacheDir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
                val file = File(cacheDir, "story_capture_${System.currentTimeMillis()}.jpg")
                tempCameraFile = file
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.chat_error_camera_unavailable), Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun launchGallery() {
        try {
            pickVisualMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (_: Exception) {
            getContentLauncher.launch("image/*")
        }
    }

    fun launchCamera() {
        cameraPermissionLauncher.launch()
    }

    val canPublish = !isCompressingVideo && if (isPhotoMode) {
        !attachedImagePath.isNullOrBlank() || !attachedMediaUrl.isNullOrBlank()
    } else {
        textContent.isNotBlank()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.social_stories_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                // Mode Switcher: Photo vs Text
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Mode Media (Photo & Video)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPhotoMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { isPhotoMode = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = if (isPhotoMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.social_story_mode_media),
                                fontSize = 13.sp,
                                fontWeight = if (isPhotoMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (isPhotoMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Mode Texte
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isPhotoMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { isPhotoMode = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = if (!isPhotoMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.social_story_mode_text),
                                fontSize = 13.sp,
                                fontWeight = if (!isPhotoMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isPhotoMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            null to stringResource(R.string.social_audience_all_friends),
                            "circle_family" to stringResource(R.string.social_audience_family),
                            "circle_close" to stringResource(R.string.social_audience_close),
                            "circle_work" to stringResource(R.string.social_audience_work)
                        ).forEach { (id, label) ->
                            val isSelected = selectedCircleId == id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable {
                                        selectedCircleId = id
                                        if (id == "circle_family") {
                                            excludedPhones.clear()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (selectedCircleId != "circle_family") {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
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

                                TextButton(
                                    onClick = { showExcludePicker = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
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

                                if (excludeFamily || excludedPhones.isNotEmpty()) {
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
                }

                if (isPhotoMode) {
                    // --- MEDIA MODE (PHOTO & VIDEO) ---
                    if (isCompressingVideo) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
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
                                    color = Color(0xFFEF4444),
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
                    } else if (attachedImagePath == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .padding(vertical = 16.dp, horizontal = 12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.social_story_add_media),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Camera Button
                                Button(
                                    onClick = { launchCamera() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.chat_attach_camera),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // 2. Gallery Button
                                Button(
                                    onClick = { launchGallery() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.chat_attach_gallery),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // 3. Video Button (Vibrant red, bold, 100% visible)
                                Button(
                                    onClick = { videoPickerLauncher.launch("video/*") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEF4444),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.chat_attach_video),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.social_story_video_hint),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    } else {
                        // Photo or Video Preview & Controls
                        if (attachedMediaType == "video") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                OrbisVideoPlayer(
                                    videoPathOrId = attachedImagePath!!,
                                    modifier = Modifier.fillMaxSize(),
                                    autoPlay = false,
                                    showFullScreenButton = false
                                )
                                IconButton(
                                    onClick = {
                                        attachedImagePath = null
                                        attachedImageBase64 = null
                                        attachedMediaUrl = null
                                        attachedMediaType = "image"
                                    },
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x99000000))
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            val previewBitmap = remember(attachedImagePath) {
                                MediaAttachmentHelper.loadBitmap(attachedImagePath!!)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x99000000))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    IconButton(onClick = { launchGallery() }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.social_story_change_photo), tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            attachedImagePath = null
                                            attachedImageBase64 = null
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.social_post_remove_photo), tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        // Caption input
                        OutlinedTextField(
                            value = captionContent,
                            onValueChange = { captionContent = it },
                            placeholder = { Text(stringResource(R.string.social_story_caption_placeholder), fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                } else {
                    // --- TEXT MODE ---
                    // Live Gradient Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(STORY_GRADIENTS[selectedGradientIndex]))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = textContent.ifBlank { stringResource(R.string.social_story_text_placeholder) },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Gradient Color Palette Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.social_story_gradient_style),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(STORY_GRADIENTS) { index, gradientColors ->
                                val isSelected = selectedGradientIndex == index
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(gradientColors))
                                        .clickable { selectedGradientIndex = index }
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            } else {
                                                Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Text Input
                    OutlinedTextField(
                        value = textContent,
                        onValueChange = { textContent = it },
                        placeholder = { Text(stringResource(R.string.social_story_text_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(95.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                // E2EE Notice
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = OrbisColorPalette.StatusActive,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stringResource(R.string.social_story_e2ee_notice),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Publish Button
                Button(
                    onClick = {
                        val finalExcludedCircleIds = if (excludeFamily && selectedCircleId != "circle_family") listOf("circle_family") else emptyList()
                        val finalExcludedPhones = if (selectedCircleId == "circle_family") emptyList() else excludedPhones.toList()
                        if (isPhotoMode && (attachedImagePath != null || attachedMediaUrl != null)) {
                            onStoryCreated(
                                captionContent.trim(),
                                attachedImagePath,
                                attachedImageBase64,
                                0,
                                attachedMediaType,
                                attachedMediaUrl,
                                selectedCircleId,
                                finalExcludedCircleIds,
                                finalExcludedPhones
                            )
                        } else if (!isPhotoMode && textContent.isNotBlank()) {
                            onStoryCreated(
                                textContent.trim(),
                                null,
                                null,
                                selectedGradientIndex,
                                null,
                                null,
                                selectedCircleId,
                                finalExcludedCircleIds,
                                finalExcludedPhones
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = canPublish,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.social_story_publish_btn), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StoryViewerDialog(
    groups: List<UserStoryGroup>,
    initialGroupIndex: Int,
    initialStoryIndex: Int,
    currentPhone: String,
    onDismiss: () -> Unit,
    onDeleteStory: (String) -> Unit,
    onStorySeen: (SocialStory) -> Unit,
    onStoryReact: (SocialStory, String) -> Unit
) {
    val context = LocalContext.current
    var activeGroups by remember(groups) { mutableStateOf(groups) }
    var currentGroupIndex by remember { mutableIntStateOf(initialGroupIndex.coerceIn(0, (groups.size - 1).coerceAtLeast(0))) }
    var currentStoryIndex by remember { mutableIntStateOf(initialStoryIndex) }

    if (activeGroups.isEmpty() || currentGroupIndex !in activeGroups.indices) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val currentGroup = activeGroups[currentGroupIndex]
    if (currentGroup.stories.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val safeStoryIndex = currentStoryIndex.coerceIn(0, currentGroup.stories.lastIndex)
    val currentStory = currentGroup.stories[safeStoryIndex]

    val isMyStory = remember(currentGroup.authorPhone, currentPhone) {
        currentPhone.isNotBlank() && FriendRequestRepository.isSamePhone(currentGroup.authorPhone, currentPhone)
    }

    val relativeTime = remember(currentStory.createdAt) {
        SocialDateFormatter.formatRelative(context, currentStory.createdAt)
    }

    var localVideoPath by remember(currentStory.id, currentStory.mediaPath) {
        mutableStateOf(currentStory.mediaPath?.takeIf { java.io.File(it).exists() }
            ?: VideoMediaHelper.getVideoFile(context, "story_${currentStory.id}")?.absolutePath)
    }
    var isDownloadingStoryVideo by remember(currentStory.id) { mutableStateOf(false) }
    var storyVideoDownloadProgress by remember(currentStory.id) { mutableIntStateOf(0) }

    LaunchedEffect(currentStory.id, currentStory.mediaUrl, currentStory.mediaBase64) {
        if (currentStory.isVideo && (localVideoPath == null || !java.io.File(localVideoPath!!).exists())) {
            val cleanStoryId = currentStory.id.filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "story_${System.currentTimeMillis()}" }
            if (!currentStory.mediaUrl.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    val file = com.sha.orbis.nostr.media.BlossomMediaManager.downloadVideo(context, currentStory.mediaUrl!!, "story_$cleanStoryId")
                    if (file != null && file.exists()) {
                        localVideoPath = file.absolutePath
                    }
                }
            } else if (!currentStory.mediaBase64.isNullOrBlank()) {
                isDownloadingStoryVideo = true
                val file = withContext(Dispatchers.IO) {
                    VideoMediaHelper.base64ToVideoFile(context, currentStory.mediaBase64!!, "story_$cleanStoryId") { pct ->
                        storyVideoDownloadProgress = pct
                    }
                }
                if (file != null && file.exists()) {
                    localVideoPath = file.absolutePath
                }
                isDownloadingStoryVideo = false
            }
        }
    }

    val storyDurationMillis = remember(currentStory.id, localVideoPath, currentStory.mediaType, currentStory.mediaUrl) {
        if (currentStory.isVideo && !localVideoPath.isNullOrBlank()) {
            val file = java.io.File(localVideoPath!!)
            if (file.exists()) {
                val retr = android.media.MediaMetadataRetriever()
                val dur = try {
                    retr.setDataSource(file.absolutePath)
                    retr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 15000L
                } catch (_: Exception) { 15000L }
                finally { try { retr.release() } catch (_: Exception) {} }
                dur.coerceIn(2000L, 60000L)
            } else 15000L
        } else if (currentStory.isVideo) {
            15000L
        } else {
            5000L
        }
    }
    var isPaused by remember { mutableStateOf(false) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showViewersSheet by remember { mutableStateOf(false) }
    var viewersSheetInitialTab by remember { mutableIntStateOf(0) }

    fun replaceCurrentStory(updatedStory: SocialStory) {
        val updatedStories = currentGroup.stories.toMutableList()
        if (safeStoryIndex in updatedStories.indices) {
            updatedStories[safeStoryIndex] = updatedStory
            val updatedGroups = activeGroups.toMutableList()
            updatedGroups[currentGroupIndex] = currentGroup.copy(stories = updatedStories)
            activeGroups = updatedGroups
        }
    }

    val seenCount = remember(currentStory.seenBy) {
        currentStory.seenBy
            .map { stableViewerKey(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .size
    }
    val reactionCount = remember(currentStory.reactions) {
        currentStory.reactions
            .map { stableViewerKey(it.userPhone) }
            .filter { it.isNotBlank() }
            .distinct()
            .size
    }
    val myReaction = remember(currentStory.reactions, currentPhone) {
        if (currentPhone.isBlank()) null else currentStory.reactions.firstOrNull {
            FriendRequestRepository.isSamePhone(it.userPhone, currentPhone) ||
                stableViewerKey(it.userPhone) == stableViewerKey(currentPhone)
        }?.emoji
    }

    // Dès qu'on arrive sur une story, on la marque comme vue
    LaunchedEffect(currentStory.id) {
        elapsedMillis = 0L
        if (!isMyStory) {
            onStorySeen(currentStory)
            if (currentPhone.isNotBlank() && currentStory.seenBy.none { FriendRequestRepository.isSamePhone(it, currentPhone) }) {
                replaceCurrentStory(currentStory.copy(seenBy = currentStory.seenBy + currentPhone))
            }
        }
    }

    // Minuterie séquentielle auto-advance avec mise en pause (hold to pause ou pendant téléchargement vidéo)
    LaunchedEffect(currentStory.id, isPaused, isDownloadingStoryVideo, showDeleteConfirmDialog, showViewersSheet) {
        if (!isPaused && !isDownloadingStoryVideo && !showDeleteConfirmDialog && !showViewersSheet) {
            val startRef = System.currentTimeMillis() - elapsedMillis
            while (elapsedMillis < storyDurationMillis) {
                elapsedMillis = System.currentTimeMillis() - startRef
                delay(25L)
            }
            // Fin du temps : passer à la story suivante ou à l'utilisateur suivant
            if (safeStoryIndex < currentGroup.stories.lastIndex) {
                currentStoryIndex = safeStoryIndex + 1
                elapsedMillis = 0L
            } else if (currentGroupIndex < activeGroups.lastIndex) {
                currentGroupIndex++
                val nextGroup = activeGroups[currentGroupIndex]
                currentStoryIndex = nextGroup.getFirstUnseenIndex(currentPhone)
                elapsedMillis = 0L
            } else {
                onDismiss()
            }
        }
    }

    val activeProgress = (elapsedMillis.toFloat() / storyDurationMillis.toFloat()).coerceIn(0f, 1f)

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.social_story_delete_title)) },
            text = { Text(stringResource(R.string.social_story_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        val storyIdToDelete = currentStory.id
                        onDeleteStory(storyIdToDelete)

                        val updatedStories = currentGroup.stories.filterNot { it.id == storyIdToDelete }
                        if (updatedStories.isNotEmpty()) {
                            val updatedGroup = currentGroup.copy(stories = updatedStories)
                            val newGroups = activeGroups.toMutableList()
                            newGroups[currentGroupIndex] = updatedGroup
                            activeGroups = newGroups
                            if (currentStoryIndex >= updatedStories.size) {
                                currentStoryIndex = updatedStories.lastIndex
                            }
                            elapsedMillis = 0L
                        } else {
                            val newGroups = activeGroups.filterIndexed { idx, _ -> idx != currentGroupIndex }
                            activeGroups = newGroups
                            if (newGroups.isEmpty()) {
                                onDismiss()
                            } else {
                                if (currentGroupIndex >= newGroups.size) {
                                    currentGroupIndex = newGroups.lastIndex
                                }
                                currentStoryIndex = 0
                                elapsedMillis = 0L
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.friends_btn_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.friends_btn_cancel_invite))
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .height(600.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Contenu de la story + gestes navigation/pause
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentStory.id, safeStoryIndex, currentGroupIndex) {
                            detectTapGestures(
                                onPress = {
                                    isPaused = true
                                    tryAwaitRelease()
                                    isPaused = false
                                },
                                onTap = { offset ->
                                    if (offset.x < size.width * 0.3f) {
                                        // Tap gauche : story précédente
                                        if (safeStoryIndex > 0) {
                                            currentStoryIndex = safeStoryIndex - 1
                                            elapsedMillis = 0L
                                        } else if (currentGroupIndex > 0) {
                                            currentGroupIndex--
                                            currentStoryIndex = activeGroups[currentGroupIndex].stories.lastIndex
                                            elapsedMillis = 0L
                                        } else {
                                            elapsedMillis = 0L
                                        }
                                    } else {
                                        // Tap droit : story suivante
                                        if (safeStoryIndex < currentGroup.stories.lastIndex) {
                                            currentStoryIndex = safeStoryIndex + 1
                                            elapsedMillis = 0L
                                        } else if (currentGroupIndex < activeGroups.lastIndex) {
                                            currentGroupIndex++
                                            val nextGroup = activeGroups[currentGroupIndex]
                                            currentStoryIndex = nextGroup.getFirstUnseenIndex(currentPhone)
                                            elapsedMillis = 0L
                                        } else {
                                            onDismiss()
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    // Contenu de la story : Vidéo, Image ou Texte vibrant dégradé
                    if (currentStory.isVideo) {
                        val videoSource = localVideoPath?.takeIf { java.io.File(it).exists() } ?: currentStory.mediaUrl
                        if (!videoSource.isNullOrBlank()) {
                            OrbisVideoPlayer(
                                videoPathOrId = videoSource,
                                modifier = Modifier.fillMaxSize(),
                                autoPlay = !isPaused && !isDownloadingStoryVideo,
                                loop = false,
                                initialMuted = false,
                                showFullScreenButton = false,
                                onPlayerEnded = {
                                    if (safeStoryIndex < currentGroup.stories.lastIndex) {
                                        currentStoryIndex = safeStoryIndex + 1
                                        elapsedMillis = 0L
                                    } else if (currentGroupIndex < activeGroups.lastIndex) {
                                        currentGroupIndex++
                                        val nextGroup = activeGroups[currentGroupIndex]
                                        currentStoryIndex = nextGroup.getFirstUnseenIndex(currentPhone)
                                        elapsedMillis = 0L
                                    } else {
                                        onDismiss()
                                    }
                                }
                            )
                        } else if (isDownloadingStoryVideo) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { (storyVideoDownloadProgress / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.size(48.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 4.dp
                                    )
                                    Text(
                                        text = stringResource(R.string.video_downloading_progress, storyVideoDownloadProgress),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.video_file_not_found),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else if (currentStory.hasMedia) {
                        val mediaBitmap = remember(currentStory.id, currentStory.mediaPath, currentStory.mediaBase64) {
                            MediaAttachmentHelper.loadBitmap(currentStory.mediaPath ?: currentStory.mediaBase64 ?: "")
                        }
                        if (mediaBitmap != null) {
                            Image(
                                bitmap = mediaBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.linearGradient(STORY_GRADIENTS[0])),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.social_story_image_loading), color = Color.White)
                            }
                        }

                        // Légende optionnelle
                        if (currentStory.content.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color(0xCC000000), Color(0xF2000000))
                                        )
                                    )
                                    .padding(horizontal = 20.dp, vertical = 24.dp)
                            ) {
                                Text(
                                    text = currentStory.content,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        // Story texte avec dégradé
                        val gradient = STORY_GRADIENTS[currentStory.backgroundGradientIndex.coerceIn(0, STORY_GRADIENTS.lastIndex)]
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(gradient))
                                .padding(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentStory.content,
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                lineHeight = 32.sp
                            )
                        }
                    }
                }

                if (!isMyStory) {
                    val bottomPadding = if (currentStory.hasMedia && currentStory.content.isNotBlank()) 96.dp else 20.dp
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.social_story_react_hint),
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0x66000000))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            STORY_REACTION_EMOJIS.forEach { emoji ->
                                val isSelected = myReaction == emoji
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White.copy(alpha = 0.28f) else Color.Transparent)
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) Color.White.copy(alpha = 0.72f) else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            val withoutMine = currentStory.reactions.filterNot {
                                                FriendRequestRepository.isSamePhone(it.userPhone, currentPhone) ||
                                                    stableViewerKey(it.userPhone) == stableViewerKey(currentPhone)
                                            }
                                            val updatedReactions = if (isSelected || currentPhone.isBlank()) {
                                                withoutMine
                                            } else {
                                                withoutMine + SocialReaction(
                                                    id = UUID.randomUUID().toString(),
                                                    targetId = currentStory.id,
                                                    userPhone = currentPhone,
                                                    emoji = emoji
                                                )
                                            }
                                            val updatedStory = currentStory.copy(reactions = updatedReactions)
                                            replaceCurrentStory(updatedStory)
                                            onStoryReact(currentStory, emoji)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }

                // Superposition Haute : Barres de progression segmentées + En-tête profil auteur et boutons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xD9000000), Color(0x66000000), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Barres de progression segmentées (une barre par story du groupe actuel)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        currentGroup.stories.forEachIndexed { index, _ ->
                            val fillFraction = when {
                                index < safeStoryIndex -> 1f
                                index == safeStoryIndex -> activeProgress
                                else -> 0f
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.35f))
                            ) {
                                if (fillFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fillFraction)
                                            .height(3.dp)
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    }

                    // En-tête Auteur & Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OrbisAvatar(
                                avatarPath = currentGroup.authorAvatarPath,
                                name = currentGroup.authorName,
                                size = 40.dp
                            )
                            Column {
                                Text(
                                    text = currentGroup.authorName,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${safeStoryIndex + 1}/${currentGroup.stories.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                    Text(
                                        text = "•",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = relativeTime,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isMyStory) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x33FFFFFF))
                                        .clickable {
                                            viewersSheetInitialTab = 0
                                            showViewersSheet = true
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "$seenCount",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (reactionCount > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x33FFFFFF))
                                            .clickable {
                                                viewersSheetInitialTab = 1
                                                showViewersSheet = true
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.social_story_reactions_count, reactionCount),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.social_story_delete_title),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (currentStory.hasMedia) {
                                IconButton(
                                    onClick = {
                                        val mediaSource = if (currentStory.isVideo) {
                                            localVideoPath?.takeIf { java.io.File(it).exists() }
                                                ?: currentStory.mediaUrl
                                                ?: currentStory.mediaPath
                                                ?: currentStory.mediaBase64
                                                ?: ""
                                        } else {
                                            currentStory.mediaPath ?: currentStory.mediaBase64 ?: ""
                                        }
                                        if (mediaSource.isNotBlank()) {
                                            if (currentStory.isVideo) {
                                                MediaDownloadManager.saveVideoAsync(context, mediaSource, "story_${currentStory.authorName}")
                                            } else {
                                                MediaDownloadManager.saveImageAsync(context, mediaSource, "story_${currentStory.authorName}")
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = stringResource(
                                            if (currentStory.isVideo) R.string.video_action_download else R.string.media_download_image
                                        ),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom sheet des viewers/réacteurs de la story
    if (showViewersSheet) {
        StoryViewersSheet(
            story = currentStory,
            initialTab = viewersSheetInitialTab,
            onDismiss = { showViewersSheet = false }
        )
    }
}
