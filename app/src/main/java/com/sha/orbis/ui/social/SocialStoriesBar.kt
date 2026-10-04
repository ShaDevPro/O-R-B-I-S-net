package com.sha.orbis.ui.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sha.orbis.social.SocialStory
import com.sha.orbis.social.UserStoryGroup

data class StoryViewerSession(
    val groups: List<UserStoryGroup>,
    val initialGroupIndex: Int,
    val initialStoryIndex: Int
)

@Composable
fun SocialStoriesBar(
    stories: List<SocialStory>,
    userAvatarPath: String?,
    userName: String,
    currentPhone: String = "",
    onAddStory: (
        content: String,
        mediaPath: String?,
        mediaBase64: String?,
        gradientIndex: Int,
        mediaType: String?,
        mediaUrl: String?,
        targetCircleId: String?,
        excludedCircleIds: List<String>,
        excludedPhones: List<String>
    ) -> Unit,
    onDeleteStory: ((String) -> Unit)? = null,
    onStorySeen: ((SocialStory) -> Unit)? = null,
    onStoryReact: ((SocialStory, String) -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxWidth().height(90.dp))
}

@Composable
internal fun StoryViewerDialog(
    groups: List<UserStoryGroup>,
    initialGroupIndex: Int,
    initialStoryIndex: Int,
    currentPhone: String,
    onDismiss: () -> Unit,
    onDeleteStory: (String) -> Unit,
    onStorySeen: (SocialStory) -> Unit,
    onStoryReact: (SocialStory, String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize())
}
