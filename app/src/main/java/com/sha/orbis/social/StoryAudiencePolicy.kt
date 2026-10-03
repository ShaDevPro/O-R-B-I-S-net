package com.sha.orbis.social

import android.content.Context
import com.sha.orbis.storage.FriendCircleRepository
import com.sha.orbis.storage.FriendRequestRepository

/**
 * Local audience gate for ephemeral stories.
 *
 * Stories use the same lightweight social metadata as posts. The relay transports
 * the packet, and each client hides content that is outside the viewer's accepted
 * circle/exclusion rules.
 */
object StoryAudiencePolicy {
    fun isVisibleToCurrentUser(
        context: Context,
        story: SocialStory,
        currentPhone: String,
        accountId: String? = null
    ): Boolean {
        if (currentPhone.isBlank()) {
            return false
        }

        if (FriendRequestRepository.isSamePhone(story.authorPhone, currentPhone)) {
            return true
        }

        val friendRepo = FriendRequestRepository(context, accountId)
        val isFriend = friendRepo.isFriend(story.authorPhone) ||
            (!story.authorPubkey.isNullOrBlank() && friendRepo.isFriend(story.authorPubkey))
        if (!isFriend) {
            return false
        }

        if (story.excludedPhones.any { FriendRequestRepository.isSamePhone(it, currentPhone) }) {
            return false
        }

        val circleRepo = FriendCircleRepository(context, accountId)
        val viewerMatchedExcludedCircle = story.excludedCircleIds.any { circleId ->
            authorMatchesViewerCircle(circleRepo, story.authorPhone, circleId)
        }
        if (viewerMatchedExcludedCircle) {
            return false
        }

        val targetCircleId = story.targetCircleId
        return targetCircleId.isNullOrBlank() ||
            authorMatchesViewerCircle(circleRepo, story.authorPhone, targetCircleId)
    }

    private fun authorMatchesViewerCircle(
        circleRepo: FriendCircleRepository,
        authorPhone: String,
        circleId: String
    ): Boolean {
        if (circleId == "circle_family" && circleRepo.isFamilyMember(authorPhone)) {
            return true
        }
        val circle = circleRepo.loadCircles().firstOrNull { it.id == circleId } ?: return false
        return circle.memberPhones.any { FriendRequestRepository.isSamePhone(it, authorPhone) }
    }
}
