package com.sha.orbis.social.algorithm

import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.UserSocialRole
import kotlin.math.ln
import kotlin.math.pow

data class FeedRankerConfig(
    val freshWindowMs: Long = 60 * 60 * 1000L,
    val activityWindowMs: Long = 48 * 60 * 60 * 1000L,
    val futureClockSkewGraceMs: Long = 5 * 60 * 1000L,
    val minimumReasonableTimestampMs: Long = 1_262_304_000_000L, // 2010-01-01
    val freshnessBaseScore: Double = 45.0,
    val freshFriendBoost: Double = 30.0,
    val explorationJitterMax: Double = 0.35
)

class FeedRanker(
    private val config: FeedRankerConfig = FeedRankerConfig()
) {
    fun rankForYou(
        posts: List<SocialPost>,
        nowMillis: Long = System.currentTimeMillis(),
        userKey: String,
        declaredInterests: List<String>,
        implicitInterestWeights: Map<String, Float>,
        categoryProvider: (SocialPost) -> List<String>,
        isCloseAuthor: (SocialPost) -> Boolean
    ): List<ScoredPost> {
        return posts.map { post ->
            val categories = categoryProvider(post)
            val matchingTags = matchingInterests(categories, declaredInterests, implicitInterestWeights)
            val closeAuthor = isCloseAuthor(post)
            val engagement = engagementScore(post)
            val activity = recentActivityScore(post, nowMillis)
            val freshness = freshnessScore(post, nowMillis)
            val freshBoost = freshBoost(post, nowMillis)
            val interest = interestScore(categories, declaredInterests, implicitInterestWeights)
            val authorAffinity = if (closeAuthor) 8.0 else 0.0
            val stableExploration = stableJitter(post.id, userKey, nowMillis)

            val finalScore = freshness +
                freshBoost +
                activity +
                engagement +
                interest +
                authorAffinity +
                stableExploration

            val reason = when {
                post.isOfficialAnnouncement || post.authorRole == UserSocialRole.FOUNDER_DEV || post.isPinned ->
                    RecommendationReason.OFFICIAL_ANNOUNCEMENT
                matchingTags.isNotEmpty() -> RecommendationReason.USER_INTEREST_MATCH
                activity + engagement >= 18.0 -> RecommendationReason.TRENDING_VIRAL
                closeAuthor -> RecommendationReason.CIRCLE_AFFINITY
                else -> RecommendationReason.EXPLORATION
            }

            ScoredPost(
                post = post,
                finalScore = finalScore,
                reason = reason,
                matchingTags = matchingTags
            )
        }.sortedWith(scoreComparator())
    }

    fun rankTrending(
        posts: List<SocialPost>,
        nowMillis: Long = System.currentTimeMillis(),
        userKey: String,
        categoryProvider: (SocialPost) -> List<String>
    ): List<ScoredPost> {
        return posts.map { post ->
            val score = recentActivityScore(post, nowMillis) +
                (engagementScore(post) * 0.9) +
                (freshnessScore(post, nowMillis) * 0.35) +
                stableJitter(post.id, userKey, nowMillis)

            ScoredPost(
                post = post,
                finalScore = score,
                reason = RecommendationReason.TRENDING_VIRAL,
                matchingTags = categoryProvider(post)
            )
        }.sortedWith(scoreComparator())
    }

    fun effectivePublishTime(post: SocialPost, nowMillis: Long): Long {
        val received = post.receivedAt.takeIf { it > 0L } ?: post.timestamp
        val published = post.timestamp
        return when {
            published <= 0L -> received.coerceAtMost(nowMillis)
            published > nowMillis + config.futureClockSkewGraceMs -> received.coerceAtMost(nowMillis)
            published < config.minimumReasonableTimestampMs && received >= config.minimumReasonableTimestampMs ->
                received.coerceAtMost(nowMillis)
            else -> published
        }
    }

    private fun scoreComparator(): Comparator<ScoredPost> =
        compareByDescending<ScoredPost> { it.finalScore }
            .thenByDescending { it.post.timestamp }
            .thenBy { it.post.id }

    private fun freshnessScore(post: SocialPost, nowMillis: Long): Double {
        val ageHours = ageMillis(effectivePublishTime(post, nowMillis), nowMillis) / 3_600_000.0
        return config.freshnessBaseScore / (1.0 + (ageHours / 12.0).pow(1.35))
    }

    private fun freshBoost(post: SocialPost, nowMillis: Long): Double {
        val age = ageMillis(effectivePublishTime(post, nowMillis), nowMillis)
        if (age > config.freshWindowMs) return 0.0
        val remaining = 1.0 - (age.toDouble() / config.freshWindowMs.toDouble())
        return config.freshFriendBoost * remaining.coerceIn(0.0, 1.0)
    }

    private fun recentActivityScore(post: SocialPost, nowMillis: Long): Double {
        val reactions = post.reactions.sumOf { activityWeight(it.timestamp, nowMillis) * 5.0 }
        val comments = post.comments.sumOf { comment ->
            (activityWeight(comment.timestamp, nowMillis) * 18.0) +
                comment.reactions.sumOf { activityWeight(it.timestamp, nowMillis) * 3.0 }
        }
        val poll = (post.poll?.totalVotes ?: 0).coerceAtMost(100) * 0.35
        val reposts = post.repostsCount.coerceAtMost(100) * 0.8
        return reactions + comments + poll + reposts
    }

    private fun engagementScore(post: SocialPost): Double {
        val total = (post.reactions.size * 1.5) +
            (post.comments.size * 3.0) +
            ((post.poll?.totalVotes ?: 0) * 1.8) +
            (post.repostsCount * 4.0)
        return ln(1.0 + total) * 3.0
    }

    private fun interestScore(
        categories: List<String>,
        declaredInterests: List<String>,
        implicitInterestWeights: Map<String, Float>
    ): Double {
        val declared = declaredInterests.map { it.lowercase() }
        val declaredMatches = categories.count { category ->
            declared.any { it.contains(category.lowercase()) || category.lowercase().contains(it) }
        }
        val implicit = categories.sumOf { (implicitInterestWeights[it] ?: 0f).toDouble().coerceAtMost(20.0) }
        return (declaredMatches * 6.0) + (implicit * 1.1)
    }

    private fun matchingInterests(
        categories: List<String>,
        declaredInterests: List<String>,
        implicitInterestWeights: Map<String, Float>
    ): List<String> {
        val declared = declaredInterests.map { it.lowercase() }
        return categories.filter { category ->
            declared.any { it.contains(category.lowercase()) || category.lowercase().contains(it) } ||
                (implicitInterestWeights[category] ?: 0f) > 1.0f
        }
    }

    private fun activityWeight(timestamp: Long, nowMillis: Long): Double {
        if (timestamp <= 0L) return 0.0
        val age = ageMillis(timestamp, nowMillis)
        if (age > config.activityWindowMs) return 0.0
        val ageHours = age / 3_600_000.0
        return 1.0 / (1.0 + (ageHours / 8.0).pow(1.25))
    }

    private fun ageMillis(timestamp: Long, nowMillis: Long): Long =
        (nowMillis - timestamp).coerceAtLeast(0L)

    private fun stableJitter(postId: String, userKey: String, nowMillis: Long): Double {
        val dayBucket = nowMillis / 86_400_000L
        val seed = "$postId|$userKey|$dayBucket"
        var hash = 1_125_899_906_842_597L
        seed.forEach { ch -> hash = (31L * hash) + ch.code.toLong() }
        val normalized = ((hash ushr 1) % 1000L).toDouble() / 1000.0
        return normalized * config.explorationJitterMax
    }
}
