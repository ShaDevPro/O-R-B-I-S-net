package com.sha.orbis.storage

import android.content.Context
import android.util.Log
import com.sha.orbis.data.SessionManager
import java.io.File

/**
 * Gestionnaire d'isolation hermétique du stockage par compte / ligne SIM.
 * Chaque compte possède son propre sous-dossier dédié :
 * context.filesDir/accounts/<cleanAccountId>/
 *
 * Élimine toute fuite ou mélange de données entre la Ligne 1 et la Ligne 2.
 */
object AccountStorageManager {

    private const val TAG = "AccountStorageManager"

    /**
     * Nettoie et sécurise un identifiant de compte pour le système de fichiers.
     * Exemple : "+213 550 12 34 56" -> "213550123456"
     */
    fun sanitizeAccountId(raw: String): String {
        val clean = raw.filter { it.isLetterOrDigit() || it == '_' }
        return if (clean.isNotBlank()) clean else "default_account"
    }

    /**
     * Retourne l'identifiant propre et sécurisé pour le système de fichiers.
     * Exemple : "acc_213550123456"
     */
    fun getCleanAccountId(context: Context, accountId: String? = null): String {
        val raw = accountId?.takeIf { it.isNotBlank() } ?: try {
            SessionManager(context).activeAccountId
        } catch (_: Exception) {
            ""
        }
        return sanitizeAccountId(raw)
    }

    /**
     * Retourne le répertoire hermétique dédié au compte spécifié (ou compte actif).
     * Crée le répertoire s'il n'existe pas.
     */
    fun getAccountDirectory(context: Context, accountId: String? = null): File {
        val cleanId = getCleanAccountId(context, accountId)
        val accountsBase = File(context.filesDir, "accounts")
        val dir = File(accountsBase, cleanId)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Retourne un fichier spécifique à l'intérieur du coffre isolé du compte.
     */
    fun getAccountFile(context: Context, fileName: String, accountId: String? = null): File {
        return File(getAccountDirectory(context, accountId), fileName)
    }

    /**
     * Retourne un sous-dossier à l'intérieur du coffre isolé du compte (ex: "messages").
     */
    fun getAccountSubdir(context: Context, dirName: String, accountId: String? = null): File {
        val subdir = File(getAccountDirectory(context, accountId), dirName)
        if (!subdir.exists()) {
            subdir.mkdirs()
        }
        return subdir
    }

    /**
     * Migration des anciennes données racine vers le coffre du compte PRIMAIRE uniquement.
     * Cette fonction n'est JAMAIS exécutée pour un nouveau compte secondaire (ex: Ligne 2),
     * garantissant ainsi que le compte 2 démarre avec une base 100% vierge.
     */
    @Synchronized
    fun migrateLegacyDataForPrimaryAccountIfNeeded(context: Context, primaryAccountId: String) {
        try {
            val sessionManager = SessionManager(context)
            val accounts = sessionManager.getAccounts()
            val firstAccount = accounts.firstOrNull() ?: return
            
            // Seul le premier compte historique a le droit d'hériter des fichiers de la racine
            if (firstAccount.id != primaryAccountId) {
                Log.d(TAG, "Compte $primaryAccountId n'est pas le compte primaire, aucune migration héritée.")
                return
            }

            val targetDir = getAccountDirectory(context, primaryAccountId)

            val legacyMapping = mapOf(
                "conversations.json" to "conversations.json",
                "contacts.json" to "contacts.json",
                "orbis_friend_requests.json" to "friend_requests.json",
                "orbis_social_posts.json" to "social_posts.json",
                "orbis_social_stories.json" to "social_stories.json",
                "orbis_deleted_post_ids.json" to "deleted_post_ids.json",
                "orbis_call_history.json" to "call_history.json",
                "orbis_friend_circles.json" to "friend_circles.json",
                "orbis_blocked_contacts.json" to "blocked_contacts.json",
                "orbis_inapp_notifications.json" to "notifications.json"
            )

            for ((legacyName, newName) in legacyMapping) {
                val legacyFile = File(context.filesDir, legacyName)
                val targetFile = File(targetDir, newName)
                if (legacyFile.exists() && !targetFile.exists()) {
                    try {
                        legacyFile.copyTo(targetFile, overwrite = false)
                        Log.i(TAG, "Migration fichier $legacyName -> $newName pour compte primaire $primaryAccountId")
                    } catch (e: Exception) {
                        Log.w(TAG, "Échec copie $legacyName : ${e.message}")
                    }
                }
            }

            // Migration dossier messages
            val legacyMessagesDir = File(context.filesDir, "messages")
            val targetMessagesDir = File(targetDir, "messages")
            if (legacyMessagesDir.exists() && legacyMessagesDir.isDirectory && (!targetMessagesDir.exists() || targetMessagesDir.list().isNullOrEmpty())) {
                if (!targetMessagesDir.exists()) targetMessagesDir.mkdirs()
                legacyMessagesDir.listFiles()?.forEach { file ->
                    val target = File(targetMessagesDir, file.name)
                    if (!target.exists()) {
                        try {
                            file.copyTo(target, overwrite = false)
                        } catch (_: Exception) {}
                    }
                }
                Log.i(TAG, "Migration répertoire messages vers compte primaire effectué.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur globale migration legacy : ${e.message}")
        }
    }
}
