package com.sha.orbis.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.data.ContactsPickerHelper
import com.sha.orbis.data.SessionManager
import kotlinx.coroutines.launch
import com.sha.orbis.model.Contact
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendCircleRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.storage.SocialRepository
import com.sha.orbis.ui.components.AvatarManager
import com.sha.orbis.social.SocialFriendsCatalog
import com.sha.orbis.ui.profile.wall.WallEditProfileDialog
import com.sha.orbis.ui.profile.wall.WallFriendsHubCard
import com.sha.orbis.ui.profile.wall.WallHeaderSection
import com.sha.orbis.ui.profile.wall.WallStatsRow
import com.sha.orbis.ui.profile.wall.WallTabs
import com.sha.orbis.ui.social.FriendRequestsScreen
import com.sha.orbis.ui.social.friends.MyFriendsListScreen
import com.sha.orbis.ui.profile.wall.wallAboutTab
import com.sha.orbis.ui.profile.wall.wallPostsTab
import com.sha.orbis.ui.social.CommentsBottomSheet
import com.sha.orbis.ui.social.CreatePostDialog
import com.sha.orbis.ui.social.SharePostToChatDialog
import com.sha.orbis.ui.social.ReactionsDetailSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileWallScreen(
    userPhone: String,
    initialPseudo: String,
    initialAvatarPath: String? = null,
    initialRole: UserSocialRole = UserSocialRole.STANDARD,
    onBack: () -> Unit,
    onOpenChat: ((phone: String, name: String) -> Unit)? = null,
    onNavigateToPeerWall: ((phone: String, pseudo: String, avatar: String?, role: UserSocialRole) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val activeAccountId = remember { SessionManager(context).activeAccountId }
    val sessionManager = remember(activeAccountId) { SessionManager(context) }
    val socialRepo = remember(activeAccountId) { SocialRepository(context, activeAccountId) }
    val convRepo = remember(activeAccountId) { ConversationRepository(context, activeAccountId) }
    val circleRepo = remember(activeAccountId) { FriendCircleRepository(context, activeAccountId) }

    val isMyProfile = remember(userPhone) {
        sessionManager.userPhone.isNotBlank() && FriendRequestRepository.isSamePhone(sessionManager.userPhone, userPhone)
    }

    val isDev = remember(userPhone) { AdminSecurityHelper.isAdmin(userPhone) }
    val effectiveRole = remember(userPhone, initialRole) {
        if (isDev) UserSocialRole.FOUNDER_DEV
        else AdminSecurityHelper.getUserSocialRole(userPhone, context).takeIf { it != UserSocialRole.STANDARD } ?: initialRole
    }

    val friendRepo = remember(activeAccountId) { FriendRequestRepository(context, activeAccountId) }
    val isFriendWithUser = remember(userPhone, isMyProfile, activeAccountId) {
        isMyProfile || friendRepo.isFriend(userPhone)
    }

    // Contact Lookup
    val contact = remember(userPhone) {
        convRepo.loadContacts().find { FriendRequestRepository.isSamePhone(it.phone, userPhone) }
    }
    val nativeContact = remember(userPhone) {
        ContactsPickerHelper.lookupNativeContact(context, userPhone)
    }

    // Dynamic Profile Attributes
    var profileName by remember {
        mutableStateOf(
            if (isMyProfile) sessionManager.userName.ifBlank { "Moi" }
            else contact?.name ?: nativeContact?.displayName ?: initialPseudo
        )
    }
    var profileAvatar by remember {
        mutableStateOf(
            if (isMyProfile) sessionManager.userAvatarPath
            else if (isDev) AvatarManager.ensureOfficialAppAvatar(context)
            else contact?.avatarPath ?: initialAvatarPath
        )
    }
    var profileBio by remember {
        mutableStateOf(
            if (isMyProfile) sessionManager.userBio
            else contact?.bio ?: ""
        )
    }
    var profileJob by remember {
        mutableStateOf(
            if (isMyProfile) sessionManager.userJobTitle
            else contact?.jobTitle ?: ""
        )
    }
    var profileLocation by remember {
        mutableStateOf(
            if (isMyProfile) sessionManager.userLocation
            else contact?.location ?: ""
        )
    }
    var profileInterests by remember {
        mutableStateOf(
            if (isMyProfile) sessionManager.userInterests
            else contact?.interests ?: emptyList()
        )
    }

    // Public Key Fingerprint
    val publicKeyString = remember(userPhone) {
        if (isMyProfile) sessionManager.publicKey
        else contact?.publicKey ?: ""
    }
    val keyFingerprint = remember(publicKeyString) {
        if (publicKeyString.isNotBlank()) publicKeyString.take(16) + "..." else "NON_SYNCHRONISEE"
    }

    // Posts & Wall Data
    var allPosts by remember { mutableStateOf(socialRepo.loadPosts()) }
    val userPosts: List<SocialPost> = remember(allPosts, userPhone, isFriendWithUser) {
        if (!isFriendWithUser) emptyList()
        else allPosts.filter { post -> FriendRequestRepository.isSamePhone(post.authorPhone, userPhone) }
    }
    val userOriginalPosts: List<SocialPost> = remember(userPosts) {
        userPosts.filter { it.repostAuthorPhone == null }
    }
    val userReposts: List<SocialPost> = remember(userPosts) {
        userPosts.filter { it.repostAuthorPhone != null }
    }
    val userPolls: List<SocialPost> = remember(userPosts) {
        userPosts.filter { it.poll != null }
    }
    val totalReactions = remember(userPosts) {
        userPosts.sumOf { it.reactions.size }
    }
    val sharedCirclesCount = remember(userPhone) {
        circleRepo.loadCircles().count { c -> c.memberPhones.any { FriendRequestRepository.isSamePhone(it, userPhone) } }
    }

    // Tabs: 0 = Publications, 1 = Republications, 2 = À Propos, 3 = Sondages
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Dialog & Interaction States
    var showMenu by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var activePostForComments by remember { mutableStateOf<SocialPost?>(null) }
    var postToDelete by remember { mutableStateOf<SocialPost?>(null) }
    var postToShareInChat by remember { mutableStateOf<SocialPost?>(null) }
    var activePostForReactions by remember { mutableStateOf<SocialPost?>(null) }
    var showFriendsList by remember { mutableStateOf(false) }
    var showFriendRequestsFromWall by remember { mutableStateOf(false) }
    val blockedRepo = remember(activeAccountId) { com.sha.orbis.storage.BlockedContactsRepository(context, activeAccountId) }
    var isUserBlocked by remember(userPhone) { mutableStateOf(blockedRepo.isBlocked(userPhone)) }
    var showBlockDialog by remember { mutableStateOf(false) }

    val friendsCount = if (isMyProfile) SocialFriendsCatalog.load(context, activeAccountId).size else 0

    fun refreshPosts() {
        val updated = socialRepo.loadPosts()
        allPosts = updated
        isUserBlocked = blockedRepo.isBlocked(userPhone)
        if (activePostForComments != null) {
            activePostForComments = socialRepo.findPostById(activePostForComments?.id)
        }
        if (activePostForReactions != null) {
            activePostForReactions = socialRepo.findPostById(activePostForReactions?.id)
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                refreshPosts()
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(com.sha.orbis.notification.OrbisEventBus.ACTION_ORBIS_POST_RECEIVED)
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

    // Avatar Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = AvatarManager.saveAvatarFromUri(
                context = context,
                imageUri = uri,
                identifier = "avatar_${System.currentTimeMillis()}"
            )
            if (savedPath != null) {
                profileAvatar = savedPath
                sessionManager.updateActiveAccountAvatar(savedPath)
                Toast.makeText(context, context.getString(R.string.contact_photo_assigned), Toast.LENGTH_SHORT).show()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).publishProfileUpdate(avatarPath = savedPath)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Share Profile helper
    fun shareProfile() {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                "Profil Souverain OrbisNet : $profileName ($userPhone)\nRejoignez le réseau souverain décentralisé Nostr !\nTélécharger l'application : https://github.com/ShaDevPro/O-R-B-I-S-net/releases/download/OrbisNet-v1.4.0/O.R.B.I.S.apk"
            )
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.wall_action_share_profile)))
    }

    fun handleReactPost(postId: String, emoji: String) {
        socialRepo.addReaction(postId, sessionManager.userPhone, emoji)
        refreshPosts()

        val targetPost = socialRepo.findPostById(postId)
        try {
            val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
            val authorTargetKey = targetPost?.authorPubkey ?: targetPost?.authorPhone ?: ""
            nostrSync.publishReaction(postId, authorTargetKey, emoji)
        } catch (e: Exception) {
            android.util.Log.w("UserProfileWall", "Erreur reaction Nostr: ${e.message}")
        }

    }

    fun handleVotePoll(postId: String, optId: String) {
        val voteApplied = socialRepo.votePoll(postId, optId, sessionManager.userPhone, isLocalUser = true)
        if (voteApplied) {
            refreshPosts()
            try {
                val targetPost = socialRepo.loadPosts().firstOrNull { it.id == postId }
                val targetAuthorKey = targetPost?.authorPubkey ?: targetPost?.authorPhone ?: userPhone
                val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                nostrSync.publishPollVote(postId, optId, targetAuthorKey)
            } catch (e: Exception) {
                android.util.Log.w("UserProfileWall", "Erreur diffusion vote Nostr: ${e.message}")
            }
            Toast.makeText(context, context.getString(R.string.social_toast_vote_broadcast), Toast.LENGTH_SHORT).show()
        }
    }

    fun handleEditPost(post: SocialPost, newContent: String, newHashtags: List<String>) {
        socialRepo.editPost(post.id, newContent, newHashtags)
        refreshPosts()
        try {
            val updatedPost = socialRepo.loadPosts().firstOrNull { it.id == post.id }
            if (updatedPost != null) {
                val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                nostrSync.publishPost(updatedPost)
            }
        } catch (e: Exception) {
            android.util.Log.w("UserProfileWall", "Erreur edit post Nostr: ${e.message}")
        }
        Toast.makeText(context, context.getString(R.string.social_toast_post_edited), Toast.LENGTH_SHORT).show()
    }

    if (showFriendRequestsFromWall) {
        FriendRequestsScreen(
            onBack = {
                showFriendRequestsFromWall = false
                refreshPosts()
            }
        )
        return
    }

    if (showFriendsList && isMyProfile) {
        MyFriendsListScreen(
            currentAccountId = activeAccountId,
            onBack = {
                showFriendsList = false
                refreshPosts()
            },
            onOpenChat = onOpenChat,
            onOpenWall = { phone, pseudo, avatar, role ->
                showFriendsList = false
                onNavigateToPeerWall?.invoke(phone, pseudo, avatar, role)
            },
            onManageRequests = { showFriendRequestsFromWall = true }
        )
        return
    }

    // Modals & BottomSheets
    if (postToShareInChat != null) {
        SharePostToChatDialog(
            post = postToShareInChat!!,
            onDismiss = { postToShareInChat = null }
        )
    }

    if (showEditProfileDialog) {
        WallEditProfileDialog(
            initialName = profileName,
            initialBio = profileBio,
            initialJob = profileJob,
            initialLocation = profileLocation,
            initialInterests = profileInterests,
            userPhone = if (isMyProfile) sessionManager.userPhone else userPhone,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, bio, job, location, interests ->
                profileName = name
                profileBio = bio
                profileJob = job
                profileLocation = location
                profileInterests = interests

                if (isMyProfile) {
                    sessionManager.updateProfileDetails(
                        name = name,
                        bio = bio,
                        jobTitle = job,
                        location = location,
                        interests = interests
                    )
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context).publishProfileUpdate(
                                displayName = name,
                                bio = bio
                            )
                        } catch (_: Exception) {}
                    }
                } else if (contact != null) {
                    val updatedContact = contact.copy(
                        name = name,
                        bio = bio,
                        jobTitle = job,
                        location = location,
                        interests = interests
                    )
                    val contacts = convRepo.loadContacts().toMutableList()
                    val idx = contacts.indexOfFirst { it.id == contact.id }
                    if (idx >= 0) contacts[idx] = updatedContact
                    convRepo.saveContacts(contacts)
                }
                showEditProfileDialog = false
                Toast.makeText(context, context.getString(R.string.profile_updated_success), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showCreatePostDialog) {
        CreatePostDialog(
            authorPhone = sessionManager.userPhone,
            authorName = sessionManager.userName.ifBlank { "Moi" },
            authorAvatarPath = sessionManager.userAvatarPath,
            onDismiss = { showCreatePostDialog = false },
            onPostCreated = { content, hashtags, poll, circleId, excludedCircleIds, excludedPhones, isOfficial, isPinned, role, mediaType, mediaPath, mediaData, mediaUrl ->
                val normalizedAuthorPhone = ContactsPickerHelper.normalizePhoneNumber(sessionManager.userPhone)
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
                    rsaSignature = "rsa_valid_signature",
                    poll = poll,
                    reactions = emptyList(),
                    comments = emptyList(),
                    isOfficialAnnouncement = isOfficial,
                    isPinned = isPinned,
                    authorRole = role,
                    mediaType = mediaType,
                    mediaPath = mediaPath,
                    mediaData = mediaData,
                    mediaUrl = mediaUrl
                )
                socialRepo.addPost(newPost)
                refreshPosts()
                showCreatePostDialog = false

                // Broadcast via Nostr Relays
                try {
                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                    nostrSync.publishPost(newPost)
                } catch (e: Exception) {
                    android.util.Log.w("UserProfileWall", "Erreur post Nostr: ${e.message}")
                }

                Toast.makeText(context, "Publication diffusée sur le réseau OrbisNet 🛡️", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (activePostForComments != null) {
        val currentActivePost = activePostForComments!!
        CommentsBottomSheet(
            post = currentActivePost,
            currentPhone = sessionManager.userPhone,
            currentUserName = sessionManager.userName.ifBlank { "Moi" },
            currentUserAvatarPath = sessionManager.userAvatarPath,
            onDismiss = { activePostForComments = null },
            onAddComment = { text, replyToId, replyToName ->
                val comment = SocialComment(
                    id = "c_${UUID.randomUUID().toString().take(6)}",
                    postId = currentActivePost.id,
                    authorPhone = sessionManager.userPhone,
                    authorName = sessionManager.userName.ifBlank { "Moi" },
                    authorAvatarPath = sessionManager.userAvatarPath,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    replyToCommentId = replyToId,
                    replyToAuthorName = replyToName
                )
                socialRepo.addComment(currentActivePost.id, comment)
                refreshPosts()
                activePostForComments = socialRepo.findPostById(currentActivePost.id)

                // Broadcast via Nostr Relays
                try {
                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                    val authorTargetKey = currentActivePost.authorPubkey ?: currentActivePost.authorPhone
                    nostrSync.publishComment(currentActivePost.id, authorTargetKey, comment)
                } catch (e: Exception) {
                    android.util.Log.w("UserProfileWall", "Erreur commentaire Nostr: ${e.message}")
                }

                Toast.makeText(context, context.getString(R.string.social_toast_comment_broadcast), Toast.LENGTH_SHORT).show()
            },
            onDeleteComment = { commentId ->
                socialRepo.deleteComment(currentActivePost.id, commentId)
                refreshPosts()
                activePostForComments = socialRepo.findPostById(currentActivePost.id)

                // Broadcast Delete Comment via Nostr (Kind 5 NIP-09)
                try {
                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                    nostrSync.publishDeleteComment(currentActivePost.id, commentId)
                } catch (e: Exception) {
                    android.util.Log.w("UserProfileWall", "Erreur suppression commentaire Nostr: ${e.message}")
                }

                Toast.makeText(context, context.getString(R.string.social_toast_comment_deleted), Toast.LENGTH_SHORT).show()
            },
            onEditComment = { commentId, newText ->
                socialRepo.editComment(currentActivePost.id, commentId, newText)
                refreshPosts()
                activePostForComments = socialRepo.findPostById(currentActivePost.id)

                try {
                    val updatedComment = activePostForComments?.comments?.firstOrNull { it.id == commentId }
                    if (updatedComment != null) {
                        val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                        val authorTargetKey = currentActivePost.authorPubkey ?: currentActivePost.authorPhone
                        nostrSync.publishComment(currentActivePost.id, authorTargetKey, updatedComment)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("UserProfileWall", "Erreur modification commentaire Nostr: ${e.message}")
                }

                Toast.makeText(context, context.getString(R.string.social_toast_comment_edited), Toast.LENGTH_SHORT).show()
            },
            onReactComment = { commentId, emoji ->
                socialRepo.addCommentReaction(currentActivePost.id, commentId, sessionManager.userPhone, emoji)
                refreshPosts()
                activePostForComments = socialRepo.findPostById(currentActivePost.id)

                // Broadcast Comment Reaction via Nostr (Kind 7 with comment_id tag)
                try {
                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                    val authorTargetKey = currentActivePost.authorPubkey ?: currentActivePost.authorPhone
                    nostrSync.publishCommentReaction(currentActivePost.id, commentId, authorTargetKey, emoji)
                } catch (e: Exception) {
                    android.util.Log.w("UserProfileWall", "Erreur diffusion reaction-commentaire Nostr: ${e.message}")
                }
            }
        )
    }

    // ── Reactions Detail Sheet ──────────────────────────────
    if (activePostForReactions != null) {
        ReactionsDetailSheet(
            reactions = activePostForReactions!!.reactions,
            currentPhone = sessionManager.userPhone,
            onDismiss = { activePostForReactions = null }
        )
    }

    if (postToDelete != null) {
        AlertDialog(
            onDismissRequest = { postToDelete = null },
            title = { Text(stringResource(R.string.social_delete_post_title)) },
            text = { Text(stringResource(R.string.social_delete_post_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDel = postToDelete!!
                        socialRepo.deletePost(toDel.id)
                        refreshPosts()
                        postToDelete = null

                        try {
                            val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                            nostrSync.publishDeletePost(toDel.id)
                        } catch (e: Exception) {
                            android.util.Log.w("UserProfileWall", "Erreur delete Nostr: ${e.message}")
                        }
                        Toast.makeText(context, context.getString(R.string.social_toast_post_deleted), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.delete_for_everyone), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { postToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.block_contact_confirm_title, profileName),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.block_contact_confirm_desc),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        blockedRepo.blockContact(userPhone, profileName)
                        showBlockDialog = false
                        isUserBlocked = true
                        Toast.makeText(context, context.getString(R.string.chat_toast_contact_blocked), Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.block_contact_btn),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Main Scaffold UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = profileName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "(${userPosts.size} ${stringResource(R.string.wall_stat_posts).lowercase()})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (isMyProfile) {
                        IconButton(onClick = { showFriendsList = true }) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = stringResource(R.string.wall_action_my_friends),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { shareProfile() }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.wall_action_share_profile),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (isMyProfile) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.wall_action_my_friends)) },
                                onClick = {
                                    showMenu = false
                                    showFriendsList = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.wall_action_edit)) },
                                onClick = {
                                    showMenu = false
                                    showEditProfileDialog = true
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.wall_action_share_profile)) },
                            onClick = {
                                showMenu = false
                                shareProfile()
                            }
                        )
                        if (!isMyProfile) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isUserBlocked) stringResource(R.string.wall_action_unblock) else stringResource(R.string.wall_action_block),
                                        color = if (isUserBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    if (isUserBlocked) {
                                        blockedRepo.unblockContact(userPhone)
                                        isUserBlocked = false
                                        Toast.makeText(context, context.getString(R.string.chat_toast_contact_unblocked), Toast.LENGTH_SHORT).show()
                                        refreshPosts()
                                    } else {
                                        showBlockDialog = true
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (isMyProfile) {
                FloatingActionButton(
                    onClick = { showCreatePostDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.feed_hero_create_post),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Hero Cover + Floating Avatar + Identity + Actions
            item {
                WallHeaderSection(
                    userPhone = userPhone,
                    userName = profileName,
                    userAvatarPath = profileAvatar,
                    userRole = effectiveRole,
                    userBio = profileBio,
                    userJobTitle = profileJob,
                    userLocation = profileLocation,
                    keyFingerprint = keyFingerprint,
                    isMyProfile = isMyProfile,
                    onEditAvatar = { photoPickerLauncher.launch("image/*") },
                    onEditProfile = { showEditProfileDialog = true },
                    onCreatePost = { showCreatePostDialog = true },
                    onOpenFriends = if (isMyProfile) {
                        { showFriendsList = true }
                    } else null,
                    onOpenChat = if (!isMyProfile && onOpenChat != null) {
                        { onOpenChat(userPhone, profileName) }
                    } else null,
                    onShareProfile = { shareProfile() }
                )
            }

            // 2. Metrics / Stats Bar (Posts, Reposts, Reactions, Circles)
            item {
                WallStatsRow(
                    postsCount = userOriginalPosts.size,
                    repostsCount = userReposts.size,
                    reactionsCount = totalReactions,
                    circlesCount = sharedCirclesCount,
                    onTabSelect = { selectedTabIndex = it }
                )
            }

            if (isMyProfile) {
                item {
                    WallFriendsHubCard(
                        friendsCount = friendsCount,
                        onOpenFriendsList = { showFriendsList = true }
                    )
                }
            }

            // 3. Tab Bar (Publications, Republications, À Propos, Sondages)
            item {
                WallTabs(
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it }
                )
            }

            // 4. Tab Content Rendering
            if (!isFriendWithUser && selectedTabIndex != 2) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = stringResource(R.string.wall_private_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.wall_private_desc),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                when (selectedTabIndex) {
                    0 -> {
                        // TAB 0: Publications
                        wallPostsTab(
                            posts = userOriginalPosts,
                            currentPhone = sessionManager.userPhone,
                            isMyProfile = isMyProfile,
                            isRepostsTab = false,
                            onReact = { postId, emoji -> handleReactPost(postId, emoji) },
                            onVotePoll = { postId, optId -> handleVotePoll(postId, optId) },
                            onOpenComments = { activePostForComments = it },
                            onDeletePost = { postToDelete = it },
                            onEditPost = { post, newContent, newHashtags -> handleEditPost(post, newContent, newHashtags) },
                            onTogglePin = { post ->
                                socialRepo.togglePinPost(post.id)
                                refreshPosts()
                                val willBePinned = !post.isPinned
                                Toast.makeText(
                                    context,
                                    if (willBePinned) context.getString(R.string.social_toast_post_pinned) else context.getString(R.string.social_toast_post_unpinned),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onRepost = { post ->
                                val reposted = socialRepo.repost(post, sessionManager.userPhone, sessionManager.userName.ifBlank { "Moi" }, sessionManager.userAvatarPath)
                                refreshPosts()
                                try {
                                    val nostrSync = com.sha.orbis.nostr.service.NostrSyncManager.getInstance(context)
                                    nostrSync.publishPost(reposted)
                                } catch (e: Exception) {
                                    android.util.Log.w("UserProfileWall", "Erreur repost Nostr: ${e.message}")
                                }
                                Toast.makeText(context, context.getString(R.string.social_repost_success), Toast.LENGTH_SHORT).show()
                            },
                            onShareToChat = { postToShareInChat = it },
                            onShowReactions = { activePostForReactions = it },
                            onCreatePost = { showCreatePostDialog = true }
                        )
                    }

                    1 -> {
                        // TAB 1: Republications (Posts reposted by this user)
                        wallPostsTab(
                            posts = userReposts,
                            currentPhone = sessionManager.userPhone,
                            isMyProfile = isMyProfile,
                            isRepostsTab = true,
                            onReact = { postId, emoji -> handleReactPost(postId, emoji) },
                            onVotePoll = { postId, optId -> handleVotePoll(postId, optId) },
                            onOpenComments = { activePostForComments = it },
                            onDeletePost = { postToDelete = it },
                            onTogglePin = { post ->
                                socialRepo.togglePinPost(post.id)
                                refreshPosts()
                                val willBePinned = !post.isPinned
                                Toast.makeText(
                                    context,
                                    if (willBePinned) context.getString(R.string.social_toast_post_pinned) else context.getString(R.string.social_toast_post_unpinned),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onRepost = null,
                            onShareToChat = { postToShareInChat = it },
                            onShowReactions = { activePostForReactions = it },
                            onCreatePost = null
                        )
                    }

                    2 -> {
                        // TAB 2: À Propos (Bio, Details, Interêts, Cryptographie)
                        wallAboutTab(
                            userPhone = userPhone,
                            userBio = profileBio,
                            userJobTitle = profileJob,
                            userLocation = profileLocation,
                            userInterests = profileInterests,
                            publicKeyString = publicKeyString,
                            keyFingerprint = keyFingerprint
                        )
                    }

                    3 -> {
                        // TAB 3: Sondages
                        wallPostsTab(
                            posts = userPolls,
                            currentPhone = sessionManager.userPhone,
                            isMyProfile = isMyProfile,
                            isRepostsTab = false,
                            onReact = { postId, emoji -> handleReactPost(postId, emoji) },
                            onVotePoll = { postId, optId -> handleVotePoll(postId, optId) },
                            onOpenComments = { activePostForComments = it },
                            onDeletePost = { postToDelete = it },
                            onEditPost = { post, newContent, newHashtags -> handleEditPost(post, newContent, newHashtags) },
                            onTogglePin = { socialRepo.togglePinPost(it.id); refreshPosts() },
                            onRepost = null,
                            onShareToChat = { postToShareInChat = it },
                            onShowReactions = { activePostForReactions = it },
                            onCreatePost = { showCreatePostDialog = true }
                        )
                    }
                }
            }
        }
    }
}
