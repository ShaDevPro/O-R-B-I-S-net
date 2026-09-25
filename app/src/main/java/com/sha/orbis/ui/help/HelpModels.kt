package com.sha.orbis.ui.help

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.ui.graphics.vector.ImageVector
import com.sha.orbis.R

/**
 * Categorization of help topics for intuitive discovery.
 */
enum class HelpCategory(
    val titleRes: Int,
    val icon: ImageVector
) {
    ALL(R.string.help_cat_all, Icons.Default.Layers),
    AI(R.string.help_cat_ai, Icons.Default.AutoAwesome),
    GETTING_STARTED(R.string.help_cat_getting_started, Icons.Default.RocketLaunch),
    MESSAGING(R.string.help_cat_messaging, Icons.AutoMirrored.Filled.Chat),
    CALLS(R.string.help_cat_calls, Icons.Default.Call),
    SOCIAL(R.string.help_cat_social, Icons.Default.Public),
    DUAL_SIM(R.string.help_cat_dual_sim, Icons.Default.SimCard),
    CONTACTS(R.string.help_cat_contacts, Icons.Default.Contacts),
    SECURITY(R.string.help_cat_security, Icons.Default.Security)
}

/**
 * Data model for an individual expandable help card.
 */
data class HelpTopic(
    val id: String,
    val category: HelpCategory,
    val titleRes: Int,
    val summaryRes: Int,
    val contentRes: Int,
    val icon: ImageVector,
    val isHighlighted: Boolean = false
)

/**
 * Data model for prominent top banner tips.
 */
data class HelpQuickTip(
    val id: String,
    val textRes: Int,
    val icon: ImageVector
)
