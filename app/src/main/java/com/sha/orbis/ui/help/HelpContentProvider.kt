package com.sha.orbis.ui.help

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiOff
import com.sha.orbis.R

/**
 * Provides static, localized help topics and quick tips extracted from the Orbis architecture.
 */
object HelpContentProvider {

    fun getTopics(): List<HelpTopic> = listOf(
        HelpTopic(
            id = "orbis_guard_llm",
            category = HelpCategory.AI,
            titleRes = R.string.help_topic_guard_llm_title,
            summaryRes = R.string.help_topic_guard_llm_summary,
            contentRes = R.string.help_topic_guard_llm_content,
            icon = Icons.Default.Security,
            isHighlighted = true
        ),
        HelpTopic(
            id = "orbis_reply_llm",
            category = HelpCategory.AI,
            titleRes = R.string.help_topic_reply_llm_title,
            summaryRes = R.string.help_topic_reply_llm_summary,
            contentRes = R.string.help_topic_reply_llm_content,
            icon = Icons.Default.AutoAwesome,
            isHighlighted = true
        ),
        HelpTopic(
            id = "orbis_ai_privacy",
            category = HelpCategory.AI,
            titleRes = R.string.help_topic_ai_privacy_title,
            summaryRes = R.string.help_topic_ai_privacy_summary,
            contentRes = R.string.help_topic_ai_privacy_content,
            icon = Icons.Default.WifiOff,
            isHighlighted = true
        ),
        HelpTopic(
            id = "telegram_community",
            category = HelpCategory.GETTING_STARTED,
            titleRes = R.string.help_topic_community_title,
            summaryRes = R.string.help_topic_community_summary,
            contentRes = R.string.help_topic_community_content,
            icon = Icons.Default.Public,
            isHighlighted = true
        ),
        HelpTopic(
            id = "offline_mode",
            category = HelpCategory.GETTING_STARTED,
            titleRes = R.string.help_topic_offline_title,
            summaryRes = R.string.help_topic_offline_summary,
            contentRes = R.string.help_topic_offline_content,
            icon = Icons.Default.WifiOff,
            isHighlighted = true
        ),
        HelpTopic(
            id = "first_chat_keys",
            category = HelpCategory.GETTING_STARTED,
            titleRes = R.string.help_topic_keys_title,
            summaryRes = R.string.help_topic_keys_summary,
            contentRes = R.string.help_topic_keys_content,
            icon = Icons.Default.QrCode
        ),
        HelpTopic(
            id = "voice_notes",
            category = HelpCategory.MESSAGING,
            titleRes = R.string.help_topic_audio_title,
            summaryRes = R.string.help_topic_audio_summary,
            contentRes = R.string.help_topic_audio_content,
            icon = Icons.Default.Mic,
            isHighlighted = true
        ),
        HelpTopic(
            id = "gps_location_sharing",
            category = HelpCategory.MESSAGING,
            titleRes = R.string.help_topic_gps_title,
            summaryRes = R.string.help_topic_gps_summary,
            contentRes = R.string.help_topic_gps_content,
            icon = Icons.Default.LocationOn,
            isHighlighted = true
        ),
        HelpTopic(
            id = "ephemeral_messages",
            category = HelpCategory.MESSAGING,
            titleRes = R.string.help_topic_ephemeral_title,
            summaryRes = R.string.help_topic_ephemeral_summary,
            contentRes = R.string.help_topic_ephemeral_content,
            icon = Icons.Default.Timer
        ),
        HelpTopic(
            id = "emoji_reactions_comments",
            category = HelpCategory.MESSAGING,
            titleRes = R.string.help_topic_reactions_title,
            summaryRes = R.string.help_topic_reactions_summary,
            contentRes = R.string.help_topic_reactions_content,
            icon = Icons.Default.Favorite
        ),
        HelpTopic(
            id = "e2ee_voice_calls",
            category = HelpCategory.CALLS,
            titleRes = R.string.help_topic_call_e2ee_title,
            summaryRes = R.string.help_topic_call_e2ee_summary,
            contentRes = R.string.help_topic_call_e2ee_content,
            icon = Icons.Default.Call,
            isHighlighted = true
        ),
        HelpTopic(
            id = "call_safety_number_sas",
            category = HelpCategory.CALLS,
            titleRes = R.string.help_topic_call_sas_title,
            summaryRes = R.string.help_topic_call_sas_summary,
            contentRes = R.string.help_topic_call_sas_content,
            icon = Icons.Default.Lock,
            isHighlighted = true
        ),
        HelpTopic(
            id = "call_secure_hangup",
            category = HelpCategory.CALLS,
            titleRes = R.string.help_topic_call_hangup_title,
            summaryRes = R.string.help_topic_call_hangup_summary,
            contentRes = R.string.help_topic_call_hangup_content,
            icon = Icons.Default.CallEnd
        ),
        HelpTopic(
            id = "p2p_social_feed",
            category = HelpCategory.SOCIAL,
            titleRes = R.string.help_topic_feed_title,
            summaryRes = R.string.help_topic_feed_summary,
            contentRes = R.string.help_topic_feed_content,
            icon = Icons.Default.Public
        ),
        HelpTopic(
            id = "p2p_polls",
            category = HelpCategory.SOCIAL,
            titleRes = R.string.help_topic_polls_title,
            summaryRes = R.string.help_topic_polls_summary,
            contentRes = R.string.help_topic_polls_content,
            icon = Icons.Default.BarChart,
            isHighlighted = true
        ),
        HelpTopic(
            id = "p2p_24h_stories",
            category = HelpCategory.SOCIAL,
            titleRes = R.string.help_topic_stories_title,
            summaryRes = R.string.help_topic_stories_summary,
            contentRes = R.string.help_topic_stories_content,
            icon = Icons.Default.Star
        ),
        HelpTopic(
            id = "dual_sim_management",
            category = HelpCategory.DUAL_SIM,
            titleRes = R.string.help_topic_dual_sim_title,
            summaryRes = R.string.help_topic_dual_sim_summary,
            contentRes = R.string.help_topic_dual_sim_content,
            icon = Icons.Default.SimCard,
            isHighlighted = true
        ),
        HelpTopic(
            id = "contacts_and_groups",
            category = HelpCategory.CONTACTS,
            titleRes = R.string.help_topic_contacts_title,
            summaryRes = R.string.help_topic_contacts_summary,
            contentRes = R.string.help_topic_contacts_content,
            icon = Icons.Default.Contacts
        ),
        HelpTopic(
            id = "sovereign_groups_circles",
            category = HelpCategory.CONTACTS,
            titleRes = R.string.help_topic_groups_circles_title,
            summaryRes = R.string.help_topic_groups_circles_summary,
            contentRes = R.string.help_topic_groups_circles_content,
            icon = Icons.Default.Groups,
            isHighlighted = true
        ),
        HelpTopic(
            id = "security_pin_vault",
            category = HelpCategory.SECURITY,
            titleRes = R.string.help_topic_security_title,
            summaryRes = R.string.help_topic_security_summary,
            contentRes = R.string.help_topic_security_content,
            icon = Icons.Default.Lock
        ),
        HelpTopic(
            id = "developer_console_telemetry",
            category = HelpCategory.SECURITY,
            titleRes = R.string.help_topic_devtools_title,
            summaryRes = R.string.help_topic_devtools_summary,
            contentRes = R.string.help_topic_devtools_content,
            icon = Icons.Default.Build,
            isHighlighted = true
        ),
        HelpTopic(
            id = "legal_privacy_terms",
            category = HelpCategory.SECURITY,
            titleRes = R.string.help_topic_legal_title,
            summaryRes = R.string.help_topic_legal_summary,
            contentRes = R.string.help_topic_legal_content,
            icon = Icons.Default.Policy,
            isHighlighted = true
        )
    )

    fun getQuickTips(): List<HelpQuickTip> = listOf(
        HelpQuickTip(
            id = "tip_guard_llm",
            textRes = R.string.help_tip_guard_llm,
            icon = Icons.Default.Security
        ),
        HelpQuickTip(
            id = "tip_reply_llm",
            textRes = R.string.help_tip_reply_llm,
            icon = Icons.Default.AutoAwesome
        ),
        HelpQuickTip(
            id = "tip_calls_e2ee",
            textRes = R.string.help_tip_calls_e2ee,
            icon = Icons.Default.Call
        ),
        HelpQuickTip(
            id = "tip_offline_ready",
            textRes = R.string.help_tip_offline_ready,
            icon = Icons.Default.WifiOff
        ),
        HelpQuickTip(
            id = "tip_dual_sim",
            textRes = R.string.help_tip_dual_sim_switch,
            icon = Icons.Default.SimCard
        ),
        HelpQuickTip(
            id = "tip_gps_offline",
            textRes = R.string.help_tip_gps_offline,
            icon = Icons.Default.LocationOn
        ),
        HelpQuickTip(
            id = "tip_polls_stories",
            textRes = R.string.help_tip_polls_stories,
            icon = Icons.Default.BarChart
        )
    )
}
