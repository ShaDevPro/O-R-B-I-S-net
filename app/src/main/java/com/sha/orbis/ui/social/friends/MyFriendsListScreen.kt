package com.sha.orbis.ui.social.friends

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.notification.OrbisEventBus
import com.sha.orbis.social.SocialFriendsCatalog
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.storage.BlockedContactsRepository
import com.sha.orbis.storage.FriendRequestRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFriendsListScreen(
    currentAccountId: String? = null,
    onBack: () -> Unit,
    onOpenChat: ((phone: String, name: String) -> Unit)? = null,
    onOpenWall: ((phone: String, pseudo: String, avatar: String?, role: UserSocialRole) -> Unit)? = null,
    onManageRequests: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activeAccountId = currentAccountId ?: remember { com.sha.orbis.data.SessionManager(context).activeAccountId }
    val friendRepo = remember(activeAccountId) { FriendRequestRepository(context, activeAccountId) }
    val blockedRepo = remember(activeAccountId) { BlockedContactsRepository(context, activeAccountId) }
    var searchQuery by remember { mutableStateOf("") }
    var friends by remember(activeAccountId) { mutableStateOf(SocialFriendsCatalog.load(context, activeAccountId)) }
    var friendToRemove by remember { mutableStateOf<SocialFriendsCatalog.Entry?>(null) }
    var friendToBlock by remember { mutableStateOf<SocialFriendsCatalog.Entry?>(null) }

    fun refresh() {
        friends = SocialFriendsCatalog.load(context, activeAccountId)
    }

    val filtered = remember(friends, searchQuery) {
        if (searchQuery.isBlank()) {
            friends
        } else {
            val q = searchQuery.trim().lowercase()
            friends.filter { entry ->
                entry.displayName.lowercase().contains(q) ||
                    entry.phone.lowercase().contains(q) ||
                    entry.phone.filter { it.isDigit() }.contains(q.filter { it.isDigit() })
            }
        }
    }

    if (friendToRemove != null) {
        val target = friendToRemove!!
        AlertDialog(
            onDismissRequest = { friendToRemove = null },
            title = { Text(stringResource(R.string.my_friends_dialog_remove_title)) },
            text = { Text(stringResource(R.string.my_friends_dialog_remove_desc, target.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        friendRepo.removeFriend(target.phone)
                        val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                            setPackage(context.packageName)
                        }
                        context.sendBroadcast(intent)
                        Toast.makeText(
                            context,
                            context.getString(R.string.my_friends_toast_removed, target.displayName),
                            Toast.LENGTH_SHORT
                        ).show()
                        friendToRemove = null
                        refresh()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.my_friends_action_remove),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { friendToRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (friendToBlock != null) {
        val target = friendToBlock!!
        AlertDialog(
            onDismissRequest = { friendToBlock = null },
            title = { Text(stringResource(R.string.my_friends_dialog_block_title)) },
            text = { Text(stringResource(R.string.my_friends_dialog_block_desc, target.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        blockedRepo.blockContact(target.phone, target.displayName, "Bloqué depuis la liste d'amis")
                        friendRepo.removeFriend(target.phone)
                        val intent = Intent(OrbisEventBus.ACTION_REFRESH_CONVERSATIONS).apply {
                            setPackage(context.packageName)
                        }
                        context.sendBroadcast(intent)
                        Toast.makeText(
                            context,
                            context.getString(R.string.my_friends_toast_blocked, target.displayName),
                            Toast.LENGTH_SHORT
                        ).show()
                        friendToBlock = null
                        refresh()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.my_friends_action_block),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { friendToBlock = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.my_friends_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.my_friends_subtitle, friends.size),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                },
                actions = {
                    if (onManageRequests != null) {
                        IconButton(onClick = {
                            onManageRequests()
                            refresh()
                        }) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = stringResource(R.string.my_friends_manage_requests)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.my_friends_network_hint),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.my_friends_search_hint), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotBlank()) {
                        {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            if (onManageRequests != null) {
                item {
                    OutlinedButton(
                        onClick = onManageRequests,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.my_friends_manage_requests),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    MyFriendsEmptyState(onManageRequests = { onManageRequests?.invoke() ?: onBack() })
                }
            } else {
                items(filtered, key = { it.phone }) { entry ->
                    FriendListItemCard(
                        entry = entry,
                        onOpenWall = {
                            onOpenWall?.invoke(entry.phone, entry.displayName, entry.avatarPath, entry.socialRole)
                        },
                        onOpenChat = if (onOpenChat != null) {
                            { onOpenChat(entry.phone, entry.displayName) }
                        } else null,
                        onRemoveFriend = {
                            friendToRemove = entry
                        },
                        onBlockUser = {
                            friendToBlock = entry
                        }
                    )
                }
            }
        }
    }
}
