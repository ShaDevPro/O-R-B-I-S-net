package com.sha.orbis.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.social.SocialEngagementAudiencePolicy
import com.sha.orbis.social.SocialReaction
import com.sha.orbis.social.SocialStory
import com.sha.orbis.storage.BlockedContactsRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.components.OrbisAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Clé stable pour dédupliquer les viewers/réacteurs (mêmes 10 derniers chiffres).
 */
private fun stableViewerKey(value: String): String {
    val digits = value.filter { it.isDigit() }
    return if (digits.length >= 8) digits.takeLast(10) else value.trim().lowercase(Locale.ROOT)
}

/**
 * Données résolues pour afficher un viewer ou réacteur dans la liste.
 */
private data class ResolvedUser(
    val phone: String,
    val displayName: String,
    val avatarPath: String?,
    val isDev: Boolean = false,
    val isVerified: Boolean = false
)

/**
 * Données résolues pour un réacteur avec son emoji.
 */
private data class StoryResolvedReactor(
    val phone: String,
    val displayName: String,
    val avatarPath: String?,
    val emoji: String,
    val isDev: Boolean = false,
    val isVerified: Boolean = false
)

/**
 * Bottom sheet affichant 2 onglets : les viewers et les réacteurs d'une story.
 *
 * @param story La story dont on affiche les détails.
 * @param initialTab 0 = onglet Viewers, 1 = onglet Réactions.
 * @param onDismiss Callback de fermeture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryViewersSheet(
    story: SocialStory,
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val friendRepo = remember { FriendRequestRepository(context) }
    val convRepo = remember { com.sha.orbis.storage.ConversationRepository(context) }
    val blockedRepo = remember { BlockedContactsRepository(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    // --- Résolution asynchrone des viewers (souverains uniquement) ---
    var resolvedViewers by remember { mutableStateOf<List<ResolvedUser>>(emptyList()) }
    LaunchedEffect(story.seenBy) {
        resolvedViewers = withContext(Dispatchers.IO) {
            val contacts = try { convRepo.loadContacts() } catch (_: Exception) { emptyList() }
            story.seenBy
                .map { stableViewerKey(it) }
                .filter { it.isNotBlank() && !blockedRepo.isBlocked(it) }
                .filter { key -> !story.excludedPhones.any { FriendRequestRepository.isSamePhone(it, key) } }
                .distinct()
                .map { key ->
                    val isDev = AdminSecurityHelper.isAdmin(key)
                    val isVerified = isDev || com.sha.orbis.security.OrbisTrustVerificationEngine.isAutomatedVerified(context, key)
                    val friend = friendRepo.getFriendRequestForPhone(key)
                    val contact = contacts.find { FriendRequestRepository.isSamePhone(it.phone, key) }
                    val name = when {
                        isDev -> "O R B I S net"
                        !friend?.senderName.isNullOrBlank() && !friend.senderName.startsWith("+") && friend.senderName != "Ami Orbis" && friend.senderName != "Ami OrbisNet" && !AdminSecurityHelper.isReservedName(friend.senderName) -> friend.senderName
                        !contact?.name.isNullOrBlank() && !contact.name.startsWith("+") && contact.name != "Ami Orbis" && contact.name != "Ami OrbisNet" && !AdminSecurityHelper.isReservedName(contact.name) -> contact.name
                        else -> friend?.senderName?.takeIf { it.isNotBlank() && !AdminSecurityHelper.isReservedName(it) } ?: contact?.name?.takeIf { it.isNotBlank() && !AdminSecurityHelper.isReservedName(it) } ?: context.getString(R.string.notif_sender_orbis_member)
                    }
                    val avatar = if (isDev) com.sha.orbis.ui.components.AvatarManager.ensureOfficialAppAvatar(context) else (friend?.senderAvatarPath ?: contact?.avatarPath)
                    ResolvedUser(
                        phone = key,
                        displayName = name,
                        avatarPath = avatar,
                        isDev = isDev,
                        isVerified = isVerified
                    )
                }
        }
    }

    // --- Résolution asynchrone des réacteurs (souverains uniquement) ---
    val visibleStoryReactions = remember(story.reactions, story.id) {
        SocialEngagementAudiencePolicy.filterVisibleStoryReactions(
            reactions = story.reactions,
            story = story,
            viewerPhone = sessionManager.userPhone,
            friendRepo = friendRepo,
            blockedRepo = blockedRepo
        )
    }

    var resolvedReactors by remember { mutableStateOf<List<StoryResolvedReactor>>(emptyList()) }
    LaunchedEffect(visibleStoryReactions) {
        resolvedReactors = withContext(Dispatchers.IO) {
            val contacts = try { convRepo.loadContacts() } catch (_: Exception) { emptyList() }
            val latestByUser = mutableMapOf<String, SocialReaction>()
            for (reaction in visibleStoryReactions) {
                val key = stableViewerKey(reaction.userPhone)
                if (key.isNotBlank()) {
                    latestByUser[key] = reaction
                }
            }
            latestByUser.map { (key, reaction) ->
                val isDev = AdminSecurityHelper.isAdmin(key)
                val isVerified = isDev || com.sha.orbis.security.OrbisTrustVerificationEngine.isAutomatedVerified(context, key)
                val friend = friendRepo.getFriendRequestForPhone(key)
                val contact = contacts.find { FriendRequestRepository.isSamePhone(it.phone, key) }
                val name = when {
                    isDev -> "O R B I S net"
                    !friend?.senderName.isNullOrBlank() && !friend.senderName.startsWith("+") && friend.senderName != "Ami Orbis" && friend.senderName != "Ami OrbisNet" && !AdminSecurityHelper.isReservedName(friend.senderName) -> friend.senderName
                    !contact?.name.isNullOrBlank() && !contact.name.startsWith("+") && contact.name != "Ami Orbis" && contact.name != "Ami OrbisNet" && !AdminSecurityHelper.isReservedName(contact.name) -> contact.name
                    else -> friend?.senderName?.takeIf { it.isNotBlank() && !AdminSecurityHelper.isReservedName(it) } ?: contact?.name?.takeIf { it.isNotBlank() && !AdminSecurityHelper.isReservedName(it) } ?: context.getString(R.string.notif_sender_orbis_member)
                }
                val avatar = if (isDev) com.sha.orbis.ui.components.AvatarManager.ensureOfficialAppAvatar(context) else (friend?.senderAvatarPath ?: contact?.avatarPath)
                StoryResolvedReactor(
                    phone = key,
                    displayName = name,
                    avatarPath = avatar,
                    emoji = reaction.emoji,
                    isDev = isDev,
                    isVerified = isVerified
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp)
                .padding(bottom = 16.dp)
        ) {
            // Titre
            Text(
                text = if (selectedTab == 0)
                    stringResource(R.string.story_viewers_title)
                else
                    stringResource(R.string.story_reactors_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // TabRow
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = stringResource(R.string.story_viewers_tab_viewers) +
                                    " (${resolvedViewers.size})"
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = stringResource(R.string.story_viewers_tab_reactions) +
                                    " (${resolvedReactors.size})"
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Contenu selon l'onglet
            when (selectedTab) {
                0 -> ViewersTabContent(viewers = resolvedViewers)
                1 -> ReactorsTabContent(reactors = resolvedReactors)
            }
        }
    }
}

/**
 * Contenu de l'onglet Viewers.
 */
@Composable
private fun ViewersTabContent(viewers: List<ResolvedUser>) {
    if (viewers.isEmpty()) {
        EmptyTabMessage(stringResource(R.string.story_viewers_empty))
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(viewers, key = { it.phone }) { viewer ->
                UserRow(
                    avatarPath = viewer.avatarPath,
                    displayName = viewer.displayName,
                    isDev = viewer.isDev,
                    isVerified = viewer.isVerified
                )
            }
        }
    }
}

/**
 * Contenu de l'onglet Réactions.
 */
@Composable
private fun ReactorsTabContent(reactors: List<StoryResolvedReactor>) {
    if (reactors.isEmpty()) {
        EmptyTabMessage(stringResource(R.string.story_reactors_empty))
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(reactors, key = { it.phone }) { reactor ->
                ReactorRow(
                    avatarPath = reactor.avatarPath,
                    displayName = reactor.displayName,
                    emoji = reactor.emoji,
                    isDev = reactor.isDev,
                    isVerified = reactor.isVerified
                )
            }
        }
    }
}

/**
 * Ligne utilisateur avec avatar et nom.
 */
@Composable
private fun UserRow(
    avatarPath: String?,
    displayName: String,
    isDev: Boolean = false,
    isVerified: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OrbisAvatar(
            avatarPath = avatarPath,
            name = displayName,
            size = 42.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isDev || isVerified) {
                com.sha.orbis.ui.social.BlueVerifiedBadge(size = 14.dp)
            }
        }
    }
}

/**
 * Ligne réacteur avec avatar, nom et emoji.
 */
@Composable
private fun ReactorRow(
    avatarPath: String?,
    displayName: String,
    emoji: String,
    isDev: Boolean = false,
    isVerified: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OrbisAvatar(
            avatarPath = avatarPath,
            name = displayName,
            size = 42.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isDev || isVerified) {
                com.sha.orbis.ui.social.BlueVerifiedBadge(size = 14.dp)
            }
        }
        Text(
            text = emoji,
            fontSize = 24.sp
        )
    }
}

/**
 * Message vide centré pour un onglet sans données.
 */
@Composable
private fun EmptyTabMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
