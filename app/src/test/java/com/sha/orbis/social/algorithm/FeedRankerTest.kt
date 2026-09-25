package com.sha.orbis.social.algorithm

import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.SocialReaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRankerTest {
    private val now = 1_760_000_000_000L
    private val ranker = FeedRanker()

    @Test
    fun rankForYou_recentPostBeatsStaleEngagement() {
        val recent = post("recent", now - 5 * 60_000L)
        val stale = post(
            id = "stale",
            timestamp = now - 7 * 86_400_000L,
            reactions = (1..80).map { reaction("stale", now - 6 * 86_400_000L - it) },
            comments = (1..12).map { comment("stale", now - 6 * 86_400_000L - it) }
        )

        val ranked = ranker.rankForYou(listOf(stale, recent))

        assertEquals("recent", ranked.first().post.id)
    }

    @Test
    fun rankForYou_oldPostWithFreshDiscussionCanSurfaceAboveNewQuietPost() {
        val recentQuiet = post("recent_quiet", now - 25 * 60_000L)
        val activeOld = post(
            id = "active_old",
            timestamp = now - 2 * 86_400_000L,
            comments = (1..5).map { comment("active_old", now - it * 60_000L) }
        )

        val ranked = ranker.rankForYou(listOf(recentQuiet, activeOld))

        assertEquals("active_old", ranked.first().post.id)
    }

    @Test
    fun rankForYou_isStableForSameInputs() {
        val posts = listOf(
            post("a", now - 10 * 60_000L),
            post("b", now - 20 * 60_000L),
            post("c", now - 30 * 60_000L)
        )

        val first = ranker.rankForYou(posts)
        val second = ranker.rankForYou(posts)

        assertEquals(first.map { it.post.id }, second.map { it.post.id })
        assertEquals(first.map { it.finalScore }, second.map { it.finalScore })
    }

    @Test
    fun effectivePublishTime_usesReceivedAtWhenTimestampIsInFuture() {
        val futureClockPost = post(
            id = "future",
            timestamp = now + 2 * 86_400_000L,
            receivedAt = now - 60_000L
        )

        assertEquals(now - 60_000L, ranker.effectivePublishTime(futureClockPost, now))
    }

    @Test
    fun socialPostJson_preservesReceivedAtWithBackwardCompatibleDefault() {
        val original = post("json", now - 10_000L, receivedAt = now - 5_000L)
        val parsed = SocialPost.fromJson(original.toJson())
        val legacyJson = original.toJson().apply { remove("receivedAt") }
        val legacyParsed = SocialPost.fromJson(legacyJson)

        assertEquals(original.receivedAt, parsed.receivedAt)
        assertTrue(legacyParsed.receivedAt > 0L)
        assertEquals(original.timestamp, legacyParsed.receivedAt)
    }

    private fun FeedRanker.rankForYou(posts: List<SocialPost>): List<ScoredPost> =
        rankForYou(
            posts = posts,
            nowMillis = now,
            userKey = "test-user",
            declaredInterests = emptyList(),
            implicitInterestWeights = emptyMap(),
            categoryProvider = { emptyList() },
            isCloseAuthor = { false }
        )

    private fun post(
        id: String,
        timestamp: Long,
        receivedAt: Long = timestamp,
        reactions: List<SocialReaction> = emptyList(),
        comments: List<SocialComment> = emptyList()
    ): SocialPost = SocialPost(
        id = id,
        authorPhone = "+1000",
        authorName = "Friend",
        content = "Post $id",
        timestamp = timestamp,
        reactions = reactions,
        comments = comments,
        receivedAt = receivedAt
    )

    private fun reaction(targetId: String, timestamp: Long): SocialReaction =
        SocialReaction(
            id = "r_$targetId$timestamp",
            targetId = targetId,
            userPhone = "+2000",
            emoji = "like",
            timestamp = timestamp
        )

    private fun comment(postId: String, timestamp: Long): SocialComment =
        SocialComment(
            id = "c_$postId$timestamp",
            postId = postId,
            authorPhone = "+3000",
            authorName = "Friend",
            text = "Comment",
            timestamp = timestamp
        )
}
