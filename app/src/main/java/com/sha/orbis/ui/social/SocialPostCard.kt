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
import androidx.compose.foundation.layout.aspectRatio
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
import com.sha.orbis.ui.social.feed.FeedDesignTokens
import com.sha.orbis.ui.social.feed.FeedPostHeaderActions
import com.sha.orbis.ui.social.feed.post.FeedPostCardIg
import com.sha.orbis.ui.social.feed.popups.FeedBlockUserConfirmDialog
import com.sha.orbis.ui.social.feed.popups.FeedEditPostDialog
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.sha.orbis.ui.theme.OrbisColorPalette
import com.sha.orbis.ui.conversation.SocialLinkPreviewCard
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val POST_URL_REGEX = Regex("""https?://[^\s]+""")

@Composable
fun SocialPostCard(
    post: SocialPost,
    currentPhone: String,
    onReact: (emoji: String) -> Unit,
    onVotePoll: (optionId: String) -> Unit,
    onOpenComments: () -> Unit,
    onDeletePost: (() -> Unit)? = null,
    onHidePost: (() -> Unit)? = null,
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

    val detectedUrl = remember(post.content) {
        val safeContent = if (post.content.length > 2048) post.content.take(2048) else post.content
        try { POST_URL_REGEX.find(safeContent)?.value } catch (_: Throwable) { null }
    }

    val cleanContent = remember(post.content, gpsCoords, detectedUrl) {
        var result = if (post.content.length > 4000) post.content.take(4000) else post.content
        // Strip GPS payload
        if (gpsCoords != null) {
            val prefix = com.sha.orbis.media.LocationGpsHelper.GPS_PREFIX
            val suffix = com.sha.orbis.media.LocationGpsHelper.GPS_SUFFIX
            val start = result.indexOf(prefix)
            val end = result.indexOf(suffix, start)
            if (start != -1 && end != -1) {
                result = (result.substring(0, start) + result.substring(end + suffix.length)).trim()
            }
        }
        // Strip URL when preview card will be displayed
        if (!detectedUrl.isNullOrBlank()) {
            result = result.replace(detectedUrl, "").trim()
        }
        result
    }

    val postImageSource = remember(post.mediaPath, post.mediaData) {
        val validLocalPath = post.mediaPath?.takeIf { path ->
            if (path.isBlank()) return@takeIf false
            try {
                val f = java.io.File(path)
                f.exists() && f.isFile && f.length() > 0L
            } catch (_: Exception) { false }
        }
        validLocalPath ?: post.mediaData?.takeIf { it.isNotBlank() }
    }
    val isVideoPost = remember(post.mediaType, post.mediaPath, post.mediaData, post.mediaUrl) {
        val validVideoFile = post.mediaPath?.takeIf { path ->
            try {
                val f = java.io.File(path)
                f.exists() && f.isFile && f.length() > 0L
            } catch (_: Exception) { false }
        }
        val hasVideoData = !post.mediaData.isNullOrBlank() || !post.mediaUrl.isNullOrBlank()
        (post.mediaType == "video" && (validVideoFile != null || hasVideoData)) ||
            (validVideoFile != null && post.mediaPath?.endsWith(".mp4", ignoreCase = true) == true)
    }
    val hasMedia = remember(isVideoPost, postImageSource) {
        isVideoPost || !postImageSource.isNullOrBlank()
    }

    FeedPostCardIg(
        post = post,
        currentPhone = currentPhone,
        metaLine = metaLine,
        cleanContent = cleanContent,
        isEmergencySos = isEmergencySos,
        hasMedia = hasMedia,
        onReact = onReact,
        onOpenComments = onOpenComments,
        onRepost = onRepost,
        onShareToChat = onShareToChat,
        onShowReactions = onShowReactions,
        onAuthorClick = onAuthorClick,
        recommendationReason = recommendationReason,
        matchingTags = matchingTags,
        headerActions = {
            FeedPostHeaderActions(
                post = post,
                currentPhone = currentPhone,
                showPostOptionsMenu = showPostOptionsMenu,
                onShowPostOptionsMenuChange = { showPostOptionsMenu = it },
                onRequestBlockConfirm = { showBlockConfirmDialog = true },
                onHidePost = onHidePost,
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
        },
        aboveMedia = {
            if (isEmergencySos) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = FeedDesignTokens.ContentPaddingHorizontal,
                        vertical = 4.dp
                    )
                ) {
                    FamilySosLocationCard(
                        coords = gpsCoords,
                        authorPhone = post.authorPhone,
                        authorName = post.authorName,
                        currentPhone = currentPhone
                    )
                }
            }
        },
        belowMedia = {
            // Interactive Website / Social Post Preview Card (TikTok, Instagram, Facebook, etc.)
            if (!detectedUrl.isNullOrBlank()) {
                Box(modifier = Modifier.padding(
                    horizontal = FeedDesignTokens.ContentPaddingHorizontal,
                    vertical = 4.dp
                )) {
                    SocialLinkPreviewCard(
                        url = detectedUrl,
                        isMsgMine = false
                    )
                }
            }

            if (post.poll != null) {
                Box(
                    modifier = Modifier.padding(
                        horizontal = FeedDesignTokens.ContentPaddingHorizontal,
                        vertical = 4.dp
                    )
                ) {
                    SocialPollView(
                        poll = post.poll,
                        currentPhone = currentPhone,
                        onVote = { optionId -> onVotePoll(optionId) }
                    )
                }
            }
        }
    )

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
