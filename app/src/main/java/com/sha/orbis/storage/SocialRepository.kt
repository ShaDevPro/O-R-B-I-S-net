package com.sha.orbis.storage

import android.content.Context
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.social.OfficialAnnouncementsProvider
import com.sha.orbis.social.PollOption
import com.sha.orbis.social.SocialComment
import com.sha.orbis.social.SocialPoll
import com.sha.orbis.social.SocialPost
import com.sha.orbis.social.SocialReaction
import com.sha.orbis.social.SocialStory
import com.sha.orbis.social.UserSocialRole
import com.sha.orbis.ui.components.AvatarManager
import org.json.JSONArray
import java.io.File
import java.util.UUID

class SocialRepository(
    private val context: Context,
    private val accountId: String? = null
) {
    private val currentAccountId: String = accountId?.takeIf { it.isNotBlank() } ?: try {
        com.sha.orbis.data.SessionManager(context).activeAccountId
    } catch (_: Exception) { "" }

    companion object {
        private val accountCachedPosts = java.util.concurrent.ConcurrentHashMap<String, List<SocialPost>>()
        private val accountCachedStories = java.util.concurrent.ConcurrentHashMap<String, List<SocialStory>>()

        /** Single-thread executor for async disk writes — prevents ANR when sync floods addPost. */
        private val diskWriteExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "SocialRepo-DiskWriter").apply { isDaemon = true }
        }

        fun invalidateCache() {
            accountCachedPosts.clear()
            accountCachedStories.clear()
        }

        fun invalidateCacheForAccount(accountId: String) {
            accountCachedPosts.remove(accountId)
            accountCachedStories.remove(accountId)
        }
    }

    private val postsFile: File by lazy { AccountStorageManager.getAccountFile(context, "social_posts.json", currentAccountId) }
    private val storiesFile: File by lazy { AccountStorageManager.getAccountFile(context, "social_stories.json", currentAccountId) }
    private val deletedPostsFile: File by lazy { AccountStorageManager.getAccountFile(context, "deleted_post_ids.json", currentAccountId) }

    private val deletedPostIds: MutableSet<String> = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    @Volatile
    private var deletedPostsLoaded = false

    /** Comments/reactions received before the parent post exists locally (Nostr ordering). */
    private val pendingComments = mutableListOf<SocialComment>()
    private val pendingReactions = mutableListOf<Triple<String, String, String>>()
    private val pendingCommentReactions = mutableListOf<Triple<String, String, Pair<String, String>>>()
    private val pendingPollVotes = mutableListOf<Triple<String, String, String>>()

    private fun ensureDeletedPostsLoaded() {
        if (deletedPostsLoaded) return
        synchronized(deletedPostIds) {
            if (deletedPostsLoaded) return
            try {
                if (deletedPostsFile.exists()) {
                    val content = deletedPostsFile.readText().trim()
                    if (content.isNotEmpty()) {
                        val array = JSONArray(content)
                        for (i in 0 until array.length()) {
                            val id = array.optString(i)?.trim()
                            if (!id.isNullOrBlank()) {
                                deletedPostIds.add(id)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("SocialRepository", "Erreur lecture deletedPostIds: ${e.message}")
            }
            deletedPostsLoaded = true
        }
    }

    private fun saveDeletedPostIds() {
        try {
            val array = JSONArray()
            synchronized(deletedPostIds) {
                deletedPostIds.forEach { array.put(it) }
            }
            deletedPostsFile.writeText(array.toString())
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "Erreur sauvegarde deletedPostIds: ${e.message}")
        }
    }

    @Synchronized
    fun isPostDeleted(postId: String?): Boolean {
        if (postId.isNullOrBlank()) return false
        ensureDeletedPostsLoaded()
        val clean = postId.trim()
        if (deletedPostIds.contains(clean)) return true
        val hex = try {
            com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(clean)
        } catch (_: Exception) { null }
        if (!hex.isNullOrBlank() && deletedPostIds.contains(hex)) return true
        return false
    }

    @Synchronized
    fun markPostAsDeleted(postId: String) {
        if (postId.isBlank()) return
        ensureDeletedPostsLoaded()
        val clean = postId.trim()
        var modified = deletedPostIds.add(clean)
        val hex = try {
            com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(clean)
        } catch (_: Exception) { null }
        if (!hex.isNullOrBlank() && deletedPostIds.add(hex)) {
            modified = true
        }
        if (modified) {
            saveDeletedPostIds()
        }
    }

    @Synchronized
    fun deletePost(postId: String) {
        ensureDeletedPostsLoaded()
        markPostAsDeleted(postId)
        val current = loadPosts().toMutableList()
        val toDelete = current.filter {
            it.id == postId ||
            (postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(postId, ignoreCase = true)) ||
            isPostDeleted(it.id)
        }
        toDelete.forEach { post ->
            markPostAsDeleted(post.id)
            post.mediaPath?.let { path ->
                try {
                    com.sha.orbis.media.VideoMediaHelper.deleteVideoPhysical(context, path)
                    val file = java.io.File(path)
                    if (file.exists() && file.isFile) file.delete()
                } catch (_: Exception) {}
            }
        }
        current.removeAll(toDelete.toSet())
        savePosts(current)

        // Purge cascade des notifications liées au post supprimé
        try {
            val notifRepo = NotificationRepository(context)
            notifRepo.deletePostNotifications(postId)
            toDelete.forEach { notifRepo.deletePostNotifications(it.id) }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun togglePinPost(postId: String) {
        val current = loadPosts().toMutableList()
        val index = current.indexOfFirst {
            it.id == postId || (postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(postId, ignoreCase = true))
        }
        if (index >= 0) {
            val old = current[index]
            current[index] = old.copy(isPinned = !old.isPinned)
            savePosts(current)
        }
    }

    @Synchronized
    fun repost(originalPost: SocialPost, currentUserPhone: String, currentUserName: String, currentUserAvatar: String?): SocialPost {
        val current = loadPosts().toMutableList()
        val origIdx = current.indexOfFirst { it.id == originalPost.id }
        if (origIdx >= 0) {
            current[origIdx] = current[origIdx].copy(repostsCount = current[origIdx].repostsCount + 1)
        }
        val repostItem = SocialPost(
            id = "repost_${UUID.randomUUID().toString().take(8)}",
            authorPhone = currentUserPhone,
            authorName = currentUserName.ifBlank { "Moi" },
            authorAvatarPath = currentUserAvatar,
            content = originalPost.content,
            hashtags = originalPost.hashtags,
            timestamp = System.currentTimeMillis(),
            targetCircleId = originalPost.targetCircleId,
            rsaSignature = "rsa_repost_sig",
            poll = originalPost.poll,
            reactions = emptyList(),
            comments = emptyList(),
            authorRole = originalPost.authorRole,
            repostAuthorName = originalPost.authorName,
            repostAuthorPhone = originalPost.authorPhone,
            repostOriginalPostId = originalPost.id
        )
        current.add(0, repostItem)
        savePosts(current)
        return repostItem
    }

    private fun matchesPostId(storedId: String, referenceId: String): Boolean {
        if (storedId == referenceId) return true
        if (referenceId.length == 64) {
            return com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(storedId)
                .equals(referenceId, ignoreCase = true)
        }
        if (storedId.length == 64) {
            return com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(referenceId)
                .equals(storedId, ignoreCase = true)
        }
        return false
    }

    private fun findPostIndex(posts: List<SocialPost>, postId: String): Int =
        posts.indexOfFirst { matchesPostId(it.id, postId) }

    fun findPostById(postId: String?): SocialPost? {
        if (postId.isNullOrBlank()) return null
        return loadPosts().find { matchesPostId(it.id, postId) }
    }

    private fun mergeReactions(
        existing: List<SocialReaction>,
        incoming: List<SocialReaction>
    ): List<SocialReaction> {
        if (incoming.isEmpty()) return existing
        val merged = existing.toMutableList()
        for (reaction in incoming) {
            val idx = merged.indexOfFirst {
                FriendRequestRepository.isSamePhone(it.userPhone, reaction.userPhone)
            }
            if (idx >= 0) {
                merged[idx] = reaction
            } else {
                merged.add(reaction)
            }
        }
        return merged
    }

    private fun mergeComments(
        existing: List<SocialComment>,
        incoming: List<SocialComment>
    ): List<SocialComment> {
        if (incoming.isEmpty()) return existing
        val byId = existing.associateBy { it.id }.toMutableMap()
        for (incomingComment in incoming) {
            val stored = byId[incomingComment.id]
            if (stored == null) {
                byId[incomingComment.id] = incomingComment
            } else {
                val mergedReactions = mergeReactions(stored.reactions, incomingComment.reactions)
                val bestReplyToId = stored.replyToCommentId?.takeIf { it.isNotBlank() } ?: incomingComment.replyToCommentId
                val bestReplyToName = stored.replyToAuthorName?.takeIf { it.isNotBlank() } ?: incomingComment.replyToAuthorName
                val locallyEdited = stored.isEdited
                val locallyEditedAt = stored.editedAt
                val incomingEdited = incomingComment.isEdited && (incomingComment.editedAt ?: 0L) > 0L
                val keepLocalEdit = locallyEdited && (!incomingEdited || (locallyEditedAt ?: 0L) >= (incomingComment.editedAt ?: 0L))
                byId[incomingComment.id] = stored.copy(
                    text = if (keepLocalEdit) stored.text else incomingComment.text.ifBlank { stored.text },
                    isEdited = locallyEdited || incomingEdited,
                    editedAt = maxOf(locallyEditedAt ?: 0L, incomingComment.editedAt ?: 0L).takeIf { it > 0L },
                    replyToCommentId = bestReplyToId,
                    replyToAuthorName = bestReplyToName,
                    reactions = mergedReactions
                )
            }
        }
        return byId.values.sortedBy { it.timestamp }
    }

    private fun mergePolls(
        existing: SocialPoll?,
        incoming: SocialPoll?
    ): SocialPoll? {
        if (existing == null) return incoming
        if (incoming == null) return existing

        // Conserver impérativement l'option votée localement par l'utilisateur courant
        val localUserVotedId = existing.userVotedOptionId ?: incoming.userVotedOptionId

        // Fusionner les options et leurs votants sans jamais perdre de votes
        val mergedOptions = incoming.options.map { incOpt ->
            val exOpt = existing.options.find { it.id == incOpt.id || it.text.equals(incOpt.text, ignoreCase = true) }
            val exVoters = exOpt?.votersPhones ?: emptyList()
            val incVoters = incOpt.votersPhones

            val combinedVoters = (exVoters + incVoters).distinctBy { voter ->
                val trimmed = voter.trim()
                if (trimmed.startsWith("npub1", ignoreCase = true) || (trimmed.length == 64 && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' })) {
                    trimmed.lowercase()
                } else {
                    val digits = trimmed.filter { it.isDigit() }
                    if (digits.length >= 8) digits.takeLast(8) else trimmed
                }
            }
            val count = maxOf(combinedVoters.size, exOpt?.voteCount ?: 0, incOpt.voteCount)
            incOpt.copy(
                voteCount = count,
                votersPhones = combinedVoters
            )
        }.toMutableList()

        existing.options.forEach { exOpt ->
            if (mergedOptions.none { it.id == exOpt.id || it.text.equals(exOpt.text, ignoreCase = true) }) {
                mergedOptions.add(exOpt)
            }
        }

        val totalVotes = maxOf(
            mergedOptions.sumOf { it.voteCount },
            existing.totalVotes,
            incoming.totalVotes
        )

        return incoming.copy(
            options = mergedOptions,
            totalVotes = totalVotes,
            userVotedOptionId = localUserVotedId
        )
    }

    @Synchronized
    fun enqueuePendingComment(comment: SocialComment): Boolean {
        if (pendingComments.any { it.id == comment.id }) return true
        pendingComments.add(comment)
        return true
    }

    @Synchronized
    fun enqueuePendingReaction(postId: String, userPhone: String, emoji: String): Boolean {
        if (pendingReactions.any {
                it.first == postId &&
                    FriendRequestRepository.isSamePhone(it.second, userPhone) &&
                    it.third == emoji
            }
        ) {
            return true
        }
        pendingReactions.add(Triple(postId, userPhone, emoji))
        return true
    }

    @Synchronized
    fun enqueuePendingCommentReaction(postId: String, commentId: String, userPhone: String, emoji: String): Boolean {
        if (pendingCommentReactions.any {
                it.first == postId && it.second == commentId &&
                    FriendRequestRepository.isSamePhone(it.third.first, userPhone) &&
                    it.third.second == emoji
            }
        ) {
            return true
        }
        pendingCommentReactions.add(Triple(postId, commentId, userPhone to emoji))
        return true
    }

    @Synchronized
    fun enqueuePendingPollVote(postId: String, optionId: String, voterPhone: String): Boolean {
        if (pendingPollVotes.any {
                it.first == postId &&
                    it.second == optionId &&
                    FriendRequestRepository.isSamePhone(it.third, voterPhone)
            }
        ) {
            return true
        }
        pendingPollVotes.add(Triple(postId, optionId, voterPhone))
        return true
    }

    @Synchronized
    private fun flushPendingEngagements(resolvedPostId: String) {
        if (pendingComments.isEmpty() && pendingReactions.isEmpty() && pendingCommentReactions.isEmpty() && pendingPollVotes.isEmpty()) return
        val commentBatch = pendingComments.filter {
            matchesPostId(it.postId, resolvedPostId) ||
                com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.postId).let { hex ->
                    hex.equals(resolvedPostId, ignoreCase = true) || matchesPostId(resolvedPostId, hex)
                }
        }
        pendingComments.removeAll(commentBatch.toSet())
        commentBatch.forEach { addComment(resolvedPostId, it) }

        val reactionBatch = pendingReactions.filter { matchesPostId(it.first, resolvedPostId) }
        pendingReactions.removeAll(reactionBatch.toSet())
        reactionBatch.forEach { (pid, phone, emoji) ->
            applyIncomingReaction(pid, phone, emoji)
        }

        val commentReactionBatch = pendingCommentReactions.filter { matchesPostId(it.first, resolvedPostId) }
        pendingCommentReactions.removeAll(commentReactionBatch.toSet())
        commentReactionBatch.forEach { (pid, cid, userEmoji) ->
            applyIncomingCommentReaction(pid, cid, userEmoji.first, userEmoji.second)
        }

        val pollVoteBatch = pendingPollVotes.filter { matchesPostId(it.first, resolvedPostId) }
        pendingPollVotes.removeAll(pollVoteBatch.toSet())
        pollVoteBatch.forEach { (pid, optId, phone) ->
            applyIncomingPollVote(pid, optId, phone)
        }
    }

    /**
     * Idempotent reaction apply for Nostr sync (no toggle-off when the same emoji is replayed).
     */
    @Synchronized
    fun applyIncomingReaction(postId: String, userPhone: String, emoji: String): Boolean {
        ensureDeletedPostsLoaded()
        if (isPostDeleted(postId)) return false
        val current = loadPosts().toMutableList()
        val index = findPostIndex(current, postId)
        if (index < 0) return false
        val post = current[index]
        if (isPostDeleted(post.id)) return false
        val existingReactionIdx = post.reactions.indexOfFirst {
            FriendRequestRepository.isSamePhone(it.userPhone, userPhone)
        }
        val newReactions = post.reactions.toMutableList()
        if (existingReactionIdx >= 0) {
            val existing = newReactions[existingReactionIdx]
            if (existing.emoji == emoji) {
                return true
            }
            newReactions[existingReactionIdx] = SocialReaction(
                UUID.randomUUID().toString(),
                post.id,
                userPhone,
                emoji
            )
        } else {
            newReactions.add(
                SocialReaction(UUID.randomUUID().toString(), post.id, userPhone, emoji)
            )
        }
        current[index] = post.copy(reactions = newReactions)
        savePosts(current)
        return true
    }

    private fun sanitizePost(rawPost: SocialPost): SocialPost {
        val path = rawPost.mediaPath
        val isDirectoryOrCorrupt = if (path.isNullOrBlank()) false else {
            try {
                val f = java.io.File(path)
                f.isDirectory || !f.exists() || !f.isFile || f.length() == 0L
            } catch (_: Exception) { true }
        }

        val hasAnyMedia = !rawPost.mediaData.isNullOrBlank() || !rawPost.mediaUrl.isNullOrBlank()
        val shouldClearMediaType = (rawPost.mediaType == "image" || rawPost.mediaType == "video") &&
                isDirectoryOrCorrupt && !hasAnyMedia

        return if (isDirectoryOrCorrupt) {
            rawPost.copy(
                mediaPath = null,
                mediaType = if (shouldClearMediaType) null else rawPost.mediaType
            )
        } else rawPost
    }

    @Synchronized
    fun loadPosts(): List<SocialPost> {
        ensureDeletedPostsLoaded()
        accountCachedPosts[currentAccountId]?.let { cached ->
            var cacheModified = false
            val cleanCached = cached.filterNot { isPostDeleted(it.id) }.distinctBy { it.id }.map { post ->
                val sanitized = sanitizePost(post)
                if (sanitized != post) cacheModified = true
                sanitized
            }
            if (cleanCached.size != cached.size || cacheModified) {
                accountCachedPosts[currentAccountId] = cleanCached
                savePosts(cleanCached)
            }
            return cleanCached
        }

        if (!postsFile.exists()) {
            accountCachedPosts[currentAccountId] = emptyList()
            return emptyList()
        }
        val result = try {
            // Guard against truly pathological bloat (>50MB) — log a warning but don't delete
            if (postsFile.length() > 50 * 1024 * 1024L) {
                android.util.Log.w("SocialRepository", "postsFile very large (${postsFile.length()} bytes) — reading anyway to preserve data")
            }
            val array = JSONArray(postsFile.readText())
            val list = mutableListOf<SocialPost>()
            var needsResave = false
            for (i in 0 until array.length()) {
                val rawPost = SocialPost.fromJson(array.getJSONObject(i))
                val isSyncPacket = rawPost.content.startsWith("[ORBIS_PEER_SYNC_V1]") ||
                    rawPost.content.contains("\"action\":\"EXCHANGE_") ||
                    (rawPost.content.startsWith("{") && rawPost.content.contains("\"bundle\":{"))

                // Filter out all official Orbis announcements, deleted posts, and leaked sync packets
                if (!rawPost.isOfficialAnnouncement && !isPostDeleted(rawPost.id) && !isSyncPacket) {
                    val sanitizedPost = sanitizePost(rawPost)
                    if (sanitizedPost != rawPost) {
                        needsResave = true
                    }
                    list.add(sanitizedPost)
                }
            }

            // Deduplicate by ID
            val distinctList = list.distinctBy { it.id }
            if (distinctList.size != list.size) {
                needsResave = true
            }

            // If we just purged official posts, deleted posts, duplicate IDs, or sanitized corrupt media paths, persist the cleaned list
            val rawSize = array.length()
            if (rawSize > distinctList.size || needsResave) {
                savePosts(distinctList)
            }

            if (distinctList.isEmpty()) {
                emptyList()
            } else {
                distinctList.sortedWith(
                    compareByDescending<SocialPost> { it.isPinned }
                        .thenByDescending { it.timestamp }
                )
            }
        } catch (e: OutOfMemoryError) {
            System.gc()
            try { postsFile.delete() } catch (_: Exception) {}
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        accountCachedPosts[currentAccountId] = result
        return result
    }

    @Synchronized
    fun savePosts(posts: List<SocialPost>) {
        val sorted = posts.distinctBy { it.id }.sortedWith(
            compareByDescending<SocialPost> { it.isPinned }
                .thenByDescending { if (!it.isPinned && it.isOfficialAnnouncement) 0L else it.timestamp }
                .thenByDescending { it.timestamp }
        )
        // Strip mediaData from posts that already have a valid local file — mediaData (base64) is
        // only needed for Nostr transport, not for local disk. Keeping it would bloat the JSON file.
        val toSave = sorted.map { rawPost ->
            val post = sanitizePost(rawPost)
            val isLocalValid = !post.mediaPath.isNullOrBlank() && try {
                val f = java.io.File(post.mediaPath)
                f.exists() && f.isFile && f.length() > 0L
            } catch (_: Exception) { false }

            if (isLocalValid && !post.mediaData.isNullOrBlank()) {
                post.copy(mediaData = null)
            } else post
        }
        // Update in-memory cache immediately (fast, no I/O)
        accountCachedPosts[currentAccountId] = toSave
        // Async disk write — releases the lock so UI thread won't ANR
        val file = postsFile
        val snapshot = ArrayList(toSave)
        diskWriteExecutor.execute {
            try {
                file.bufferedWriter().use { writer ->
                    writer.write("[\n")
                    snapshot.forEachIndexed { index, post ->
                        if (index > 0) writer.write(",\n")
                        writer.write(post.toJson().toString())
                    }
                    writer.write("\n]")
                }
            } catch (e: OutOfMemoryError) {
                System.gc()
                android.util.Log.e("SocialRepository", "OOM avoided in savePosts: ${e.message}")
            } catch (e: Exception) {
                android.util.Log.e("SocialRepository", "Error in savePosts: ${e.message}")
            }
        }
    }

    @Synchronized
    fun addPost(post: SocialPost) {
        ensureDeletedPostsLoaded()
        if (isPostDeleted(post.id)) {
            android.util.Log.d("SocialRepository", "addPost rejeté : post marqué comme supprimé (${post.id})")
            return
        }
        if (post.content.startsWith("[ORBIS_PEER_SYNC_V1]") ||
            post.content.contains("\"action\":\"EXCHANGE_") ||
            (post.content.startsWith("{") && post.content.contains("\"bundle\":{"))
        ) {
            android.util.Log.w("SocialRepository", "addPost rejeté : paquet de synchronisation détecté dans le post")
            return
        }
        val current = loadPosts().toMutableList()
        val existingIdx = current.indexOfFirst { it.id == post.id }
        if (existingIdx >= 0) {
            val existing = current[existingIdx]
            // Preserve locally accumulated reactions, comments, and poll votes — these are
            // never included in the original Nostr Kind-1 event, so a re-broadcast must not
            // silently erase them. Only update content fields.
            // Also preserve the existing mediaPath if it already points to a valid local file —
            // avoids overwriting the author's own valid image path when their post bounces back via relay.
            val isExistingValidFile = !existing.mediaPath.isNullOrBlank() && try {
                val f = java.io.File(existing.mediaPath!!)
                f.exists() && f.isFile && f.length() > 0L
            } catch (_: Exception) { false }

            val isIncomingValidFile = !post.mediaPath.isNullOrBlank() && try {
                val f = java.io.File(post.mediaPath!!)
                f.exists() && f.isFile && f.length() > 0L
            } catch (_: Exception) { false }

            val bestMediaPath = if (isExistingValidFile) {
                existing.mediaPath
            } else if (isIncomingValidFile) {
                post.mediaPath
            } else {
                null
            }
            current[existingIdx] = existing.copy(
                // Contenu : garder l'existant si l'entrant est vide (relay renvoie content vide)
                content = post.content.ifBlank { existing.content },
                // Auteur : garder l'existant si l'entrant est un npub brut ou un placeholder
                authorName = if (post.authorName.isBlank() ||
                    post.authorName.startsWith("npub1", ignoreCase = true) ||
                    post.authorName == "Utilisateur OrbisNet" || post.authorName == "OrbisNet") {
                    existing.authorName.ifBlank { post.authorName }
                } else post.authorName,
                authorAvatarPath = post.authorAvatarPath ?: existing.authorAvatarPath,
                authorPhone = post.authorPhone.ifBlank { existing.authorPhone },
                authorPubkey = post.authorPubkey ?: existing.authorPubkey,
                // Engagement : fusionner, jamais écraser
                reactions = mergeReactions(existing.reactions, post.reactions),
                comments = mergeComments(existing.comments, post.comments),
                // Poll : fusionner intelligemment sans écraser les votes locaux ou distants
                poll = mergePolls(existing.poll, post.poll),
                // Média : bestMediaPath calculé plus haut, URL/type : garder le meilleur
                mediaPath = bestMediaPath,
                mediaUrl = post.mediaUrl ?: existing.mediaUrl,
                mediaType = post.mediaType ?: existing.mediaType,
                mediaData = null, // strip Base64 en mémoire
                // Champs locaux JAMAIS transmis via Nostr — toujours préserver
                hashtags = if (post.hashtags.isNotEmpty()) post.hashtags else existing.hashtags,
                isPinned = existing.isPinned,
                isEdited = existing.isEdited,
                editedAt = existing.editedAt,
                repostsCount = maxOf(existing.repostsCount, post.repostsCount),
                repostAuthorName = existing.repostAuthorName ?: post.repostAuthorName,
                repostAuthorPhone = existing.repostAuthorPhone ?: post.repostAuthorPhone,
                repostOriginalPostId = existing.repostOriginalPostId ?: post.repostOriginalPostId,
                rsaSignature = existing.rsaSignature.ifBlank { post.rsaSignature },
                authorRole = if (post.authorRole != com.sha.orbis.social.UserSocialRole.STANDARD) post.authorRole else existing.authorRole,
                // Cercles/exclusions : garder les données les plus complètes
                targetCircleId = post.targetCircleId ?: existing.targetCircleId,
                excludedCircleIds = if (post.excludedCircleIds.isNotEmpty()) post.excludedCircleIds else existing.excludedCircleIds,
                excludedPhones = if (post.excludedPhones.isNotEmpty()) post.excludedPhones else existing.excludedPhones,
                // Timestamps
                timestamp = if (post.timestamp > 0L) post.timestamp else existing.timestamp,
                receivedAt = existing.receivedAt.takeIf { it > 0L } ?: post.receivedAt
            )
            flushPendingEngagements(current[existingIdx].id)
        } else {
            val safePost = sanitizePost(post)
            current.add(0, safePost)
            flushPendingEngagements(safePost.id)

            // Télémétrie autonome du feed (Orbis vs Extra-Orbis, cercles privés)
            try {
                val isExtra = !safePost.id.startsWith("post_") && safePost.authorPhone.isBlank()
                val hasMedia = !safePost.mediaPath.isNullOrBlank() || !safePost.mediaUrl.isNullOrBlank()
                com.sha.orbis.telemetry.FeedTelemetryTracker.trackPostCreated(context, isExtraOrbis = isExtra, hasMedia = hasMedia)
                if (!safePost.targetCircleId.isNullOrBlank()) {
                    com.sha.orbis.telemetry.FeedTelemetryTracker.trackCircleAction(context)
                }
            } catch (_: Exception) {}
        }
        savePosts(current)
    }

    @Synchronized
    fun editPost(postId: String, newContent: String, newHashtags: List<String>): Boolean {
        val current = loadPosts().toMutableList()
        val index = current.indexOfFirst {
            it.id == postId || (postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(postId, ignoreCase = true))
        }
        if (index >= 0) {
            val oldPost = current[index]
            val updated = oldPost.copy(
                content = newContent.trim(),
                hashtags = newHashtags,
                isEdited = true,
                editedAt = System.currentTimeMillis()
            )
            current[index] = updated
            savePosts(current)
            return true
        }
        return false
    }

    @Synchronized
    fun addReaction(postId: String, userPhone: String, emoji: String): Boolean {
        ensureDeletedPostsLoaded()
        if (isPostDeleted(postId)) return false
        val current = loadPosts().toMutableList()
        val index = current.indexOfFirst {
            it.id == postId || (postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(postId, ignoreCase = true))
        }
        if (index >= 0) {
            val post = current[index]
            if (isPostDeleted(post.id)) return false
            val existingReactionIdx = post.reactions.indexOfFirst {
                it.userPhone.equals(userPhone, ignoreCase = true) ||
                (it.userPhone.any { c -> c.isDigit() } && userPhone.any { c -> c.isDigit() } && FriendRequestRepository.isSamePhone(it.userPhone, userPhone))
            }
            val newReactions = post.reactions.toMutableList()
            val isNewlyAdded: Boolean
            if (existingReactionIdx >= 0) {
                if (newReactions[existingReactionIdx].emoji == emoji) {
                    newReactions.removeAt(existingReactionIdx) // Toggle off
                    isNewlyAdded = false
                } else {
                    newReactions[existingReactionIdx] = SocialReaction(UUID.randomUUID().toString(), post.id, userPhone, emoji)
                    isNewlyAdded = true
                }
            } else {
                newReactions.add(SocialReaction(UUID.randomUUID().toString(), post.id, userPhone, emoji))
                isNewlyAdded = true
            }
            current[index] = post.copy(reactions = newReactions)
            savePosts(current)
            if (isNewlyAdded) {
                try {
                    com.sha.orbis.telemetry.FeedTelemetryTracker.trackReaction(context)
                } catch (_: Exception) {}
            }
            return isNewlyAdded
        }
        return false
    }

    @Synchronized
    fun votePoll(postId: String, optionId: String, userPhone: String, isLocalUser: Boolean = false): Boolean {
        val current = loadPosts().toMutableList()
        val index = current.indexOfFirst {
            it.id == postId ||
            (postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(postId, ignoreCase = true)) ||
            (it.id.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(postId).equals(it.id, ignoreCase = true))
        }
        if (index >= 0) {
            val post = current[index]
            val poll = post.poll ?: return false

            val targetOption = poll.options.find { it.id == optionId || it.text.equals(optionId, ignoreCase = true) }
            val targetOptId = targetOption?.id ?: optionId

            // Check if user has already voted for this specific option
            val alreadyVotedThisOption = poll.options.any { opt ->
                opt.id == targetOptId && opt.votersPhones.any { FriendRequestRepository.isSamePhone(it, userPhone) }
            }
            if (alreadyVotedThisOption) {
                // Duplicate vote packet, nothing to change
                return false
            }

            // Remove previous vote by this user if they are switching options or updating
            val updatedOptions = poll.options.map { opt ->
                val hasUserVotedThis = opt.votersPhones.any { FriendRequestRepository.isSamePhone(it, userPhone) }
                if (opt.id == targetOptId) {
                    val cleanList = opt.votersPhones.filterNot { FriendRequestRepository.isSamePhone(it, userPhone) } + userPhone
                    opt.copy(voteCount = cleanList.size, votersPhones = cleanList)
                } else if (hasUserVotedThis) {
                    val cleanList = opt.votersPhones.filterNot { FriendRequestRepository.isSamePhone(it, userPhone) }
                    opt.copy(voteCount = cleanList.size, votersPhones = cleanList)
                } else {
                    opt
                }
            }

            val totalVotesCount = updatedOptions.sumOf { it.voteCount }
            val newLocalVotedId = if (isLocalUser) {
                targetOptId
            } else {
                poll.userVotedOptionId
            }

            val updatedPoll = poll.copy(
                options = updatedOptions,
                totalVotes = totalVotesCount,
                userVotedOptionId = newLocalVotedId
            )
            current[index] = post.copy(poll = updatedPoll)
            savePosts(current)
            if (isLocalUser) {
                try {
                    com.sha.orbis.telemetry.FeedTelemetryTracker.trackPollVote(context)
                } catch (_: Exception) {}
            }
            return true
        }
        return false
    }

    /**
     * Applique un vote de sondage décentralisé reçu via les relais Nostr (Kind 7 avec tag poll_option).
     */
    @Synchronized
    fun applyIncomingPollVote(postId: String, optionId: String, voterPhone: String): Boolean {
        ensureDeletedPostsLoaded()
        if (isPostDeleted(postId)) return false
        val current = loadPosts().toMutableList()
        val index = current.indexOfFirst {
            it.id == postId ||
            (postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(postId, ignoreCase = true)) ||
            (it.id.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(postId).equals(it.id, ignoreCase = true))
        }
        if (index < 0) return false
        val post = current[index]
        if (isPostDeleted(post.id)) return false
        val poll = post.poll ?: return false

        val targetOption = poll.options.find { it.id == optionId || it.text.equals(optionId, ignoreCase = true) }
        val targetOptId = targetOption?.id ?: optionId

        val alreadyVotedThisOption = poll.options.any { opt ->
            opt.id == targetOptId && opt.votersPhones.any { FriendRequestRepository.isSamePhone(it, voterPhone) }
        }
        if (alreadyVotedThisOption) return true

        val updatedOptions = poll.options.map { opt ->
            val hasUserVotedThis = opt.votersPhones.any { FriendRequestRepository.isSamePhone(it, voterPhone) }
            if (opt.id == targetOptId) {
                val cleanList = opt.votersPhones.filterNot { FriendRequestRepository.isSamePhone(it, voterPhone) } + voterPhone
                opt.copy(voteCount = cleanList.size, votersPhones = cleanList)
            } else if (hasUserVotedThis) {
                val cleanList = opt.votersPhones.filterNot { FriendRequestRepository.isSamePhone(it, voterPhone) }
                opt.copy(voteCount = cleanList.size, votersPhones = cleanList)
            } else {
                opt
            }
        }

        val totalVotes = updatedOptions.sumOf { it.voteCount }
        val updatedPoll = poll.copy(
            options = updatedOptions,
            totalVotes = totalVotes
        )
        current[index] = post.copy(poll = updatedPoll)
        savePosts(current)
        return true
    }

    /**
     * Idempotent comment-reaction apply for Nostr sync (no toggle-off when the same emoji is replayed).
     */
    @Synchronized
    fun applyIncomingCommentReaction(postId: String, commentId: String, userPhone: String, emoji: String): Boolean {
        ensureDeletedPostsLoaded()
        if (isPostDeleted(postId)) return false
        val current = loadPosts().toMutableList()
        val pIndex = current.indexOfFirst {
            it.id == postId || (postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(postId, ignoreCase = true))
        }
        if (pIndex < 0) return false
        val post = current[pIndex]
        if (isPostDeleted(post.id)) return false
        val comments = post.comments.toMutableList()
        val cIndex = comments.indexOfFirst { it.id == commentId }
        if (cIndex < 0) return false
        val comment = comments[cIndex]
        val existingIdx = comment.reactions.indexOfFirst {
            FriendRequestRepository.isSamePhone(it.userPhone, userPhone)
        }
        val newReactions = comment.reactions.toMutableList()
        if (existingIdx >= 0) {
            if (newReactions[existingIdx].emoji == emoji) {
                return true
            }
            newReactions[existingIdx] = SocialReaction(
                UUID.randomUUID().toString(), commentId, userPhone, emoji
            )
        } else {
            newReactions.add(SocialReaction(UUID.randomUUID().toString(), commentId, userPhone, emoji))
        }
        comments[cIndex] = comment.copy(reactions = newReactions)
        current[pIndex] = post.copy(comments = comments)
        savePosts(current)
        return true
    }

    @Synchronized
    fun addComment(postId: String, comment: SocialComment): Boolean {
        ensureDeletedPostsLoaded()
        if (isPostDeleted(postId) || isPostDeleted(comment.postId)) {
            return false
        }
        val current = loadPosts().toMutableList()
        val index = current.indexOfFirst {
            it.id == postId || (postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(postId, ignoreCase = true))
        }
        if (index >= 0) {
            val post = current[index]
            if (isPostDeleted(post.id)) return false
            val resolvedComment = if (comment.postId != post.id) comment.copy(postId = post.id) else comment
            val comments = post.comments.toMutableList()
            val existingCommentIdx = comments.indexOfFirst { it.id == resolvedComment.id }
            if (existingCommentIdx >= 0) {
                comments[existingCommentIdx] = resolvedComment
                current[index] = post.copy(comments = comments)
                savePosts(current)
                return false
            } else {
                comments.add(resolvedComment)
                current[index] = post.copy(comments = comments)
                savePosts(current)
                try {
                    com.sha.orbis.telemetry.FeedTelemetryTracker.trackComment(context)
                } catch (_: Exception) {}
                return true
            }
        }
        return false
    }

    @Synchronized
    fun deleteComment(postId: String, commentId: String) {
        val current = loadPosts().toMutableList()
        val index = current.indexOfFirst {
            it.id == postId || (postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(postId, ignoreCase = true))
        }
        if (index >= 0) {
            val post = current[index]
            val comments = post.comments.filterNot { it.id == commentId }
            current[index] = post.copy(comments = comments)
            savePosts(current)
        }
        try {
            NotificationRepository(context).deleteNotification("notif_comment_${commentId.take(16)}")
        } catch (_: Exception) {}
    }

    @Synchronized
    fun editComment(postId: String, commentId: String, newText: String): Boolean {
        val current = loadPosts().toMutableList()
        val postIndex = current.indexOfFirst {
            it.id == postId || (postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(postId, ignoreCase = true))
        }
        if (postIndex >= 0) {
            val post = current[postIndex]
            val comments = post.comments.toMutableList()
            val commentIdx = comments.indexOfFirst { it.id == commentId }
            if (commentIdx >= 0) {
                val oldComment = comments[commentIdx]
                val updatedComment = oldComment.copy(
                    text = newText.trim(),
                    isEdited = true,
                    editedAt = System.currentTimeMillis()
                )
                comments[commentIdx] = updatedComment
                current[postIndex] = post.copy(comments = comments)
                savePosts(current)
                return true
            }
        }
        return false
    }

    @Synchronized
    fun addCommentReaction(postId: String, commentId: String, userPhone: String, emoji: String) {
        val current = loadPosts().toMutableList()
        val postIndex = current.indexOfFirst {
            it.id == postId || (postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(postId, ignoreCase = true))
        }
        if (postIndex >= 0) {
            val post = current[postIndex]
            val comments = post.comments.toMutableList()
            val commentIndex = comments.indexOfFirst { it.id == commentId }
            if (commentIndex >= 0) {
                val comment = comments[commentIndex]
                val existingReactionIdx = comment.reactions.indexOfFirst {
                    it.userPhone.equals(userPhone, ignoreCase = true) ||
                    (it.userPhone.any { c -> c.isDigit() } && userPhone.any { c -> c.isDigit() } && FriendRequestRepository.isSamePhone(it.userPhone, userPhone))
                }
                val newReactions = comment.reactions.toMutableList()
                if (existingReactionIdx >= 0) {
                    if (newReactions[existingReactionIdx].emoji == emoji) {
                        newReactions.removeAt(existingReactionIdx) // Toggle off
                    } else {
                        newReactions[existingReactionIdx] = SocialReaction(UUID.randomUUID().toString(), commentId, userPhone, emoji)
                    }
                } else {
                    newReactions.add(SocialReaction(UUID.randomUUID().toString(), commentId, userPhone, emoji))
                }
                comments[commentIndex] = comment.copy(reactions = newReactions)
                current[postIndex] = post.copy(comments = comments)
                savePosts(current)
            }
        }
    }

    // --- Stories Management ---

    @Synchronized
    fun loadStories(): List<SocialStory> {
        accountCachedStories[currentAccountId]?.let { stories ->
            val now = System.currentTimeMillis()
            val valid = stories.filter { it.expiresAt > now }
            if (valid.size != stories.size) {
                accountCachedStories[currentAccountId] = valid
                saveStories(valid)
            }
            return valid
        }

        if (!storiesFile.exists()) {
            val defaults = OfficialAnnouncementsProvider.getOfficialInitialStories(context)
            saveStories(defaults)
            return defaults
        }
        val result = try {
            if (storiesFile.length() > 5 * 1024 * 1024L) {
                android.util.Log.w("SocialRepository", "storiesFile too large, resetting")
                storiesFile.delete()
                val defaults = OfficialAnnouncementsProvider.getOfficialInitialStories(context)
                saveStories(defaults)
                return defaults
            }
            val array = JSONArray(storiesFile.readText())
            val list = mutableListOf<SocialStory>()
            for (i in 0 until array.length()) {
                val story = SocialStory.fromJson(array.getJSONObject(i))
                if (!story.isExpired) {
                    list.add(story)
                } else {
                    // Auto-delete expired story photo on disk
                    if (!story.mediaPath.isNullOrBlank()) {
                        try { File(story.mediaPath).delete() } catch (_: Exception) {}
                    }
                }
            }
            if (list.isEmpty()) {
                val defaults = OfficialAnnouncementsProvider.getOfficialInitialStories(context)
                saveStories(defaults)
                defaults
            } else {
                list.sortedByDescending { it.createdAt }
            }
        } catch (e: OutOfMemoryError) {
            System.gc()
            try { storiesFile.delete() } catch (_: Exception) {}
            OfficialAnnouncementsProvider.getOfficialInitialStories(context)
        } catch (_: Exception) {
            OfficialAnnouncementsProvider.getOfficialInitialStories(context)
        }
        accountCachedStories[currentAccountId] = result
        return result
    }

    @Synchronized
    fun saveStories(stories: List<SocialStory>) {
        val (valid, expired) = stories.partition { !it.isExpired }
        expired.forEach { story ->
            if (!story.mediaPath.isNullOrBlank()) {
                try {
                    com.sha.orbis.media.VideoMediaHelper.deleteVideoPhysical(context, story.mediaPath)
                    File(story.mediaPath).delete()
                } catch (_: Exception) {}
            }
        }
        accountCachedStories[currentAccountId] = valid
        try {
            storiesFile.bufferedWriter().use { writer ->
                writer.write("[\n")
                valid.forEachIndexed { index, story ->
                    if (index > 0) writer.write(",\n")
                    writer.write(story.toJson().toString())
                }
                writer.write("\n]")
            }
        } catch (e: OutOfMemoryError) {
            System.gc()
            android.util.Log.e("SocialRepository", "OOM in saveStories: ${e.message}")
        } catch (_: Exception) {}
    }

    @Synchronized
    fun addStory(story: SocialStory) {
        val current = loadStories().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == story.id }
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            current[existingIndex] = story.copy(
                mediaPath = story.mediaPath ?: existing.mediaPath,
                seenBy = if (story.seenBy.isEmpty()) existing.seenBy else story.seenBy,
                reactions = if (story.reactions.isEmpty()) existing.reactions else story.reactions
            )
        } else {
            current.add(0, story)
        }
        saveStories(current)
    }

    @Synchronized
    fun markStorySeen(storyId: String, viewerPhone: String): Boolean {
        if (viewerPhone.isBlank()) return false
        val current = loadStories().toMutableList()
        val index = current.indexOfFirst { it.id == storyId }
        if (index >= 0) {
            val story = current[index]
            if (FriendRequestRepository.isSamePhone(story.authorPhone, viewerPhone)) {
                return false
            }
            if (!story.seenBy.any { FriendRequestRepository.isSamePhone(it, viewerPhone) }) {
                current[index] = story.copy(seenBy = story.seenBy + viewerPhone)
                saveStories(current)
                return true
            }
        }
        return false
    }

    @Synchronized
    fun addStoryReaction(storyId: String, userPhone: String, emoji: String): Boolean {
        if (userPhone.isBlank() || emoji.isBlank()) return false
        val current = loadStories().toMutableList()
        val index = current.indexOfFirst {
            it.id == storyId ||
                (storyId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(storyId, ignoreCase = true))
        }
        if (index < 0) return false

        val story = current[index]
        val reactions = story.reactions.toMutableList()
        val existingIndex = reactions.indexOfFirst {
            it.userPhone.equals(userPhone, ignoreCase = true) ||
                (it.userPhone.any { c -> c.isDigit() } && userPhone.any { c -> c.isDigit() } && FriendRequestRepository.isSamePhone(it.userPhone, userPhone))
        }

        val isNewlyAdded = if (existingIndex >= 0) {
            if (reactions[existingIndex].emoji == emoji) {
                reactions.removeAt(existingIndex)
                false
            } else {
                reactions[existingIndex] = SocialReaction(UUID.randomUUID().toString(), story.id, userPhone, emoji)
                true
            }
        } else {
            reactions.add(SocialReaction(UUID.randomUUID().toString(), story.id, userPhone, emoji))
            true
        }

        current[index] = story.copy(reactions = reactions)
        saveStories(current)
        return isNewlyAdded
    }

    @Synchronized
    fun deleteStory(storyId: String) {
        val current = loadStories().toMutableList()
        val toRemove = current.filter { it.id == storyId }
        toRemove.forEach { story ->
            if (!story.mediaPath.isNullOrBlank()) {
                try {
                    com.sha.orbis.media.VideoMediaHelper.deleteVideoPhysical(context, story.mediaPath)
                    File(story.mediaPath).delete()
                } catch (_: Exception) {}
            }
        }
        current.removeAll { it.id == storyId }
        saveStories(current)
    }
}
