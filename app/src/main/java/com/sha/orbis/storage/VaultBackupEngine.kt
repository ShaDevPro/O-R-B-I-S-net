package com.sha.orbis.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Base64
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.AccountProfile
import com.sha.orbis.model.Contact
import com.sha.orbis.model.Conversation
import com.sha.orbis.model.Message
import com.sha.orbis.security.AesCipher
import com.sha.orbis.security.Base64Compat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sovereign Offline Vault Backup Engine (Version 3).
 * Provides military-grade PBKDF2 + AES-256-GCM encrypted backups and offline P2P data portability.
 */
object VaultBackupEngine {

    const val FORMAT_HEADER_V3 = "ORBIS_SOVEREIGN_VAULT_V3"
    const val FORMAT_HEADER_V2 = "ORBIS_SOVEREIGN_VAULT_V2"
    const val FORMAT_HEADER_V1 = "ORBIS_SOVEREIGN_VAULT_V1"

    const val ORBIS_SOVEREIGN_PLATFORM_KEY = "ORBIS_SOVEREIGN_SOVEREIGNTY_VAULT_KEY_2026_OFFLINE_MESH"
    const val ORBIS_LEGACY_DEFAULT_KEY = "orbis-vault-key-2026"

    val CANDIDATE_PLATFORM_KEYS = listOf(
        ORBIS_SOVEREIGN_PLATFORM_KEY,
        ORBIS_LEGACY_DEFAULT_KEY,
        ""
    )

    private const val PBKDF2_ITERATIONS = 100_000
    private const val PBKDF2_KEY_LENGTH = 256
    private const val SALT_LENGTH_BYTES = 16

    data class BackupStats(
        val accountsCount: Int = 0,
        val contactsCount: Int = 0,
        val conversationsCount: Int = 0,
        val messagesCount: Int = 0,
        val circlesCount: Int = 0,
        val postsCount: Int = 0,
        val storiesCount: Int = 0,
        val blockedCount: Int = 0,
        val nostrIdentityRestored: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Builds the complete, AES-256-GCM encrypted vault JSON payload with PBKDF2 key derivation.
     * In 1-Click Transparent mode (passphrase is empty), it uses the internal sovereign platform key.
     */
    fun buildEncryptedVaultPayload(context: Context, passphrase: String = ""): String? {
        return try {
            val convRepo = ConversationRepository(context)
            val circleRepo = FriendCircleRepository(context)
            val socialRepo = SocialRepository(context)
            val blockedRepo = BlockedContactsRepository(context)
            val sessionManager = SessionManager(context)
            val messageStore = LocalMessageStore(context)

            val vaultJson = JSONObject().apply {
                put("version", 3)
                put("timestamp", System.currentTimeMillis())
                put("activeAccountId", sessionManager.activeAccountId)

                // 1. Accounts & Cryptographic RSA Identity
                val accountsArray = JSONArray()
                sessionManager.getAccounts().forEach { accountsArray.put(it.toJson()) }
                put("accounts", accountsArray)

                // 2. Contacts
                val contactsArray = JSONArray()
                convRepo.loadContacts().forEach { contactsArray.put(it.toJson()) }
                put("contacts", contactsArray)

                // 3. Conversations
                val convs = convRepo.loadConversations()
                val convsArray = JSONArray()
                convs.forEach { convsArray.put(it.toJson()) }
                put("conversations", convsArray)

                // 4. Conversation Messages
                val messagesObject = JSONObject()
                convs.forEach { conv ->
                    val msgs = messageStore.loadConversationMessages(conv.id)
                    val msgsArray = JSONArray()
                    msgs.forEach { msgsArray.put(it.toJson()) }
                    messagesObject.put(conv.id, msgsArray)
                }
                put("messagesByConv", messagesObject)

                // 5. Sovereign Circles
                val circlesArray = JSONArray()
                circleRepo.loadCircles().forEach { circlesArray.put(it.toJson()) }
                put("circles", circlesArray)

                // 6. Social Posts, Polls, Reactions & Comments
                val postsArray = JSONArray()
                socialRepo.loadPosts().forEach { postsArray.put(it.toJson()) }
                put("posts", postsArray)

                // 7. 24h Ephemeral Stories
                val storiesArray = JSONArray()
                socialRepo.loadStories().forEach { storiesArray.put(it.toJson()) }
                put("stories", storiesArray)

                // 8. Blocked Contacts
                val blockedArray = JSONArray()
                blockedRepo.loadBlocked().forEach { blockedArray.put(it.toJson()) }
                put("blocked", blockedArray)

                // 9. Sovereign Nostr Identity (nsec, npub, secp256k1 keys)
                try {
                    val nostrIdentity = com.sha.orbis.nostr.identity.NostrIdentityManager.getInstance(context).exportIdentityJson()
                    put("nostrIdentity", nostrIdentity)
                } catch (_: Exception) {}
            }

            // Generate cryptographically secure salt
            val salt = ByteArray(SALT_LENGTH_BYTES)
            SecureRandom().nextBytes(salt)
            val saltBase64 = Base64Compat.encodeToString(salt)

            val isCustomProtected = passphrase.isNotBlank()
            val effectivePassphrase = if (isCustomProtected) passphrase.trim() else ORBIS_SOVEREIGN_PLATFORM_KEY

            // Derive 256-bit AES key using PBKDF2 (100,000 iterations)
            val key = deriveKeyPbkdf2(effectivePassphrase, salt)
            val encrypted = AesCipher.encrypt(vaultJson.toString(), key)

            val rootJson = JSONObject().apply {
                put("format", FORMAT_HEADER_V3)
                put("protected", isCustomProtected)
                put("kdf", "PBKDF2WithHmacSHA256")
                put("iterations", PBKDF2_ITERATIONS)
                put("salt", saltBase64)
                put("payload", encrypted.payload)
                put("iv", encrypted.iv)
            }
            rootJson.toString(2)
        } catch (_: Exception) {
            null
        }
    }

    fun exportVault(context: Context, passphrase: String = ""): File? {
        val json = buildEncryptedVaultPayload(context, passphrase) ?: return null
        return try {
            val baseDir = try {
                val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                File(docs, "Orbis").apply { if (!exists()) mkdirs() }
            } catch (_: Exception) {
                File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
            }

            val backupFile = File(baseDir, "Orbis_Vault_${System.currentTimeMillis()}.orbis")
            backupFile.writeText(json, Charsets.UTF_8)
            backupFile
        } catch (_: Exception) {
            null
        }
    }

    fun exportVaultToStream(context: Context, outputStream: OutputStream, passphrase: String = ""): Boolean {
        val json = buildEncryptedVaultPayload(context, passphrase) ?: return false
        return try {
            outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            true
        } catch (_: Exception) {
            false
        }
    }

    const val TARGET_TELEGRAM = "telegram"
    const val TARGET_WHATSAPP = "whatsapp"
    const val TARGET_OTHER = "other"

    private const val PREFS_BACKUP_STORAGE = "orbis_vault_storage_pref"
    private const val KEY_LAST_TARGET_APP = "last_backup_target_app"

    fun setLastBackupTargetApp(context: Context, target: String) {
        context.getSharedPreferences(PREFS_BACKUP_STORAGE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_TARGET_APP, target)
            .apply()
    }

    fun getLastBackupTargetApp(context: Context): String? {
        return context.getSharedPreferences(PREFS_BACKUP_STORAGE, Context.MODE_PRIVATE)
            .getString(KEY_LAST_TARGET_APP, null)
    }

    fun isAppInstalled(context: Context, targetApp: String): Boolean {
        val packages = when (targetApp) {
            TARGET_TELEGRAM -> listOf("org.telegram.messenger", "org.telegram.messenger.web")
            TARGET_WHATSAPP -> listOf("com.whatsapp", "com.whatsapp.w4b")
            else -> emptyList()
        }
        val pm = context.packageManager
        return packages.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun openExternalApp(context: Context, targetApp: String): Boolean {
        val packages = when (targetApp) {
            TARGET_TELEGRAM -> listOf("org.telegram.messenger", "org.telegram.messenger.web")
            TARGET_WHATSAPP -> listOf("com.whatsapp", "com.whatsapp.w4b")
            else -> emptyList()
        }
        val pm = context.packageManager
        for (pkg in packages) {
            try {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    fun shareVaultFile(context: Context, file: File, targetApp: String? = null): Boolean {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val packages = when (targetApp) {
                TARGET_TELEGRAM -> listOf("org.telegram.messenger", "org.telegram.messenger.web")
                TARGET_WHATSAPP -> listOf("com.whatsapp", "com.whatsapp.w4b")
                else -> emptyList()
            }

            val pm = context.packageManager
            var resolvedPkg: String? = null
            for (pkg in packages) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    resolvedPkg = pkg
                    break
                } catch (_: Exception) {}
            }

            if (resolvedPkg != null) {
                intent.setPackage(resolvedPkg)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                setLastBackupTargetApp(context, targetApp ?: TARGET_OTHER)
                return true
            }

            // Fallback to chooser
            val chooserTitle = if (targetApp != null) "Partager sur $targetApp" else "Transférer le coffre Orbis"
            val chooser = android.content.Intent.createChooser(intent, chooserTitle).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            if (targetApp != null) {
                setLastBackupTargetApp(context, targetApp)
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun shareVault(context: Context, passphrase: String = "") {
        val file = exportVault(context, passphrase) ?: return
        shareVaultFile(context, file, null)
    }

    /**
     * Inspects the header of a .orbis file to determine whether it is protected by a custom passphrase.
     * Returns:
     * - false: 1-Click Transparent mode (protected with sovereign platform key, no password needed)
     * - true: Custom passphrase required
     * - null: Invalid or corrupted archive format
     */
    fun tryDecryptVaultJson(rootJson: JSONObject, passphrase: String): JSONObject? {
        return try {
            val format = rootJson.optString("format")
            val payload = rootJson.getString("payload")
            val iv = rootJson.getString("iv")

            val key: SecretKey = if (format == FORMAT_HEADER_V3) {
                val saltBase64 = rootJson.optString("salt")
                val salt = if (saltBase64.isNotBlank()) Base64Compat.decode(saltBase64) else ByteArray(SALT_LENGTH_BYTES)
                val iterations = rootJson.optInt("iterations", PBKDF2_ITERATIONS)
                deriveKeyPbkdf2(passphrase, salt, iterations)
            } else {
                deriveKeySha256(passphrase)
            }

            val decryptedJsonStr = AesCipher.decrypt(AesCipher.EncryptedPayload(payload, iv), key)
            JSONObject(decryptedJsonStr)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Inspects a .orbis file to determine whether it requires a custom passphrase.
     * Tries candidate sovereign platform keys first:
     * - Returns false if any candidate key successfully decrypts the vault (1-Click mode!).
     * - Returns true if a custom passphrase is required.
     * - Returns null if corrupted or invalid format.
     */
    fun isVaultPasswordProtected(context: Context, uri: Uri): Boolean? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val jsonStr = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val rootJson = JSONObject(jsonStr)
            val format = rootJson.optString("format")
            if (format != FORMAT_HEADER_V3 && format != FORMAT_HEADER_V2 && format != FORMAT_HEADER_V1) {
                return null
            }

            // If any platform key can decrypt it, it does NOT require a user password!
            for (candidate in CANDIDATE_PLATFORM_KEYS) {
                if (tryDecryptVaultJson(rootJson, candidate) != null) {
                    return false
                }
            }

            // None of the platform keys worked -> custom password strictly required
            true
        } catch (_: Exception) {
            null
        }
    }

    fun restoreVaultFromStream(context: Context, inputStream: InputStream, passphrase: String = ""): BackupStats? {
        return try {
            val jsonStr = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            restoreVaultFromJson(context, jsonStr, passphrase)
        } catch (_: Exception) {
            null
        }
    }

    fun restoreVault(context: Context, file: File, passphrase: String = ""): BackupStats? {
        return try {
            val jsonStr = file.readText(Charsets.UTF_8)
            restoreVaultFromJson(context, jsonStr, passphrase)
        } catch (_: Exception) {
            null
        }
    }

    fun restoreVaultFromUri(context: Context, uri: Uri, passphrase: String = ""): BackupStats? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            restoreVaultFromStream(context, input, passphrase)
        } catch (_: Exception) {
            null
        }
    }

    fun restoreVaultFromJson(context: Context, jsonStr: String, passphrase: String = ""): BackupStats? {
        return try {
            val rootJson = JSONObject(jsonStr)
            val format = rootJson.optString("format")
            if (format != FORMAT_HEADER_V3 && format != FORMAT_HEADER_V2 && format != FORMAT_HEADER_V1) {
                return null
            }

            var vaultJson: JSONObject? = null

            // 1. If custom passphrase provided, try it first
            if (passphrase.isNotBlank()) {
                vaultJson = tryDecryptVaultJson(rootJson, passphrase.trim())
            }

            // 2. If no passphrase provided or it failed, try all candidate platform keys
            if (vaultJson == null) {
                for (candidate in CANDIDATE_PLATFORM_KEYS) {
                    val candidateJson = tryDecryptVaultJson(rootJson, candidate)
                    if (candidateJson != null) {
                        vaultJson = candidateJson
                        break
                    }
                }
            }

            if (vaultJson == null) {
                return null
            }

            val sessionManager = SessionManager(context)
            val convRepo = ConversationRepository(context)
            val messageStore = LocalMessageStore(context)
            val circleRepo = FriendCircleRepository(context)
            val socialRepo = SocialRepository(context)
            val blockedRepo = BlockedContactsRepository(context)

            var accountsCount = 0
            var contactsCount = 0
            var conversationsCount = 0
            var messagesCount = 0
            var circlesCount = 0
            var postsCount = 0
            var storiesCount = 0
            var blockedCount = 0

            // 1. Restore Accounts & RSA Keys
            val accountsArray = vaultJson.optJSONArray("accounts")
            if (accountsArray != null && accountsArray.length() > 0) {
                val accList = mutableListOf<AccountProfile>()
                for (i in 0 until accountsArray.length()) {
                    accList.add(AccountProfile.fromJson(accountsArray.getJSONObject(i)))
                }
                sessionManager.saveAccounts(accList)
                val activeId = vaultJson.optString("activeAccountId", accList.first().id)
                sessionManager.activeAccountId = activeId
                accountsCount = accList.size
            }
            sessionManager.isAuthenticated = true
            sessionManager.isOnboardingCompleted = true

            // 2. Restore Contacts
            val contactsArray = vaultJson.optJSONArray("contacts")
            if (contactsArray != null) {
                val list = mutableListOf<Contact>()
                for (i in 0 until contactsArray.length()) {
                    list.add(Contact.fromJson(contactsArray.getJSONObject(i)))
                }
                convRepo.saveContacts(list)
                contactsCount = list.size
            }

            // 3. Restore Conversations
            val convsArray = vaultJson.optJSONArray("conversations")
            if (convsArray != null) {
                val list = mutableListOf<Conversation>()
                for (i in 0 until convsArray.length()) {
                    list.add(Conversation.fromJson(convsArray.getJSONObject(i)))
                }
                convRepo.saveConversations(list)
                conversationsCount = list.size
            }

            // 4. Restore Messages per Conversation
            val messagesObj = vaultJson.optJSONObject("messagesByConv")
            if (messagesObj != null) {
                val keys = messagesObj.keys()
                while (keys.hasNext()) {
                    val convId = keys.next()
                    val msgsArray = messagesObj.getJSONArray(convId)
                    val msgsList = mutableListOf<Message>()
                    for (i in 0 until msgsArray.length()) {
                        msgsList.add(Message.fromJson(msgsArray.getJSONObject(i)))
                    }
                    messageStore.saveConversationMessages(convId, msgsList)
                    messagesCount += msgsList.size
                }
            }

            // 5. Restore Circles
            val circlesArray = vaultJson.optJSONArray("circles")
            if (circlesArray != null) {
                val list = mutableListOf<com.sha.orbis.model.FriendCircle>()
                for (i in 0 until circlesArray.length()) {
                    list.add(com.sha.orbis.model.FriendCircle.fromJson(circlesArray.getJSONObject(i)))
                }
                circleRepo.saveCircles(list)
                circlesCount = list.size
            }

            // 6. Restore Social Posts
            val postsArray = vaultJson.optJSONArray("posts")
            if (postsArray != null) {
                val list = mutableListOf<com.sha.orbis.social.SocialPost>()
                for (i in 0 until postsArray.length()) {
                    list.add(com.sha.orbis.social.SocialPost.fromJson(postsArray.getJSONObject(i)))
                }
                socialRepo.savePosts(list)
                postsCount = list.size
            }

            // 7. Restore 24h Stories
            val storiesArray = vaultJson.optJSONArray("stories")
            if (storiesArray != null) {
                val list = mutableListOf<com.sha.orbis.social.SocialStory>()
                for (i in 0 until storiesArray.length()) {
                    list.add(com.sha.orbis.social.SocialStory.fromJson(storiesArray.getJSONObject(i)))
                }
                socialRepo.saveStories(list)
                storiesCount = list.size
            }

            // 8. Restore Blocked Contacts
            val blockedArray = vaultJson.optJSONArray("blocked")
            if (blockedArray != null) {
                val list = mutableListOf<com.sha.orbis.model.BlockedContact>()
                for (i in 0 until blockedArray.length()) {
                    list.add(com.sha.orbis.model.BlockedContact.fromJson(blockedArray.getJSONObject(i)))
                }
                blockedRepo.saveBlocked(list)
                blockedCount = list.size
            }

            // 9. Restore Sovereign Nostr Identity (nsec, npub)
            var nostrRestored = false
            val nostrIdentityJson = vaultJson.optJSONObject("nostrIdentity")
            if (nostrIdentityJson != null) {
                val privHex = nostrIdentityJson.optString("privateKeyHex")
                val pubHex = nostrIdentityJson.optString("publicKeyHex")
                if (privHex.isNotBlank() && pubHex.isNotBlank()) {
                    nostrRestored = com.sha.orbis.nostr.identity.NostrIdentityManager.getInstance(context).importIdentity(privHex, pubHex)
                }
            }

            BackupStats(
                accountsCount = accountsCount,
                contactsCount = contactsCount,
                conversationsCount = conversationsCount,
                messagesCount = messagesCount,
                circlesCount = circlesCount,
                postsCount = postsCount,
                storiesCount = storiesCount,
                blockedCount = blockedCount,
                nostrIdentityRestored = nostrRestored,
                timestamp = vaultJson.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * PBKDF2WithHmacSHA256 key derivation (Military-grade defense against brute-force attacks).
     */
    fun deriveKeyPbkdf2(
        passphrase: String,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS
    ): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Legacy SHA-256 single-pass key derivation (for V1/V2 archives).
     */
    fun deriveKeySha256(passphrase: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }
}
