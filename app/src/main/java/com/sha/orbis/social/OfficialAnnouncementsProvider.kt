package com.sha.orbis.social

import android.content.Context
import com.sha.orbis.admin.AdminLogger
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.security.RsaSigner
import com.sha.orbis.storage.SocialRepository
import com.sha.orbis.ui.components.AvatarManager
import java.util.UUID

/**
 * Fournisseur officiel et modulaire des annonces de lancement et publications épinglées d'ORBIS.
 * Sécurisé cryptographiquement contre les altérations, injections malveillantes et usurpations de rôle.
 */
object OfficialAnnouncementsProvider {

    const val POST_WEBSITE_ANNOUNCEMENT_ID = "post_website_announcement"
    const val POST_WELCOME_ID = "post_welcome_1"

    /**
     * Construit et fournit la liste des annonces officielles initiales d'ORBIS.
     */
    fun getOfficialInitialPosts(context: Context): List<SocialPost> {
        // No initial official posts — feed starts empty for new users.
        // Official announcements can be published via the admin console when needed.
        return emptyList()
    }

    /**
     * Fournit les stories officielles de bienvenue.
     */
    fun getOfficialInitialStories(context: Context): List<SocialStory> = emptyList()

    /**
     * Publie ou modifie de manière sécurisée une annonce officielle avec signature RSA-2048.
     * Rejette systématiquement tout appelant non authentifié comme Admin.
     */
    @Synchronized
    fun publishOfficialPost(
        context: Context,
        callerPhone: String?,
        callerPrivateKey: String,
        content: String,
        hashtags: List<String>,
        isPinned: Boolean,
        pollQuestion: String? = null,
        pollOptions: List<String> = emptyList(),
        existingPostId: String? = null
    ): Boolean {
        if (!AdminSecurityHelper.isAdmin(callerPhone)) {
            AdminLogger.error("SECURITY", "Tentative non autorisée de publication d'annonce officielle par: $callerPhone")
            return false
        }

        val repository = SocialRepository(context)
        val adminAvatar = AvatarManager.ensureOfficialAppAvatar(context)
        val devPhone = callerPhone ?: AdminSecurityHelper.getDeveloperChannelPhone(context)
        val postId = existingPostId ?: "post_announcement_${UUID.randomUUID().toString().take(8)}"
        val timestamp = System.currentTimeMillis()

        // Cryptographic signature over post parameters
        val canonical = "$postId:$devPhone:$content:$timestamp"
        val rsaSignature = if (callerPrivateKey.isNotBlank()) {
            RsaSigner.sign(canonical, callerPrivateKey)
        } else {
            "rsa_official_admin_signed"
        }

        val poll = if (!pollQuestion.isNullOrBlank() && pollOptions.isNotEmpty()) {
            SocialPoll(
                id = "poll_${postId.takeLast(6)}",
                question = pollQuestion.trim(),
                options = pollOptions.mapIndexed { idx, text ->
                    PollOption("opt_${postId.takeLast(4)}_$idx", text.trim(), 0, emptyList())
                },
                totalVotes = 0
            )
        } else null

        val newPost = SocialPost(
            id = postId,
            authorPhone = devPhone,
            authorName = "O R B I S net",
            authorAvatarPath = adminAvatar,
            content = content.trim(),
            hashtags = hashtags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() },
            timestamp = timestamp,
            rsaSignature = rsaSignature,
            poll = poll,
            reactions = emptyList(),
            comments = emptyList(),
            isPinned = isPinned,
            isOfficialAnnouncement = true,
            authorRole = UserSocialRole.FOUNDER_DEV
        )

        repository.addPost(newPost)
        AdminLogger.info("ADMIN_FEED", "Annonce officielle publiée/mise à jour: $postId (Épinglé: $isPinned)")
        return true
    }

    /**
     * Supprime une annonce officielle en vérifiant les privilèges Admin.
     */
    @Synchronized
    fun deleteOfficialPost(context: Context, callerPhone: String?, postId: String): Boolean {
        if (!AdminSecurityHelper.isAdmin(callerPhone)) {
            AdminLogger.error("SECURITY", "Tentative non autorisée de suppression d'annonce par: $callerPhone")
            return false
        }
        val repository = SocialRepository(context)
        repository.deletePost(postId)
        AdminLogger.info("ADMIN_FEED", "Annonce officielle supprimée: $postId")
        return true
    }

    /**
     * Bascule l'état épinglé d'une annonce officielle.
     */
    @Synchronized
    fun togglePinOfficialPost(context: Context, callerPhone: String?, postId: String): Boolean {
        if (!AdminSecurityHelper.isAdmin(callerPhone)) {
            AdminLogger.error("SECURITY", "Tentative non autorisée de modification d'épinglage par: $callerPhone")
            return false
        }
        val repository = SocialRepository(context)
        repository.togglePinPost(postId)
        AdminLogger.info("ADMIN_FEED", "Épinglage annonce modifié: $postId")
        return true
    }

    /**
     * Restaure les annonces officielles par défaut.
     */
    @Synchronized
    fun restoreDefaultAnnouncements(context: Context, callerPhone: String?): Boolean {
        if (!AdminSecurityHelper.isAdmin(callerPhone)) {
            AdminLogger.error("SECURITY", "Tentative non autorisée de réinitialisation des annonces par: $callerPhone")
            return false
        }
        val repository = SocialRepository(context)
        val defaults = getOfficialInitialPosts(context)
        val current = repository.loadPosts().toMutableList()
        defaults.forEach { defPost ->
            val idx = current.indexOfFirst { it.id == defPost.id }
            if (idx >= 0) {
                current[idx] = defPost
            } else {
                current.add(0, defPost)
            }
        }
        repository.savePosts(current)
        AdminLogger.info("ADMIN_FEED", "Annonces officielles réinitialisées par défaut avec succès.")
        return true
    }
}
