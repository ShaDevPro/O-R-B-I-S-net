package com.sha.orbis.ui.components

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.data.ContactsPickerHelper
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.Contact
import com.sha.orbis.model.Conversation
import com.sha.orbis.model.FriendRequest
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object AvatarManager {

    /**
     * Extracts and ensures the official Orbis app/launcher icon is cached on disk as an avatar.
     * Features the official White background (#FFFFFF) with the Sovereign Black Shield (#0E1117).
     */
    fun ensureOfficialAppAvatar(context: Context): String? {
        return try {
            val dir = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
            val destFile = File(dir, "orbis_official_logo.png")
            val versionMarker = File(dir, "orbis_official_logo.v2")
            if (!destFile.exists() || destFile.length() == 0L || !versionMarker.exists()) {
                val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_orbis_app_avatar)
                    ?: androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                    ?: androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
                if (drawable != null) {
                    val size = 512
                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    val bgPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                    FileOutputStream(destFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    try { versionMarker.createNewFile() } catch (_: Exception) {}
                }
            }
            if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Saves an image Uri into an internal auto-adjusted, center-cropped (1:1 square) and EXIF-oriented JPEG file.
     * Guaranteed 512x512 circular-safe aspect ratio.
     */
    fun saveAvatarFromUri(context: Context, imageUri: Uri, identifier: String): String? {
        return try {
            // 1. Read EXIF Orientation
            var rotationDegrees = 0f
            try {
                context.contentResolver.openInputStream(imageUri)?.use { input: InputStream ->
                    val exif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        ExifInterface(input)
                    } else {
                        null
                    }
                    val orientation = exif?.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    ) ?: ExifInterface.ORIENTATION_NORMAL

                    rotationDegrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }
            } catch (_: Exception) {}

            // 2. Decode Raw Bitmap
            val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            } ?: return null

            // 3. Apply EXIF Rotation
            val orientedBitmap = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }

            // 4. Automatic Center-Crop (1:1 Square adjustment for perfect circle fitting)
            val width = orientedBitmap.width
            val height = orientedBitmap.height
            val minDim = minOf(width, height)
            val xOffset = (width - minDim) / 2
            val yOffset = (height - minDim) / 2

            val squareBitmap = Bitmap.createBitmap(orientedBitmap, xOffset, yOffset, minDim, minDim)

            // 5. Scale to standard 512x512
            val targetSize = 512
            val finalBitmap = if (minDim != targetSize) {
                Bitmap.createScaledBitmap(squareBitmap, targetSize, targetSize, true)
            } else {
                squareBitmap
            }

            // 6. Save locally
            val dir = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
            val cleanId = identifier.filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "avatar_${System.currentTimeMillis()}" }
            val destFile = File(dir, "${cleanId}.jpg")

            FileOutputStream(destFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getAvatarBitmap(context: Context, avatarPath: String?): Bitmap? {
        return loadAvatarBitmap(avatarPath)
    }

    /**
     * Generates a high-clarity compressed Base64 thumbnail (default 96x96) for QR Code or P2P payload exchange.
     */
    fun getAvatarAsBase64Thumbnail(avatarPath: String?, sizePx: Int = 96): String? {
        if (avatarPath.isNullOrBlank()) return null
        return try {
            val fullBitmap = loadAvatarBitmap(avatarPath) ?: return null
            val thumb = Bitmap.createScaledBitmap(fullBitmap, sizePx, sizePx, true)

            val out = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, 75, out)
            val bytes = out.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves an avatar from an incoming Base64 string into internal app storage as a high-quality JPEG file.
     */
    fun saveAvatarFromBase64(context: Context, base64Data: String?, identifier: String): String? {
        if (base64Data.isNullOrBlank()) return null
        return try {
            val clean = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val bytes = Base64.decode(clean.trim(), Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            val dir = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
            val cleanId = identifier.filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "avatar_${System.currentTimeMillis()}" }
            val destFile = File(dir, "${cleanId}.jpg")

            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }

            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Queries the local Android phonebook to find if a contact photo exists for a phone number offline.
     */
    fun findContactPhotoUriByPhoneNumber(context: Context, phoneNumber: String): Uri? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val photoIndex = it.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    if (photoIndex >= 0) {
                        val photoUriString = it.getString(photoIndex)
                        if (!photoUriString.isNullOrBlank()) {
                            return Uri.parse(photoUriString)
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Loads a local Bitmap safely from the file path.
     */
    fun loadAvatarBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        // 1. Fast L1 Memory Cache Lookup (< 1ms)
        com.sha.orbis.cache.AvatarMemoryCache.get(path)?.let { return it }

        return try {
            val file = File(path)
            val bitmap = if (file.exists() && file.length() > 0) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else if (path.length > 50 && (path.startsWith("/9j/") || path.startsWith("iVBOR") || path.startsWith("data:image") || !path.contains(File.separator))) {
                val clean = if (path.contains(",")) path.substringAfter(",") else path
                val bytes = Base64.decode(clean.trim(), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                null
            }
            if (bitmap != null) {
                com.sha.orbis.cache.AvatarMemoryCache.put(path, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves the best avatar image path for a conversation.
     * Looks up in contacts, friend requests, and native device phonebook.
     */
    fun resolveConversationAvatar(
        context: Context,
        conversation: Conversation,
        contacts: List<Contact>? = null,
        friendRequests: List<FriendRequest>? = null
    ): String? {
        if (conversation.isGroup) {
            return null
        }

        val sessionManager = SessionManager(context)
        val currentPhone = sessionManager.userPhone
        val currentAccountId = sessionManager.activeAccountId

        // 1. Determine candidate peer phone number(s) and titles
        val rawPeer = conversation.participants.firstOrNull { it != "me" && !FriendRequestRepository.isSamePhone(it, currentPhone) } ?: ""
        val idCandidate = conversation.id.removePrefix("conv_").filter { it.isDigit() }
        val titleCandidate = conversation.title

        val dialCode = com.sha.orbis.model.CountryCode.defaultCountry(context).dialCode
        val normalizedPeer = if (rawPeer.filter { it.isDigit() }.length >= 6) {
            ContactsPickerHelper.normalizePhoneNumber(rawPeer, dialCode)
        } else ""

        val loadedContacts = contacts ?: ConversationRepository(context, currentAccountId).loadContacts()

        // 2. Check in Orbis Contacts
        val matchingContact = loadedContacts.find { c ->
            (normalizedPeer.isNotBlank() && FriendRequestRepository.isSamePhone(c.phone, normalizedPeer)) ||
            (rawPeer.isNotBlank() && FriendRequestRepository.isSamePhone(c.phone, rawPeer)) ||
            (idCandidate.length >= 6 && FriendRequestRepository.isSamePhone(c.phone, idCandidate)) ||
            c.name.equals(titleCandidate, ignoreCase = true) ||
            conversation.participants.any { p -> p != "me" && FriendRequestRepository.isSamePhone(c.phone, p) }
        }

        if (matchingContact != null && !matchingContact.avatarPath.isNullOrBlank()) {
            val file = File(matchingContact.avatarPath)
            if (file.exists() && file.length() > 0) {
                return matchingContact.avatarPath
            } else if (matchingContact.avatarPath.length > 50) {
                // Auto-persist Base64 to disk
                val cleanId = (matchingContact.phone.filter { it.isDigit() }).ifBlank { matchingContact.id }
                val saved = saveAvatarFromBase64(context, matchingContact.avatarPath, "contact_$cleanId")
                if (!saved.isNullOrBlank()) {
                    val updatedContacts = loadedContacts.map {
                        if (it.id == matchingContact.id) it.copy(avatarPath = saved) else it
                    }
                    ConversationRepository(context, currentAccountId).saveContacts(updatedContacts)
                    return saved
                }
            }
        }

        // 3. Check in Friend Requests repository
        val loadedRequests = friendRequests ?: FriendRequestRepository(context).loadRequests()
        val matchingReq = loadedRequests.find { req ->
            (normalizedPeer.isNotBlank() && FriendRequestRepository.isSamePhone(req.senderPhone, normalizedPeer)) ||
            (rawPeer.isNotBlank() && FriendRequestRepository.isSamePhone(req.senderPhone, rawPeer)) ||
            (idCandidate.length >= 6 && FriendRequestRepository.isSamePhone(req.senderPhone, idCandidate)) ||
            req.senderName.equals(titleCandidate, ignoreCase = true) ||
            conversation.participants.any { p -> p != "me" && FriendRequestRepository.isSamePhone(req.senderPhone, p) }
        }

        if (matchingReq != null && !matchingReq.senderAvatarPath.isNullOrBlank()) {
            val file = File(matchingReq.senderAvatarPath)
            if (file.exists() && file.length() > 0) {
                if (matchingContact != null && matchingContact.avatarPath.isNullOrBlank()) {
                    val updatedContacts = loadedContacts.map {
                        if (it.id == matchingContact.id) it.copy(avatarPath = matchingReq.senderAvatarPath) else it
                    }
                    ConversationRepository(context, currentAccountId).saveContacts(updatedContacts)
                }
                return matchingReq.senderAvatarPath
            } else if (matchingReq.senderAvatarPath.length > 50) {
                // Auto-persist Base64 to disk
                val cleanId = (matchingReq.senderPhone.filter { it.isDigit() }).ifBlank { matchingReq.id }
                val saved = saveAvatarFromBase64(context, matchingReq.senderAvatarPath, "req_$cleanId")
                if (!saved.isNullOrBlank()) {
                    if (matchingContact != null) {
                        val updatedContacts = loadedContacts.map {
                            if (it.id == matchingContact.id) it.copy(avatarPath = saved) else it
                        }
                        ConversationRepository(context, currentAccountId).saveContacts(updatedContacts)
                    }
                    return saved
                }
            }
        }

        // 4. Check device contacts / Native Phonebook
        val phoneCandidates = listOfNotNull(
            normalizedPeer.takeIf { it.isNotBlank() },
            rawPeer.takeIf { it.isNotBlank() },
            idCandidate.takeIf { it.length >= 6 },
            titleCandidate.takeIf { it.filter { c -> c.isDigit() }.length >= 6 }
        ).distinct()

        for (candidate in phoneCandidates) {
            val photoUri = findContactPhotoUriByPhoneNumber(context, candidate)
            if (photoUri != null) {
                val cleanDigits = candidate.filter { it.isDigit() }
                val savedPath = saveAvatarFromUri(context, photoUri, "contact_$cleanDigits")
                if (!savedPath.isNullOrBlank()) {
                    if (matchingContact != null) {
                        val updatedContacts = loadedContacts.map {
                            if (it.id == matchingContact.id) it.copy(avatarPath = savedPath) else it
                        }
                        ConversationRepository(context, currentAccountId).saveContacts(updatedContacts)
                    }
                    return savedPath
                }
            }
        }

        return null
    }
}

/**
 * Universal WhatsApp-style Circular Avatar Composable.
 * Renders the auto-adjusted center-cropped photo if avatarPath exists, or falls back to a clean initial.
 */
@Composable
fun OrbisAvatar(
    avatarPath: String?,
    name: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    isOnline: Boolean? = null,
    onClick: (() -> Unit)? = null
) {
    val bitmap = remember(avatarPath) {
        AvatarManager.loadAvatarBitmap(avatarPath)
    }

    val isOfficialOrbis = remember(name) {
        val clean = name.trim().replace(" ", "")
        clean.equals("ORBIS", ignoreCase = true) || clean.equals("ORBISNET", ignoreCase = true) || clean.equals("OrbisCore", ignoreCase = true) || clean.equals("DevCore", ignoreCase = true)
    }

    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Box(
        modifier = modifier.size(size)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .then(clickModifier)
                .background(if (isOfficialOrbis) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isOfficialOrbis) 1.5.dp else 1.dp,
                    color = if (isOfficialOrbis) androidx.compose.ui.graphics.Color(0xFF38BDF8) else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else if (isOfficialOrbis) {
                Image(
                    painter = painterResource(R.drawable.ic_orbis_app_avatar),
                    contentDescription = "O R B I S net",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                val initial = name.trim().take(1).uppercase().ifBlank { "O" }
                val fontSize = (size.value * 0.40f).sp
                Text(
                    text = initial,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (isOnline == true) {
            val dotSize = (size * 0.28f).coerceIn(10.dp, 15.dp)
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(com.sha.orbis.ui.theme.OrbisColorPalette.StatusActive)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}
