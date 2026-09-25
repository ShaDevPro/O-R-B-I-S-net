package com.sha.orbis.social.algorithm

import android.content.Context
import com.sha.orbis.data.SessionManager
import com.sha.orbis.social.SocialPost
import com.sha.orbis.storage.FriendCircleRepository
import com.sha.orbis.storage.FriendRequestRepository

enum class RecommendationReason {
    OFFICIAL_ANNOUNCEMENT,
    USER_INTEREST_MATCH,
    TRENDING_VIRAL,
    CIRCLE_AFFINITY,
    EXPLORATION,
    CHRONOLOGICAL
}

data class ScoredPost(
    val post: SocialPost,
    val finalScore: Double,
    val reason: RecommendationReason,
    val matchingTags: List<String> = emptyList()
)

class FeedRecommendationEngine(
    private val context: Context,
    private val sessionManager: SessionManager
) {
    private val circleRepo = FriendCircleRepository(context)
    private val feedRanker = FeedRanker()

    companion object {
        // Semantic Keywords dictionary for Interest Categorization
        val CATEGORY_KEYWORDS: Map<String, List<String>> = mapOf(
            "Tech" to listOf("tech", "code", "dev", "android", "software", "ai", "ia", "linux", "gsm", "kotlin", "java", "algo", "python", "app", "ordinateur", "programme"),
            "Crypto" to listOf("crypto", "rsa", "e2ee", "security", "chiffre", "bitcoin", "sovereign", "privacy", "cle", "securite", "p2p", "offline", "decentralise"),
            "Art" to listOf("art", "design", "ui", "ux", "photo", "drawing", "creatif", "peinture", "style", "graphisme", "illustration"),
            "Business" to listOf("business", "startup", "entrepreneur", "marketing", "finance", "argent", "vente", "commerce", "travail", "projet", "entreprise"),
            "Sport" to listOf("sport", "fitness", "football", "gym", "workout", "sante", "match", "running", "musculation", "entrainement"),
            "Music" to listOf("music", "musique", "son", "audio", "chanson", "concert", "rap", "melody", "album", "guitare", "piano"),
            "Science" to listOf("science", "education", "physique", "chimie", "etude", "livre", "math", "recherche", "universite", "savoir", "cours"),
            "Nature" to listOf("nature", "voyage", "travel", "montagne", "mer", "rando", "camping", "ecologie", "paysage", "aventure", "soleil")
        )
    }

    /**
     * Extracts interest categories from a post based on its hashtags and content text.
     */
    fun extractCategories(post: SocialPost): List<String> {
        val extracted = mutableSetOf<String>()
        val textLower = (post.content + " " + post.hashtags.joinToString(" ")).lowercase()

        for ((category, keywords) in CATEGORY_KEYWORDS) {
            for (kw in keywords) {
                if (textLower.contains(kw)) {
                    extracted.add(category)
                    break
                }
            }
        }
        return extracted.toList()
    }

    /**
     * Resolves matching declared and implicit interest categories for the current user.
     */
    private fun getMatchingInterests(post: SocialPost): List<String> {
        val postCategories = extractCategories(post)
        val userDeclared = sessionManager.userInterests.map { it.lowercase() }
        val userImplicit = sessionManager.getImplicitInterestWeights()

        return postCategories.filter { cat ->
            userDeclared.any { it.contains(cat.lowercase()) } || (userImplicit[cat] ?: 0f) > 1.0f
        }
    }

    /**
     * Compute TikTok / FB style "Pour Vous" (For You) Algorithmic Ranking:
     * - Vector Interest Affinity (Declared + Implicit weights)
     * - EdgeRank Social Interaction Velocity (Likes, Comments, Votes, Reposts)
     * - Non-linear Time Decay
     * - Serendipity & Exploration pool injection (15%)
     */
    fun rankForYouFeed(posts: List<SocialPost>): List<ScoredPost> {
        val sharedCircles = circleRepo.loadCircles().filter { c ->
            c.memberPhones.any { FriendRequestRepository.isSamePhone(it, sessionManager.userPhone) }
        }

        return feedRanker.rankForYou(
            posts = posts,
            userKey = sessionManager.activeAccountId.ifBlank { sessionManager.userPhone },
            declaredInterests = sessionManager.userInterests,
            implicitInterestWeights = sessionManager.getImplicitInterestWeights(),
            categoryProvider = ::extractCategories,
            isCloseAuthor = { post ->
                sharedCircles.any { circle ->
                    circle.memberPhones.any { FriendRequestRepository.isSamePhone(it, post.authorPhone) }
                }
            }
        )
    }

    /**
     * Compute "Tendances" (Trending) feed based strictly on Interaction Velocity & Activity.
     */
    fun rankTrendingFeed(posts: List<SocialPost>): List<ScoredPost> {
        return feedRanker.rankTrending(
            posts = posts,
            userKey = sessionManager.activeAccountId.ifBlank { sessionManager.userPhone },
            categoryProvider = ::extractCategories
        )
    }

    /**
     * Filter posts by a specific interest category (e.g. "Tech", "Crypto", "Art").
     */
    fun filterByInterestCategory(posts: List<SocialPost>, categoryTag: String): List<SocialPost> {
        if (categoryTag.isBlank() || categoryTag.equals("Tous", ignoreCase = true) || categoryTag.equals("All", ignoreCase = true)) {
            return posts
        }
        val target = categoryTag.lowercase()
        return posts.filter { post ->
            val categories = extractCategories(post).map { it.lowercase() }
            categories.any { it.contains(target) || target.contains(it) } ||
                    post.hashtags.any { it.lowercase().contains(target) || target.contains(it.lowercase()) }
        }
    }

    /**
     * Learn from user interactions (Like, Comment, Vote) to reinforce implicit interest vector.
     */
    fun recordInteraction(post: SocialPost, weight: Float = 1.0f) {
        val categories = extractCategories(post)
        for (cat in categories) {
            sessionManager.recordImplicitInterestInteraction(cat, weight)
        }
    }
}
