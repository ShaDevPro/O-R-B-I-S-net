package com.sha.orbis.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sha.orbis.social.UserSocialRole

@Composable
fun ConversationScreen(
    conversationTitle: String = "Discussion",
    conversationId: String = "conv_demo",
    recipientPhone: String = "",
    onBack: (() -> Unit)? = null,
    onOpenWall: ((phone: String, pseudo: String, avatar: String?, role: UserSocialRole) -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = conversationTitle)
    }
}
