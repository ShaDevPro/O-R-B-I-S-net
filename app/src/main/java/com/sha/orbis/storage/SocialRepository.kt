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
        incoming.forEach { byId[it.id] = it }
        return byId.values.sortedBy { it.timestamp }
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
    private fun flushPendingEngagements(resolvedPostId: String) {
        if (pendingComments.isEmpty() && pendingReactions.isEmpty()) return
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

    @Synchronized
    fun loadPosts(): List<SocialPost> {
        ensureDeletedPostsLoaded()
        accountCachedPosts[currentAccountId]?.let { cached ->
            val cleanCached = cached.filterNot { isPostDeleted(it.id) }
            if (cleanCached.size != cached.size) {
                accountCachedPosts[currentAccountId] = cleanCached
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
            for (i in 0 until array.length()) {
                val post = SocialPost.fromJson(array.getJSONObject(i))
                // Filter out all official Orbis announcements and deleted posts
                if (!post.isOfficialAnnouncement && !isPostDeleted(post.id)) {
                    list.add(post)
                }
            }

            // If we just purged official posts or deleted posts from disk, persist the cleaned list
            val rawSize = array.length()
            if (rawSize > list.size) {
                savePosts(list)
            }

            if (list.isEmpty()) {
                emptyList()
            } else {
                list.sortedWith(
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
        val sorted = posts.sortedWith(
            compareByDescending<SocialPost> { it.isPinned }
                .thenByDescending { if (!it.isPinned && it.isOfficialAnnouncement) 0L else it.timestamp }
                .thenByDescending { it.timestamp }
        )
        // Strip mediaData from posts that already have a valid local file — mediaData (base64) is
        // only needed for Nostr transport, not for local disk. Keeping it would bloat the JSON file.
        val toSave = sorted.map { post ->
            if (!post.mediaPath.isNullOrBlank() &&
                java.io.File(post.mediaPath).exists() &&
                !post.mediaData.isNullOrBlank()
            ) {
                post.copy(mediaData = null)
            } else post
        }
        accountCachedPosts[currentAccountId] = toSave
        try {
            // Write stream-by-stream directly to disk to never allocate a giant contiguous string
            postsFile.bufferedWriter().use { writer ->
                writer.write("[\n")
                toSave.forEachIndexed { index, post ->
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

    @Synchronized
    fun addPost(post: SocialPost) {
        ensureDeletedPostsLoaded()
        if (isPostDeleted(post.id)) {
            android.util.Log.d("SocialRepository", "addPost rejeté : post marqué comme supprimé (${post.id})")
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
            val bestMediaPath = if (!existing.mediaPath.isNullOrBlank() &&
                    java.io.File(existing.mediaPath!!).exists() &&
                    java.io.File(existing.mediaPath!!).length() > 0L) {
                existing.mediaPath
            } else {
                post.mediaPath ?: existing.mediaPath
            }
            current[existingIdx] = post.copy(
                reactions = mergeReactions(existing.reactions, post.reactions),
                comments = mergeComments(existing.comments, post.comments),
                poll = post.poll ?: existing.poll,
                mediaPath = bestMediaPath,
                receivedAt = existing.receivedAt.takeIf { it > 0L } ?: post.receivedAt
            )
            flushPendingEngagements(current[existingIdx].id)
        } else {
            current.add(0, post)
            flushPendingEngagements(post.id)
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
            return isNewlyAdded
        }
        return false
    }

    @Synchronized
    fun votePoll(postId: String, optionId: String, userPhone: String, isLocalUser: Boolean = false) {
        val current = loadPosts().toMutableList()
        val index = current.indexOfFirst {
            it.id == postId || (postId.length == 64 && com.sha.orbis.nostr.protocol.NostrProtocolEngine.toNostrHex(it.id).equals(postId, ignoreCase = true))
        }
        if (index >= 0) {
            val post = current[index]
            val poll = post.poll ?: return

            // Check if user has already voted for this specific option
            val alreadyVotedThisOption = poll.options.any { opt ->
                opt.id == optionId && opt.votersPhones.any { FriendRequestRepository.isSamePhone(it, userPhone) }
            }
            if (alreadyVotedThisOption) {
                // Duplicate vote packet, nothing to change
                return
            }

            // Remove previous vote by this user if they are switching options or updating
            val updatedOptions = poll.options.map { opt ->
                val hasUserVotedThis = opt.votersPhones.any { FriendRequestRepository.isSamePhone(it, userPhone) }
                if (opt.id == optionId) {
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
                optionId
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
        }
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
