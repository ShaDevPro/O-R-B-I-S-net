package com.sha.orbis.ui.social

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.sha.orbis.social.FamilySosManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.data.SessionManager
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.SocialStory
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.social.algorithm.FeedRecommendationEngine
import com.sha.orbis.social.algorithm.RecommendationReason
import com.sha.orbis.social.algorithm.ScoredPost
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendCircleRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.storage.SocialRepository
import com.sha.orbis.storage.HiddenPostsRepository
import com.sha.orbis.ui.components.FriendProfilePreviewDialog
import com.sha.orbis.ui.components.FriendProfilePreviewDialogState
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.theme.OrbisColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

private data class FeedRefreshSnapshot(
    val posts: List<SocialPost>,
    val stories: List<SocialStory>,
    val requests: List<com.sha.orbis.model.FriendRequest>,
    val contacts: List<com.sha.orbis.model.Contact>,
    val activeComments: SocialPost?,
    val activeReactions: SocialPost?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    currentAccountId: String,
    targetPostId: String? = null,
    targetActionType: String? = null,
    onTargetPostHandled: (() -> Unit)? = null,
    onOpenWall: ((phone: String, pseudo: String, avatar: String?, role: UserSocialRole) -> Unit)? = null,
    onOpenChat: ((phone: String, name: String) -> Unit)? = null,
    triggerCreatePost: Boolean = false,
    onCreatePostTriggerHandled: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val socialRepo = remember(context, currentAccountId) { SocialRepository(context, currentAccountId) }
    val friendRequestRepo = remember(context) { FriendRequestRepository(context) }
    val circleRepo = remember(context) { FriendCircleRepository(context) }
    val convRepo = remember(context) { ConversationRepository(context) }
    val sessionManager = remember { SessionManager(context) }
    val recommendationEngine = remember(context, sessionManager) { FeedRecommendationEngine(context, sessionManager) }
    val hiddenPostsRepo = remember(context, currentAccountId) { HiddenPostsRepository(context, currentAccountId) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }
    var posts by remember { mutableStateOf(socialRepo.loadPosts()) }
    var stories by remember { mutableStateOf(socialRepo.loadStories()) }
    var friendRequestsSnapshot by remember { mutableStateOf(friendRequestRepo.loadRequests()) }
    var contactsSnapshot by remember { mutableStateOf(convRepo.loadContacts()) }
    val pendingRequestsCount = remember(friendRequestsSnapshot) { friendRequestRepo.getPendingReceived().size }

    // Algorithmic Feed Tabs State (0 = Pour Vous, 1 = Amis, 2 = Tendances, 3 = Mon Mur)
    var selectedFeedTabIndex by remember { mutableIntStateOf(0) }
    val feedTabs = listOf(
        stringResource(R.string.feed_tab_for_you) to Icons.Default.AutoAwesome,
        stringResource(R.string.feed_tab_following) to Icons.Default.People,
        stringResource(R.string.feed_tab_trending) to Icons.Default.LocalFireDepartment,
        stringResource(R.string.feed_tab_my_wall) to Icons.Default.Person
    )

    // Interest Discovery Chips Filter
    var selectedInterestCategory by remember { mutableStateOf("Tous") }
    val interestCategories = listOf(
        "Tous" to stringResource(R.string.feed_chip_all),
        "Tech" to stringResource(R.string.auth_chip_tech),
        "Crypto" to stringResource(R.string.auth_chip_crypto),
        "Art" to stringResource(R.string.auth_chip_design),
        "Business" to stringResource(R.string.auth_chip_business),
        "Sport" to stringResource(R.string.auth_chip_sport),
        "Music" to stringResource(R.string.auth_chip_music),
        "Science" to stringResource(R.string.auth_chip_science),
        "Nature" to stringResource(R.string.auth_chip_nature)
    )

    // Dialog & Interaction States
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var postPresetCircleId by remember { mutableStateOf<String?>(null) }
    var activePostForComments by remember { mutableStateOf<SocialPost?>(null) }
    var activePostForReactions by remember { mutableStateOf<SocialPost?>(null) }
    var postToDelete by remember { mutableStateOf<SocialPost?>(null) }
    var postToShareInChat by remember { mutableStateOf<SocialPost?>(null) }
    var showFriendRequestsScreen by remember { mutableStateOf(false) }

    var selectedFilterCircleId by remember { mutableStateOf<String?>(null) } // null = All
    var selectedHashtagFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeProfilePreview by remember { mutableStateOf<FriendProfilePreviewDialogState?>(null) }

    val listState = rememberLazyListState()
    var highlightedPostId by remember { mutableStateOf<String?>(null) }

    fun refreshFeed() {
        scope.launch {
            // Ne jamais perturber le défilement fluide : attendre l'arrêt du geste tactile
            while (listState.isScrollInProgress) {
                kotlinx.coroutines.delay(150)
            }
            val currentPhone = sessionManager.userPhone
            val commentsTargetId = activePostForComments?.id
            val reactionsTargetId = activePostForReactions?.id

            val snapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val updatedPosts = socialRepo.loadPosts()
                val requests = friendRequestRepo.loadRequests()
                val contacts = convRepo.loadContacts()
                val friendRepo = com.sha.orbis.storage.FriendRequestRepository(context)
                val blockedRepo = com.sha.orbis.storage.BlockedContactsRepository(context)
                val fPosts = updatedPosts.filterNot { blockedRepo.isBlocked(it.authorPhone) || hiddenPostsRepo.isHidden(it.id) }
                val fStories = socialRepo.loadStories().filter { story ->
                    !blockedRepo.isBlocked(story.authorPhone) && (
                        com.sha.orbis.storage.FriendRequestRepository.isSamePhone(story.authorPhone, currentPhone) ||
                        friendRepo.isFriend(story.authorPhone)
                    )
                }
                val cPost = commentsTargetId?.let { socialRepo.findPostById(it) }
                val rPost = reactionsTargetId?.let { socialRepo.findPostById(it) }
                FeedRefreshSnapshot(
                    posts = fPosts,
                    stories = fStories,
                    requests = requests,
                    contacts = contacts,
                    activeComments = cPost,
                    activeReactions = rPost
                )
            }

            // Attendre à nouveau si l'utilisateur a initié un nouveau scroll pendant le chargement IO
            while (listState.isScrollInProgress) {
                kotlinx.coroutines.delay(150)
            }

            posts = snapshot.posts
            stories = snapshot.stories
            friendRequestsSnapshot = snapshot.requests
            contactsSnapshot = snapshot.contacts
            if (activePostForComments != null && snapshot.activeComments != null) {
                activePostForComments = snapshot.activeComments
            }
            if (activePostForReactions != null && snapshot.activeReactions != null) {
                activePostForReactions = snapshot.activeReactions
            }
            com.sha.orbis.call.diagnostic.FeedDebugTracker.logEvent("REFRESH", "refreshFeed: ${snapshot.posts.size} posts chargés, ${snapshot.stories.size} stories")
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it }
        if (isGranted) {
            FamilySosManager.dispatchFamilyEmergency(
                context = context,
                sessionManager = sessionManager,
                circleRepo = circleRepo,
                socialRepo = socialRepo,
                onRequestLocationPermission = {},
                onNeedEnableGps = {
                    Toast.makeText(context, context.getString(R.string.family_sos_gps_disabled_prompt), Toast.LENGTH_LONG).show()
                    FamilySosManager.openLocationSettings(context)
                },
                onAlertDispatched = { refreshFeed() }
            )
        } else {
            Toast.makeText(context, context.getString(R.string.family_sos_gps_error_no_permission), Toast.LENGTH_SHORT).show()
            FamilySosManager.dispatchEmergencyWithoutGps(
                context = context,
                sessionManager = sessionManager,
                socialRepo = socialRepo,
                onAlertDispatched = { refreshFeed() }
            )
        }
    }

    // Diagnostic Watchdog & Traqueur de Navigation Feed
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.sha.orbis.call.diagnostic.FeedDebugTracker.onFeedEntered(context, posts.size)
        posts.forEachIndexed { i, p ->
            com.sha.orbis.call.diagnostic.FeedDebugTracker.logEvent(
                "POST_DETAIL",
                "Post #$i: id=${p.id}, author=${p.authorName}, mediaType=${p.mediaType}, mediaPath=${p.mediaPath?.take(30)}, hasData=${!p.mediaData.isNullOrBlank()}, contentLen=${p.content.length}, poll=${p.poll != null}"
            )
        }
        onDispose {
            com.sha.orbis.call.diagnostic.FeedDebugTracker.onFeedExited(context)
        }
    }

    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress)
        }.collect { (index, offset, inProgress) ->
            com.sha.orbis.call.diagnostic.FeedDebugTracker.onScroll(
                firstIndex = index,
                firstOffset = offset,
                isScrollInProgress = inProgress
            )
        }
    }

    LaunchedEffect(Unit) {
        com.sha.orbis.data.OrbisBadgeHub.markSocialRead(context)
        // Rapatrier activement les dernières publications des amis sur les relais Nostr
        try {
            com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).fetchFreshTimeline()
            com.sha.orbis.telemetry.FeedTelemetryTracker.trackPostViewed(context, posts.size)
        } catch (_: Exception) {}
    }

    // External trigger from the central '+' button in the bottom bar
    LaunchedEffect(triggerCreatePost) {
        if (triggerCreatePost) {
            showCreatePostDialog = true
            onCreatePostTriggerHandled?.invoke()
        }
    }

    var debounceRefreshJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun scheduleDebouncedFeedRefresh() {
        debounceRefreshJob?.cancel()
        debounceRefreshJob = scope.launch {
            // Ne jamais perturber le défilement fluide : attendre l'arrêt du geste tactile
            while (listState.isScrollInProgress) {
                kotlinx.coroutines.delay(200)
            }
            kotlinx.coroutines.delay(500)
            refreshFeed()
        }
    }

    // Live receiver for real-time feed updates when receiving P2P posts
    androidx.compose.runtime.DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                scheduleDebouncedFeedRefresh()
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(com.sha.orbis.notification.OrbisEventBus.ACTION_ORBIS_POST_RECEIVED)
            addAction(com.sha.orbis.notification.OrbisEventBus.ACTION_ORBIS_STORY_RECEIVED)
            addAction(com.sha.orbis.notification.OrbisEventBus.ACTION_ORBIS_REACTION_RECEIVED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    if (postToShareInChat != null) {
        SharePostToChatDialog(
            post = postToShareInChat!!,
            onDismiss = { postToShareInChat = null }
        )
    }

    if (activeProfilePreview != null) {
        FriendProfilePreviewDialog(
            userPhone = activeProfilePreview!!.phone,
            pseudo = activeProfilePreview!!.pseudo,
            avatarPath = activeProfilePreview!!.avatarPath,
            role = activeProfilePreview!!.role,
            onDismiss = { activeProfilePreview = null },
            onOpenChat = onOpenChat,
            onOpenWall = { phone, pseudo, avatar, role ->
                activeProfilePreview = null
                onOpenWall?.invoke(phone, pseudo, avatar, role)
            }
        )
    }

    if (showFriendRequestsScreen) {
        FriendRequestsScreen(
            onBack = {
                showFriendRequestsScreen = false
                refreshFeed()
            }
        )
        return
    }

    // Delete Post Confirmation Dialog
    if (postToDelete != null) {
        val p = postToDelete!!
        val isMine = com.sha.orbis.storage.FriendRequestRepository.isSamePhone(p.authorPhone, sessionManager.userPhone) ||
            com.sha.orbis.admin.AdminSecurityHelper.isAdmin(sessionManager.userPhone)
        com.sha.orbis.ui.social.feed.popups.FeedDeletePostConfirmDialog(
            isAuthorOrAdmin = isMine,
            onDismiss = { postToDelete = null },
            onConfirm = {
                socialRepo.deletePost(p.id)
                refreshFeed()
                if (isMine) {
                    try {
                        val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                        nostrSync.publishDeletePost(p.id)
                    } catch (e: Exception) {
                        android.util.Log.w("TimelineScreen", "Erreur suppression Nostr: ${e.message}")
                    }
                }
                Toast.makeText(context, context.getString(R.string.social_toast_post_deleted), Toast.LENGTH_SHORT).show()
                postToDelete = null
            }
        )
    }

    if (showCreatePostDialog) {
        CreatePostDialog(
            authorPhone = sessionManager.userPhone,
            authorName = sessionManager.userName.ifBlank { "Moi" },
            authorAvatarPath = sessionManager.userAvatarPath,
            initialCircleId = postPresetCircleId,
            onDismiss = {
                showCreatePostDialog = false
                postPresetCircleId = null
            },
            onPostCreated = { content, hashtags, poll, circleId, excludedCircleIds, excludedPhones, isOfficial, isPinned, role, mediaType, mediaPath, mediaData, mediaUrl ->
                val normalizedAuthorPhone = com.sha.orbis.data.ContactsPickerHelper.normalizePhoneNumber(sessionManager.userPhone)
                val newPost = SocialPost(
                    id = "post_${UUID.randomUUID().toString().take(8)}",
                    authorPhone = normalizedAuthorPhone,
                    authorName = sessionManager.userName.ifBlank { "Moi" },
                    authorAvatarPath = sessionManager.userAvatarPath,
                    content = content,
                    hashtags = hashtags,
                    timestamp = System.currentTimeMillis(),
                    targetCircleId = circleId,
                    excludedCircleIds = excludedCircleIds,
                    excludedPhones = excludedPhones,
                    rsaSignature = "sig_rsa_valid",
                    poll = poll,
                    reactions = emptyList(),
                    comments = emptyList(),
                    isPinned = isPinned,
                    isOfficialAnnouncement = isOfficial,
                    authorRole = role,
                    mediaType = mediaType,
                    mediaPath = mediaPath,
                    mediaData = mediaData,
                    mediaUrl = mediaUrl
                )
                socialRepo.addPost(newPost)
                showCreatePostDialog = false
                postPresetCircleId = null
                refreshFeed()

                // Broadcast to Decentralized Nostr Relays (Kind 1)
                try {
                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                    nostrSync.publishPost(newPost)
                } catch (e: Exception) {
                    android.util.Log.w("TimelineScreen", "Erreur diffusion Nostr: ${e.message}")
                }

                Toast.makeText(context, context.getString(R.string.social_toast_post_broadcast), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (activePostForReactions != null) {
        val reactionsPost = activePostForReactions!!
        ReactionsDetailSheet(
            reactions = reactionsPost.reactions,
            currentPhone = sessionManager.userPhone,
            onDismiss = { activePostForReactions = null }
        )
    }

    if (activePostForComments != null) {
        val targetPost = activePostForComments!!
        CommentsBottomSheet(
            post = targetPost,
            currentPhone = sessionManager.userPhone,
            currentUserName = sessionManager.userName.ifBlank { "Moi" },
            currentUserAvatarPath = sessionManager.userAvatarPath,
            onDismiss = { activePostForComments = null },
            onAddComment = { text, replyToId, replyToName ->
                val comment = SocialComment(
                    id = "c_${UUID.randomUUID().toString().take(6)}",
                    postId = targetPost.id,
                    authorPhone = sessionManager.userPhone,
                    authorName = sessionManager.userName.ifBlank { "Moi" },
                    authorAvatarPath = sessionManager.userAvatarPath,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    replyToCommentId = replyToId,
                    replyToAuthorName = replyToName
                )
                socialRepo.addComment(targetPost.id, comment)
                refreshFeed()
                activePostForComments = socialRepo.findPostById(targetPost.id)

                // Broadcast Comment via Nostr (Kind 1 with 'e' tag)
                try {
                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                    val authorTargetKey = targetPost.authorPubkey ?: targetPost.authorPhone
                    nostrSync.publishComment(targetPost.id, authorTargetKey, comment)
                } catch (e: Exception) {
                    android.util.Log.w("TimelineScreen", "Erreur diffusion commentaire Nostr: ${e.message}")
                }

                Toast.makeText(context, context.getString(R.string.social_toast_comment_broadcast), Toast.LENGTH_SHORT).show()
            },
            onDeleteComment = { commentId ->
                val postId = targetPost.id
                socialRepo.deleteComment(postId, commentId)
                refreshFeed()
                activePostForComments = socialRepo.findPostById(postId)

                // Broadcast Delete Comment via Nostr (Kind 5 NIP-09)
                try {
                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                    nostrSync.publishDeleteComment(postId, commentId)
                } catch (e: Exception) {
                    android.util.Log.w("TimelineScreen", "Erreur suppression commentaire Nostr: ${e.message}")
                }

                Toast.makeText(context, context.getString(R.string.social_toast_comment_deleted), Toast.LENGTH_SHORT).show()
            },
            onEditComment = { commentId, newText ->
                val postId = targetPost.id
                socialRepo.editComment(postId, commentId, newText)
                refreshFeed()
                activePostForComments = socialRepo.findPostById(postId)

                try {
                    val updatedComment = activePostForComments?.comments?.firstOrNull { it.id == commentId }
                    if (updatedComment != null) {
                        val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                        val authorTargetKey = targetPost.authorPubkey ?: targetPost.authorPhone
                        nostrSync.publishComment(postId, authorTargetKey, updatedComment)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("TimelineScreen", "Erreur modification commentaire Nostr: ${e.message}")
                }

                Toast.makeText(context, context.getString(R.string.social_toast_comment_edited), Toast.LENGTH_SHORT).show()
            },
            onReactComment = { commentId, emoji ->
                val postId = targetPost.id
                socialRepo.addCommentReaction(postId, commentId, sessionManager.userPhone, emoji)
                refreshFeed()
                activePostForComments = socialRepo.findPostById(postId)

                // Broadcast Comment Reaction via Nostr (Kind 7 with comment_id tag)
                try {
                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                    val authorTargetKey = targetPost.authorPubkey ?: targetPost.authorPhone
                    nostrSync.publishCommentReaction(postId, commentId, authorTargetKey, emoji)
                } catch (e: Exception) {
                    android.util.Log.w("TimelineScreen", "Erreur diffusion reaction-commentaire Nostr: ${e.message}")
                }
            },
            onUserClick = { phone, name, avatar ->
                activeProfilePreview = FriendProfilePreviewDialogState(
                    phone = phone,
                    pseudo = name,
                    avatarPath = avatar,
                    role = com.sha.orbis.admin.AdminSecurityHelper.getUserSocialRole(phone, context)
                )
            }
        )
    }

    // 1. Initial Filtering by Friendship, Circle, Hashtag & Search Query
    val baseFilteredPosts = remember(
        posts,
        selectedFilterCircleId,
        selectedHashtagFilter,
        searchQuery,
        selectedInterestCategory,
        friendRequestsSnapshot,
        contactsSnapshot
    ) {
        val filterStartTime = System.currentTimeMillis()
        val friendRepo = com.sha.orbis.storage.FriendRequestRepository(context)
        val currentPhone = sessionManager.userPhone
        val familyMembers = circleRepo.getFamilyMembers()
        val isCurrentPhoneInFamily = familyMembers.any { com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it, currentPhone) }

        val acceptedFriendPhones = friendRequestsSnapshot
            .filter { it.status == com.sha.orbis.model.FriendRequestStatus.ACCEPTED }
            .map { it.senderPhone.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        var list = posts.filter { post ->
            val isAuthorSelf = com.sha.orbis.storage.FriendRequestRepository.isSamePhone(post.authorPhone, currentPhone)

            // Respect author's selective exclusions (Family Circle & specific contacts)
            if (!isAuthorSelf) {
                if (post.excludedPhones.any { com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it, currentPhone) }) {
                    return@filter false
                }
                if (post.excludedCircleIds.contains("circle_family")) {
                    val isFamily = familyMembers.any { com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it, post.authorPhone) } || isCurrentPhoneInFamily
                    if (isFamily) {
                        return@filter false
                    }
                }
            }

            val isOfficial = post.isOfficialAnnouncement
            val isAuthorFriend = acceptedFriendPhones.any { com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it, post.authorPhone) } ||
                    friendRepo.isFriend(post.authorPhone)

            val isAllowedAuthor = isAuthorSelf || isOfficial || isAuthorFriend

            val matchCircle = when (selectedFilterCircleId) {
                null -> true
                else -> post.targetCircleId == selectedFilterCircleId
            }
            val matchHashtag = when (selectedHashtagFilter) {
                null -> true
                else -> post.hashtags.contains(selectedHashtagFilter)
            }
            val matchSearch = if (searchQuery.isBlank()) true else {
                post.content.contains(searchQuery, ignoreCase = true) ||
                        post.authorName.contains(searchQuery, ignoreCase = true) ||
                        post.hashtags.any { it.contains(searchQuery, ignoreCase = true) }
            }
            isAllowedAuthor && matchCircle && matchHashtag && matchSearch
        }

        // Apply Interest Category Filter
        if (selectedInterestCategory != "Tous") {
            list = recommendationEngine.filterByInterestCategory(list, selectedInterestCategory)
        }
        val filterDuration = System.currentTimeMillis() - filterStartTime
        com.sha.orbis.call.diagnostic.FeedDebugTracker.logFilterResult(filterDuration, posts.size, list.size)
        list
    }

    // 2. Algorithmic Ranking according to selected tab (Pour Vous / Amis / Tendances)
    val rankedPosts: List<ScoredPost> = remember(baseFilteredPosts, selectedFeedTabIndex) {
        val rankStartTime = System.currentTimeMillis()
        val rawScored = when (selectedFeedTabIndex) {
            0 -> recommendationEngine.rankForYouFeed(baseFilteredPosts)
            1 -> baseFilteredPosts.sortedByDescending { it.timestamp }.map {
                ScoredPost(it, 0.0, RecommendationReason.CHRONOLOGICAL, recommendationEngine.extractCategories(it))
            }
            2 -> recommendationEngine.rankTrendingFeed(baseFilteredPosts)
            3 -> baseFilteredPosts.filter { com.sha.orbis.storage.FriendRequestRepository.isSamePhone(it.authorPhone, sessionManager.userPhone) }.map {
                ScoredPost(it, 0.0, RecommendationReason.CHRONOLOGICAL, recommendationEngine.extractCategories(it))
            }
            else -> recommendationEngine.rankForYouFeed(baseFilteredPosts)
        }
        // Pinned posts & Official announcements are guaranteed to float to the top
        val sortedList = rawScored.sortedWith(
            compareByDescending<ScoredPost> { it.post.isOfficialAnnouncement }
                .thenByDescending { it.post.isPinned }
                .thenByDescending { it.finalScore }
                .thenByDescending { it.post.timestamp }
        ).filter { it.post.id.isNotBlank() }.distinctBy { it.post.id.trim() }
        val rankDuration = System.currentTimeMillis() - rankStartTime
        com.sha.orbis.call.diagnostic.FeedDebugTracker.logRankingResult(selectedFeedTabIndex, rankDuration, sortedList.size)
        sortedList
    }

    // Préchargement asynchrone non-bloquant des métadonnées de liens pour le fil
    LaunchedEffect(rankedPosts) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            for (scored in rankedPosts.take(20)) {
                val url = com.sha.orbis.media.LinkPreviewHelper.extractFirstUrl(scored.post.content)
                if (!url.isNullOrBlank()) {
                    com.sha.orbis.media.LinkPreviewHelper.prefetch(context, url)
                }
            }
        }
    }

    // Direct routing to notification source (post, likes & comments)
    LaunchedEffect(targetPostId, posts) {
        if (!targetPostId.isNullOrBlank()) {
            val matchedPost = posts.firstOrNull {
                it.id.equals(targetPostId, ignoreCase = true) ||
                com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(targetPostId, ignoreCase = true) ||
                (targetPostId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(targetPostId, ignoreCase = true))
            }
            if (matchedPost != null) {
                if (targetActionType == "COMMENT" || targetActionType == "LIKE") {
                    activePostForComments = matchedPost
                }
                selectedInterestCategory = "Tous"
                searchQuery = ""
                selectedFilterCircleId = null
                selectedHashtagFilter = null
                highlightedPostId = matchedPost.id

                val postIndex = rankedPosts.indexOfFirst { it.post.id == matchedPost.id }
                if (postIndex >= 0) {
                    listState.animateScrollToItem((4 + postIndex).coerceAtLeast(0))
                }
                onTargetPostHandled?.invoke()
            }
        }
    }

    LaunchedEffect(highlightedPostId) {
        val targetId = highlightedPostId
        if (targetId != null) {
            val postIndex = rankedPosts.indexOfFirst { it.post.id == targetId }
            if (postIndex >= 0) {
                listState.animateScrollToItem((4 + postIndex).coerceAtLeast(0))
            }
            kotlinx.coroutines.delay(4500)
            highlightedPostId = null
        }
    }

    Scaffold {  paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            com.sha.orbis.ui.social.feed.FeedTimelineHeader(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                pendingFriendRequestsCount = pendingRequestsCount,
                onOpenFriendRequests = { showFriendRequestsScreen = true },
                onRefreshFeed = { refreshFeed() },
                onFetchNostrTimeline = {
                    com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).fetchFreshTimeline()
                    kotlinx.coroutines.delay(1200)
                    refreshFeed()
                },
                selectedFeedTabIndex = selectedFeedTabIndex,
                feedTabs = feedTabs,
                onFeedTabSelected = { selectedFeedTabIndex = it },
                onOpenMyWallShortcut = {
                    val myRole = com.sha.orbis.admin.AdminSecurityHelper.getUserSocialRole(
                        sessionManager.userPhone,
                        context
                    )
                    onOpenWall?.invoke(
                        sessionManager.userPhone,
                        sessionManager.userName,
                        sessionManager.userAvatarPath,
                        myRole
                    )
                }
            )

            // Feed stream — Swipe-to-refresh
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        try {
                            com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).fetchFreshTimeline()
                            kotlinx.coroutines.delay(1200)
                        } catch (e: Exception) {
                            android.util.Log.w("TimelineScreen", "Swipe refresh Nostr error: ${e.message}")
                        } finally {
                            refreshFeed()
                            isRefreshing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Family Circle Hub Card (Prominent, intuitive & reassuring for non-tech users)
                item(key = "timeline_family_hub") {
                    com.sha.orbis.ui.family.FamilyCircleHubCard(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        onOpenFamilyChat = {
                            val familyPhones = circleRepo.getFamilyMembers()
                            if (familyPhones.isNotEmpty()) {
                                val conv = com.sha.orbis.model.Conversation(
                                    id = "group_family_circle",
                                    title = "👨‍👩‍👧‍👦 " + context.getString(R.string.family_hub_title),
                                    participants = (listOf(sessionManager.userPhone) + familyPhones).distinct(),
                                    lastMessage = "",
                                    updatedAt = System.currentTimeMillis(),
                                    isGroup = true
                                )
                                val convRepo = com.sha.orbis.storage.ConversationRepository(context)
                                val existing = convRepo.loadConversations().toMutableList()
                                val idx = existing.indexOfFirst { it.id == conv.id }
                                if (idx >= 0) {
                                    existing[idx] = existing[idx].copy(
                                        title = conv.title,
                                        participants = conv.participants,
                                        isGroup = true
                                    )
                                } else {
                                    existing.add(0, conv)
                                }
                                convRepo.saveConversations(existing)
                                onOpenChat?.invoke(conv.id, conv.title)
                            } else {
                                Toast.makeText(context, context.getString(R.string.family_hub_empty_desc), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenDirectChat = { phone, name ->
                            onOpenChat?.invoke(phone, name)
                        },
                        onShareFamilyMemory = {
                            postPresetCircleId = "circle_family"
                            showCreatePostDialog = true
                        },
                        onSendFamilyEmergency = {
                            FamilySosManager.dispatchFamilyEmergency(
                                context = context,
                                sessionManager = sessionManager,
                                circleRepo = circleRepo,
                                socialRepo = socialRepo,
                                onRequestLocationPermission = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                onNeedEnableGps = {
                                    Toast.makeText(context, context.getString(R.string.family_sos_gps_disabled_prompt), Toast.LENGTH_LONG).show()
                                    FamilySosManager.openLocationSettings(context)
                                },
                                onAlertDispatched = {
                                    refreshFeed()
                                }
                            )
                        },
                        onFamilyCall = { phone, name, isVideo ->
                            com.sha.orbis.call.OrbisCallManager.startOutgoingCall(
                                context = context,
                                peerPhone = phone,
                                peerName = name,
                                peerAvatar = null,
                                myPhone = sessionManager.userPhone,
                                isVideoCall = isVideo
                            )
                        }
                    )
                }

                item(key = "timeline_composer") {
                    com.sha.orbis.ui.social.feed.FeedHeroComposerCard(
                        userName = sessionManager.userName,
                        userAvatarPath = sessionManager.userAvatarPath,
                        onOpenMyWall = {
                            val myRole = com.sha.orbis.admin.AdminSecurityHelper.getUserSocialRole(sessionManager.userPhone, context)
                            onOpenWall?.invoke(sessionManager.userPhone, sessionManager.userName, sessionManager.userAvatarPath, myRole)
                        },
                        onCreatePost = { showCreatePostDialog = true }
                    )
                }

                item(key = "timeline_interests") {
                    com.sha.orbis.ui.social.feed.FeedInterestChipRow(
                        categories = interestCategories,
                        selectedCategoryKey = selectedInterestCategory,
                        onCategorySelected = { selectedInterestCategory = it }
                    )
                }

                // Stories Bar (24h)
                item(key = "timeline_stories") {
                    com.sha.orbis.ui.social.feed.FeedStoriesStrip {
                        com.sha.orbis.ui.social.feed.FeedSectionTitle(
                            title = stringResource(R.string.social_stories_title)
                        )
                        SocialStoriesBar(
                            stories = stories,
                            userAvatarPath = sessionManager.userAvatarPath,
                            userName = sessionManager.userName.ifBlank { "Moi" },
                            currentPhone = sessionManager.userPhone,
                            onAddStory = { content, mediaPath, mediaBase64, gradientIndex, mediaType, mediaUrl, targetCircleId, excludedCircleIds, excludedPhones ->
                                val story = SocialStory(
                                    id = "story_${UUID.randomUUID().toString().take(8)}",
                                    authorPhone = sessionManager.userPhone,
                                    authorName = sessionManager.userName.ifBlank { "Moi" },
                                    authorAvatarPath = sessionManager.userAvatarPath,
                                    content = content,
                                    mediaType = mediaType,
                                    mediaPath = mediaPath,
                                    mediaBase64 = mediaBase64,
                                    mediaUrl = mediaUrl,
                                    backgroundGradientIndex = gradientIndex,
                                    createdAt = System.currentTimeMillis(),
                                    expiresAt = System.currentTimeMillis() + 86_400_000L,
                                    targetCircleId = targetCircleId,
                                    excludedCircleIds = excludedCircleIds,
                                    excludedPhones = excludedPhones
                                )
                                socialRepo.addStory(story)
                                try {
                                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                    nostrSync.publishStory(story)
                                } catch (e: Exception) {
                                    android.util.Log.w("TimelineScreen", "Erreur diffusion story Nostr: ${e.message}")
                                }
                                refreshFeed()
                                Toast.makeText(context, context.getString(R.string.social_story_created), Toast.LENGTH_SHORT).show()
                            },
                            onDeleteStory = { storyId ->
                                socialRepo.deleteStory(storyId)
                                try {
                                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                    nostrSync.publishDeleteStory(storyId)
                                } catch (e: Exception) {
                                    android.util.Log.w("TimelineScreen", "Erreur diffusion suppression story Nostr: ${e.message}")
                                }
                                refreshFeed()
                                Toast.makeText(context, context.getString(R.string.social_toast_story_deleted), Toast.LENGTH_SHORT).show()
                            },
                            onStorySeen = { story ->
                                socialRepo.markStorySeen(story.id, sessionManager.userPhone)
                                refreshFeed()
                            }
                        )
                        com.sha.orbis.ui.social.feed.FeedSectionDivider(
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // Posts Stream
                if (rankedPosts.isEmpty()) {
                    item(key = "timeline_empty") {
                        com.sha.orbis.ui.social.feed.FeedEmptyTimelineState()
                    }
                } else {
                    items(
                        items = rankedPosts,
                        key = { it.post.id },
                        contentType = { "social_post" }
                    ) { scoredItem ->
                        val post = scoredItem.post
                        val isHighlighted = highlightedPostId == post.id
                        val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    if (isHighlighted) {
                                        drawRect(highlightColor)
                                    }
                                }
                        ) {
                            SocialPostCard(
                                post = post,
                                currentPhone = sessionManager.userPhone,
                                recommendationReason = scoredItem.reason,
                                matchingTags = scoredItem.matchingTags,
                                onReact = { emoji ->
                                    socialRepo.addReaction(post.id, sessionManager.userPhone, emoji)
                                    recommendationEngine.recordInteraction(post, 2.5f)
                                    refreshFeed()

                                    try {
                                        val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                        val authorTargetKey = post.authorPubkey ?: post.authorPhone
                                        nostrSync.publishReaction(post.id, authorTargetKey, emoji)
                                    } catch (e: Exception) {
                                        android.util.Log.w("TimelineScreen", "Erreur like Nostr: ${e.message}")
                                    }

                                    Toast.makeText(context, context.getString(R.string.social_toast_reaction_sent), Toast.LENGTH_SHORT).show()
                                },
                                onVotePoll = { optionId ->
                                    val voteApplied = socialRepo.votePoll(post.id, optionId, sessionManager.userPhone, isLocalUser = true)
                                    if (voteApplied) {
                                        recommendationEngine.recordInteraction(post, 3.0f)
                                        refreshFeed()
                                        try {
                                            val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                            val targetAuthorKey = post.authorPubkey ?: post.authorPhone
                                            nostrSync.publishPollVote(post.id, optionId, targetAuthorKey)
                                        } catch (e: Exception) {
                                            android.util.Log.w("TimelineScreen", "Erreur diffusion vote Nostr: ${e.message}")
                                        }
                                        Toast.makeText(context, context.getString(R.string.social_toast_vote_broadcast), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOpenComments = {
                                        activePostForComments = post
                                },
                                onDeletePost = {
                                    postToDelete = post
                                },
                                onHidePost = {
                                    hiddenPostsRepo.hidePost(post)
                                    refreshFeed()
                                },
                                onEditPost = { postId, newContent, newHashtags ->
                                    socialRepo.editPost(postId, newContent, newHashtags)
                                    refreshFeed()
                                    try {
                                        val updatedPost = socialRepo.findPostById(postId)
                                        if (updatedPost != null) {
                                            val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                            nostrSync.publishPost(updatedPost)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("TimelineScreen", "Erreur edit post Nostr: ${e.message}")
                                    }
                                    Toast.makeText(context, context.getString(R.string.social_toast_post_edited), Toast.LENGTH_SHORT).show()
                                },
                                onTogglePin = {
                                    socialRepo.togglePinPost(post.id)
                                    refreshFeed()
                                    val willBePinned = !post.isPinned
                                    Toast.makeText(
                                        context,
                                        if (willBePinned) context.getString(R.string.social_toast_post_pinned) else context.getString(R.string.social_toast_post_unpinned),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onRepost = {
                                    val repostedItem = socialRepo.repost(
                                        originalPost = post,
                                        currentUserPhone = sessionManager.userPhone,
                                        currentUserName = sessionManager.userName,
                                        currentUserAvatar = sessionManager.userAvatarPath
                                    )
                                    recommendationEngine.recordInteraction(post, 4.0f)
                                    refreshFeed()

                                    // Broadcast repost via Nostr Relays
                                    try {
                                        val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                        nostrSync.publishPost(repostedItem)
                                    } catch (e: Exception) {
                                        android.util.Log.w("TimelineScreen", "Erreur repost Nostr: ${e.message}")
                                    }

                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.social_repost_success),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onShareToChat = {
                                    postToShareInChat = post
                                },
                                onShowReactions = {
                                    activePostForReactions = socialRepo.findPostById(post.id) ?: post
                                },
                                onAuthorClick = {
                                    activeProfilePreview = FriendProfilePreviewDialogState(
                                        phone = post.authorPhone,
                                        pseudo = post.authorName,
                                        avatarPath = post.authorAvatarPath,
                                        role = post.authorRole
                                    )
                                }
                            )
                        }
                    }
                }
            } // end LazyColumn
            } // end PullToRefreshBox
        }
    }
}
