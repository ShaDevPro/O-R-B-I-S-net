package com.sha.orbis.ui.social

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Videocam
import android.widget.Toast
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.sha.orbis.media.MediaDownloadManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.sha.orbis.ui.components.LinkifiedText
import com.sha.orbis.media.MediaAttachmentHelper
import com.sha.orbis.ui.components.FullScreenImageViewerDialog
import com.sha.orbis.ui.components.OrbisVideoPlayer
import com.sha.orbis.ui.conversation.SocialLinkPreviewCard
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.social.PollOption
import com.sha.orbis.social.SocialPoll
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.social.feed.FeedActionBarIg
import com.sha.orbis.ui.social.feed.FeedCaptionBlock
import com.sha.orbis.ui.social.feed.FeedCommentPreview
import com.sha.orbis.ui.social.feed.FeedDoubleTapHeartOverlay
import com.sha.orbis.ui.social.feed.FeedEngagementSummary
import com.sha.orbis.ui.social.feed.FeedPostContextRibbons
import com.sha.orbis.ui.social.feed.FeedPostDivider
import com.sha.orbis.ui.social.feed.FeedPostHeaderActions
import com.sha.orbis.ui.social.feed.FeedPostHeaderIg
import com.sha.orbis.ui.social.feed.popups.FeedBlockUserConfirmDialog
import com.sha.orbis.ui.social.feed.popups.FeedEditPostDialog
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.sha.orbis.ui.theme.OrbisColorPalette
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SocialPostCard(
    post: SocialPost,
    currentPhone: String,
    onReact: (emoji: String) -> Unit,
    onVotePoll: (optionId: String) -> Unit,
    onOpenComments: () -> Unit,
    onDeletePost: (() -> Unit)? = null,
    onEditPost: ((postId: String, newContent: String, newHashtags: List<String>) -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onRepost: (() -> Unit)? = null,
    onShareToChat: (() -> Unit)? = null,
    onShowReactions: (() -> Unit)? = null,
    onAuthorClick: (() -> Unit)? = null,
    recommendationReason: com.sha.orbis.social.algorithm.RecommendationReason? = null,
    matchingTags: List<String> = emptyList()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val timeFormatted = remember(post.timestamp) {
        SocialDateFormatter.formatRelative(context, post.timestamp)
    }
    val audienceText = when {
        post.targetCircleId == "circle_family" -> stringResource(R.string.social_post_audience_family)
        post.targetCircleId != null -> stringResource(R.string.social_post_audience_circle)
        else -> stringResource(R.string.social_post_audience_public)
    }
    val metaLine = remember(timeFormatted, audienceText, post.isEdited) {
        buildList {
            add(timeFormatted)
            add(audienceText)
            if (post.isEdited) add(context.getString(R.string.social_post_edited))
        }.joinToString(" • ")
    }

    var showEmojiPalette by remember { mutableStateOf(false) }
    var showHeartBurst by remember { mutableStateOf(false) }
    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            delay(650)
            showHeartBurst = false
        }
    }
    var showEditDialog by remember { mutableStateOf(false) }
    var showPostOptionsMenu by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var editContentText by remember(post.content) { mutableStateOf(post.content) }
    var editHashtagsText by remember(post.hashtags) {
        mutableStateOf(post.hashtags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" })
    }

    val gpsCoords = remember(post.content) { com.sha.orbis.media.LocationGpsHelper.parseGpsPayload(post.content) }
    val isEmergencySos = remember(post.id, post.content, gpsCoords) {
        post.id.startsWith("sos_") || post.content.contains("ALERTE SOS") || gpsCoords != null
    }

    val cardBorderColor = when {
        isEmergencySos -> Color(0xFFEF4444) // Emergency Red
        post.isOfficialAnnouncement -> Color(0xFF38BDF8) // Light Blue
        post.isPinned -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val cardBgColor = when {
        isEmergencySos -> Color(0xFFEF4444).copy(alpha = 0.05f)
        post.isOfficialAnnouncement -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBgColor)
    ) {
            FeedPostContextRibbons(
                post = post,
                isEmergencySos = isEmergencySos,
                recommendationReason = recommendationReason,
                matchingTags = matchingTags
            )

            FeedPostHeaderIg(
                post = post,
                metaLine = metaLine,
                onAuthorClick = onAuthorClick,
                trailingActions = {
                    FeedPostHeaderActions(
                        post = post,
                        currentPhone = currentPhone,
                        showPostOptionsMenu = showPostOptionsMenu,
                        onShowPostOptionsMenuChange = { showPostOptionsMenu = it },
                        onRequestBlockConfirm = { showBlockConfirmDialog = true },
                        onEditClick = if (onEditPost != null) {
                            {
                                editContentText = post.content
                                editHashtagsText = post.hashtags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" }
                                showEditDialog = true
                            }
                        } else null,
                        onTogglePin = onTogglePin,
                        onDeletePost = onDeletePost
                    )
                }
            )

            val cleanContent = remember(post.content, gpsCoords) {
                if (gpsCoords != null) {
                    val prefix = com.sha.orbis.media.LocationGpsHelper.GPS_PREFIX
                    val suffix = com.sha.orbis.media.LocationGpsHelper.GPS_SUFFIX
                    val start = post.content.indexOf(prefix)
                    val end = post.content.indexOf(suffix, start)
                    if (start != -1 && end != -1) {
                        (post.content.substring(0, start) + post.content.substring(end + suffix.length)).trim()
                    } else post.content
                } else post.content
            }

            // Interactive Family SOS Live GPS Card
            if (isEmergencySos) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    FamilySosLocationCard(
                        coords = gpsCoords,
                        authorPhone = post.authorPhone,
                        authorName = post.authorName,
                        currentPhone = currentPhone
                    )
                }
            }

            // Attached Post Image
            val postImageSource = remember(post.mediaPath, post.mediaData) {
                // Check that the local file actually exists (deleted after reinstall)
                val pathValid = !post.mediaPath.isNullOrBlank() && java.io.File(post.mediaPath).exists()
                if (pathValid) post.mediaPath else post.mediaData?.takeIf { it.isNotBlank() }
            }

            var showFullScreenImage by remember { mutableStateOf(false) }

            if (showFullScreenImage && !postImageSource.isNullOrBlank()) {
                FullScreenImageViewerDialog(
                    imagePathOrBase64 = postImageSource,
                    title = post.content.take(40).ifBlank { post.authorName },
                    onDismiss = { showFullScreenImage = false }
                )
            }

            val isVideoPost = remember(post.mediaType, post.mediaPath) {
                post.mediaType == "video" || post.mediaPath?.endsWith(".mp4", ignoreCase = true) == true
            }

            var localVideoPath by remember(post.id, post.mediaPath) {
                mutableStateOf(post.mediaPath?.takeIf { java.io.File(it).exists() }
                    ?: com.sha.orbis.media.VideoMediaHelper.getVideoFile(context, post.id)?.absolutePath)
            }
            var isDownloadingVideo by remember(post.id) { mutableStateOf(false) }
            var videoDownloadProgress by remember(post.id) { mutableIntStateOf(0) }

            // Restauration automatique en arrière-plan si absent sur l'appareil du destinataire mais disponible en Base64
            androidx.compose.runtime.LaunchedEffect(post.id, post.mediaData) {
                if (isVideoPost && localVideoPath == null && !post.mediaData.isNullOrBlank()) {
                    isDownloadingVideo = true
                    val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val cleanPostId = post.id.filter { it.isLetterOrDigit() || it == '_' }.take(32).ifBlank { "post_${System.currentTimeMillis()}" }
                        val cleanBase64 = if (post.mediaData!!.contains(",")) post.mediaData!!.substringAfter(",") else post.mediaData!!
                        com.sha.orbis.media.VideoMediaHelper.base64ToVideoFile(context, cleanBase64.trim(), cleanPostId) { pct ->
                            videoDownloadProgress = pct
                        }
                    }
                    if (file != null && file.exists()) {
                        localVideoPath = file.absolutePath
                    }
                    isDownloadingVideo = false
                }
            }

            if (isVideoPost) {
                if (!localVideoPath.isNullOrBlank() && java.io.File(localVideoPath!!).exists()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    ) {
                        OrbisVideoPlayer(
                            videoPathOrId = localVideoPath!!,
                            modifier = Modifier.fillMaxSize(),
                            autoPlay = false,
                            showFullScreenButton = true
                        )
                    }
                } else if (isDownloadingVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { (videoDownloadProgress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.size(42.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                            Text(
                                text = stringResource(R.string.video_downloading_progress, videoDownloadProgress),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = stringResource(R.string.video_file_not_found),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (!postImageSource.isNullOrBlank()) {
                // Load bitmap asynchronously — base64 decode/decompress is CPU-intensive
                var postBitmap by remember(postImageSource) { mutableStateOf<android.graphics.Bitmap?>(null) }
                androidx.compose.runtime.LaunchedEffect(postImageSource) {
                    postBitmap = null
                    val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        MediaAttachmentHelper.loadBitmap(postImageSource)
                    }
                    postBitmap = loaded
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 380.dp)
                        .background(Color.Black.copy(alpha = 0.04f))
                        .pointerInput(post.id) {
                            detectTapGestures(
                                onTap = { showFullScreenImage = true },
                                onDoubleTap = {
                                    showHeartBurst = true
                                    onReact("❤️")
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = postBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = post.content.take(30),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 280.dp)
                        )
                        IconButton(
                            onClick = {
                                MediaDownloadManager.saveImageAsync(context, postImageSource, post.content.take(30))
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .size(34.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = stringResource(R.string.media_download_image),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    FeedDoubleTapHeartOverlay(
                        visible = showHeartBurst,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            // Interactive Website / Social Post Preview Card (TikTok, Instagram, Facebook, etc.)
            val detectedUrl = remember(post.content) {
                val regex = Regex("""https?://[^\s]+""")
                regex.find(post.content)?.value
            }
            if (!detectedUrl.isNullOrBlank()) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SocialLinkPreviewCard(
                        url = detectedUrl,
                        isMsgMine = false
                    )
                }
            }

            if (post.poll != null) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SocialPollView(
                        poll = post.poll,
                        currentPhone = currentPhone,
                        onVote = { optionId -> onVotePoll(optionId) }
                    )
                }
            }

            FeedActionBarIg(
                post = post,
                currentPhone = currentPhone,
                onQuickLike = { onReact("❤️") },
                onOpenReactionPalette = { showEmojiPalette = !showEmojiPalette },
                onCommentClick = onOpenComments,
                onRepost = onRepost,
                onShareToChat = onShareToChat,
                showEmojiPalette = showEmojiPalette,
                onEmojiSelected = { emoji -> onReact(emoji) },
                onDismissPalette = { showEmojiPalette = false }
            )

            FeedEngagementSummary(
                post = post,
                onOpenReactions = onShowReactions ?: { showEmojiPalette = true },
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )

            FeedCaptionBlock(
                authorName = post.authorName,
                content = cleanContent,
                hashtags = post.hashtags
            )

            FeedCommentPreview(
                comments = post.comments,
                onViewAllComments = onOpenComments,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            FeedPostDivider()
    }

    if (showBlockConfirmDialog) {
        FeedBlockUserConfirmDialog(
            authorName = post.authorName,
            onDismiss = { showBlockConfirmDialog = false },
            onConfirm = {
                val blockedRepo = com.sha.orbis.storage.BlockedContactsRepository(context)
                blockedRepo.blockContact(
                    phone = post.authorPhone,
                    name = post.authorName,
                    reason = context.getString(R.string.social_block_reason_feed)
                )
                Toast.makeText(
                    context,
                    context.getString(R.string.social_toast_user_blocked, post.authorName),
                    Toast.LENGTH_SHORT
                ).show()
                showBlockConfirmDialog = false
            }
        )
    }

    if (showEditDialog) {
        FeedEditPostDialog(
            contentText = editContentText,
            hashtagsText = editHashtagsText,
            onContentChange = { editContentText = it },
            onHashtagsChange = { editHashtagsText = it },
            onDismiss = { showEditDialog = false },
            onSave = {
                if (editContentText.isNotBlank()) {
                    val parsedTags = editHashtagsText
                        .split(" ", ",")
                        .map { it.trim().removePrefix("#") }
                        .filter { it.isNotBlank() }
                    onEditPost?.invoke(post.id, editContentText.trim(), parsedTags)
                    showEditDialog = false
                }
            }
        )
    }
}

@Composable
fun BlueVerifiedBadge(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(15.dp)
            .clip(CircleShape)
            .background(Color(0xFF1D9BF0)), // Official Facebook / Instagram / Twitter Blue
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = contentDescription ?: stringResource(R.string.social_badge_verified_official),
            tint = Color.White,
            modifier = Modifier.size(10.dp)
        )
    }
}

@Composable
internal fun SocialRoleBadge(
    role: UserSocialRole,
    isOfficialAnnouncement: Boolean = false
) {
    if (isOfficialAnnouncement || role == UserSocialRole.FOUNDER_DEV || role == UserSocialRole.VERIFIED_E2EE) {
        // Facebook / Instagram style Blue Verified Badge
        BlueVerifiedBadge()
    } else {
        // Standard users have NO badge next to their name (clean FB/Instagram standard)
    }
}

@Composable
private fun FamilySosLocationCard(
    coords: Pair<Double, Double>?,
    authorPhone: String,
    authorName: String,
    currentPhone: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isPostMine = currentPhone.isNotBlank() && com.sha.orbis.storage.FriendRequestRepository.isSamePhone(authorPhone, currentPhone)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEF4444).copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.family_sos_coordinates_label),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFFDC2626)
                )
            }

            if (coords != null) {
                Text(
                    text = "Lat: ${java.lang.String.format(java.util.Locale.US, "%.5f", coords.first)} • Lon: ${java.lang.String.format(java.util.Locale.US, "%.5f", coords.second)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            com.sha.orbis.media.LocationGpsHelper.openInMaps(context, coords.first, coords.second)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text(
                                text = stringResource(R.string.family_sos_btn_open_maps),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!isPostMine) {
                        Button(
                            onClick = {
                                com.sha.orbis.call.OrbisCallManager.startOutgoingCall(
                                    context = context,
                                    peerPhone = authorPhone,
                                    peerName = authorName,
                                    peerAvatar = null,
                                    myPhone = currentPhone,
                                    isVideoCall = false
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text(
                                    text = stringResource(R.string.family_sos_btn_emergency_call),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.family_sos_no_location_note),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isPostMine) {
                    Button(
                        onClick = {
                            com.sha.orbis.call.OrbisCallManager.startOutgoingCall(
                                context = context,
                                peerPhone = authorPhone,
                                peerName = authorName,
                                peerAvatar = null,
                                myPhone = currentPhone,
                                isVideoCall = false
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text(
                                text = stringResource(R.string.family_sos_btn_emergency_call),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialPollView(
    poll: SocialPoll,
    currentPhone: String,
    onVote: (optionId: String) -> Unit
) {
    val userVotedOptionId = poll.userVotedOptionId
        ?: poll.options.find { opt ->
            opt.votersPhones.any { com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it, currentPhone) }
        }?.id

    val hasVoted = userVotedOptionId != null
    val totalVotesCount = maxOf(poll.totalVotes, poll.options.sumOf { it.voteCount })

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = poll.question,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            poll.options.forEach { option ->
                val isSelected = userVotedOptionId == option.id
                val percentage = if (totalVotesCount > 0) (option.voteCount.toFloat() / totalVotesCount.toFloat()) else 0f
                val animatedProgress by animateFloatAsState(targetValue = if (hasVoted) percentage else 0f, label = "poll_bar")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !hasVoted) { onVote(option.id) }
                ) {
                    // Animated Fill Bar
                    if (hasVoted) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .height(44.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = option.text,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        if (hasVoted) {
                            Text(
                                text = "${(percentage * 100).toInt()}% (${option.voteCount})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            val voteText = if (totalVotesCount <= 1) "$totalVotesCount vote" else "$totalVotesCount votes"
            Text(
                text = if (hasVoted) "✓ Vote enregistré • $voteText" else "📊 Votez pour afficher les résultats • $voteText",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
