package com.sha.orbis.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.model.AccountProfile
import com.sha.orbis.ui.theme.OrbisColorPalette

/**
 * Standard Universal WhatsApp-style Top Header for Orbis.
 * 
 * Features:
 * - Proper `statusBarsPadding()` to guarantee zero overlap with Android system status bar (clock/wifi/battery).
 * - Left: "Orbis" title wordmark + Dual-SIM active line selector pill.
 * - Right: Search button + Settings icon button.
 * - Optional Back button navigation mode for child pages.
 * - Integrated expandable in-place search bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OrbisTopHeader(
    title: String = stringResource(R.string.app_name),
    subtitle: String? = null,
    activeAccount: AccountProfile? = null,
    onBack: (() -> Unit)? = null,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    searchPlaceholder: String = stringResource(R.string.search_conversations),
    onSearchQueryChange: ((String) -> Unit)? = null,
    onToggleSearch: (() -> Unit)? = null,
    onOpenAccountSwitcher: (() -> Unit)? = null,
    onOpenNotifications: (() -> Unit)? = null,
    unreadNotificationCount: Int = 0,
    onOpenSettings: (() -> Unit)? = null,
    onOpenMyWall: (() -> Unit)? = null,
    onTitleLongClick: (() -> Unit)? = null,
    userAvatarPath: String? = null,
    userName: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // Safely positions header BELOW Android status bar (clock/network/battery)
        ) {
            if (!isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Section: (Back button OR Title)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.settings_back_to_settings),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        val titleModifier = if (onTitleLongClick != null) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = onTitleLongClick
                            )
                        } else {
                            Modifier
                        }

                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = titleModifier
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (title.length > 22) 16.sp else if (title.length > 16) 18.sp else 21.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Right Section: Search + Notifications Bell + Profile Avatar with SIM Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (actions != null) {
                            actions()
                        } else {
                            RelayStatusPill()

                            if (onToggleSearch != null) {
                                IconButton(
                                    onClick = onToggleSearch,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.search_conversations),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            if (onOpenNotifications != null) {
                                Box(contentAlignment = Alignment.Center) {
                                    IconButton(
                                        onClick = onOpenNotifications,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = stringResource(R.string.notif_center_title),
                                            tint = if (unreadNotificationCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    if (unreadNotificationCount > 0) {
                                        OrbisPremiumBadge(
                                            count = unreadNotificationCount,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 1.dp, end = 1.dp)
                                        )
                                    }
                                }
                            }

                            if (onOpenSettings != null) {
                                IconButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.tab_settings),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            if (onOpenAccountSwitcher != null || onOpenMyWall != null) {
                                val simSlotIndex = activeAccount?.simSlotIndex ?: 0
                                val simBadgeText = if (simSlotIndex >= 0) "${simSlotIndex + 1}" else "1"

                                Box(
                                    modifier = Modifier
                                        .padding(start = 4.dp, end = 4.dp)
                                        .clickable {
                                            if (onOpenAccountSwitcher != null) onOpenAccountSwitcher()
                                            else onOpenMyWall?.invoke()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    OrbisAvatar(
                                        avatarPath = userAvatarPath,
                                        name = userName ?: "Moi",
                                        size = 35.dp
                                    )

                                    // Compact circular SIM Slot badge (shows only "1" or "2" with pixel-perfect optical centering)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 2.dp, y = 2.dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        @Suppress("DEPRECATION")
                                        Text(
                                            text = simBadgeText,
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                lineHeight = 9.5.sp,
                                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                    includeFontPadding = false
                                                )
                                            ),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // In-Place Search Bar with Auto-Focus, Responsive Typing, and Zero Glyph Clipping
                BackHandler(enabled = isSearchActive) {
                    onToggleSearch?.invoke()
                }

                val focusRequester = remember { FocusRequester() }
                val keyboardController = LocalSoftwareKeyboardController.current

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(21.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    @Suppress("DEPRECATION")
                                    Text(
                                        text = searchPlaceholder,
                                        style = TextStyle(
                                            fontSize = 14.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                @Suppress("DEPRECATION")
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { onSearchQueryChange?.invoke(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                    singleLine = true,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    textStyle = TextStyle(
                                        fontSize = 14.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Normal,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Search,
                                        keyboardType = KeyboardType.Text
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            keyboardController?.hide()
                                        }
                                    )
                                )
                            }

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange?.invoke("") },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.cancel),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (onToggleSearch != null) {
                        TextButton(
                            onClick = onToggleSearch,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
