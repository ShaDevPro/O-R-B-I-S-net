package com.sha.orbis.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.sha.orbis.ui.components.AvatarManager

object ContactsPickerHelper {

    data class PickedContact(
        val name: String,
        val phoneNumber: String,
        val avatarPath: String? = null
    )

    fun normalizePhoneNumber(rawNumber: String, defaultCountryDialCode: String = "+213"): String {
        val trimmed = rawNumber.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        if (trimmed.startsWith("+")) {
            return "+" + trimmed.filter { it.isDigit() }
        }
        if (trimmed.startsWith("00")) {
            return "+" + trimmed.removePrefix("00").filter { it.isDigit() }
        }
        val cleanDigits = trimmed.filter { it.isDigit() }
        if (cleanDigits.startsWith("0")) {
            val national = cleanDigits.removePrefix("0")
            val dial = if (defaultCountryDialCode.startsWith("+")) defaultCountryDialCode else "+$defaultCountryDialCode"
            return "$dial$national"
        }
        if (cleanDigits.startsWith("213") || cleanDigits.startsWith("33") || cleanDigits.startsWith("212")) {
            return "+$cleanDigits"
        }
        val dial = if (defaultCountryDialCode.startsWith("+")) defaultCountryDialCode else "+$defaultCountryDialCode"
        return "$dial$cleanDigits"
    }

    fun fetchDeviceContacts(context: Context): List<PickedContact> {
        val result = mutableListOf<PickedContact>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (it.moveToNext()) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "Contact" else "Contact"
                    val number = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                    val photoUri = if (photoIdx >= 0) it.getString(photoIdx) else null

                    if (number.isNotBlank()) {
                        val cleanNumber = number.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
                        var localAvatar: String? = null
                        if (!photoUri.isNullOrBlank()) {
                            localAvatar = AvatarManager.saveAvatarFromUri(
                                context = context,
                                imageUri = Uri.parse(photoUri),
                                identifier = "contact_${cleanNumber.filter { c -> c.isDigit() }}"
                            )
                        }
                        result.add(
                            PickedContact(
                                name = name,
                                phoneNumber = cleanNumber,
                                avatarPath = localAvatar
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        return result.distinctBy { it.phoneNumber.filter { c -> c.isDigit() } }
    }

    fun extractContact(context: Context, contactUri: Uri): PickedContact? {
        var name = ""
        var number = ""
        var photoUriString: String? = null

        try {
            val cursor = context.contentResolver.query(
                contactUri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                ),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                    if (nameIndex >= 0) {
                        name = it.getString(nameIndex) ?: ""
                    }
                    if (numberIndex >= 0) {
                        number = it.getString(numberIndex) ?: ""
                    }
                    if (photoIndex >= 0) {
                        photoUriString = it.getString(photoIndex)
                    }
                }
            }

            if (number.isNotBlank()) {
                val cleanNumber = number.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
                var localAvatarPath: String? = null

                if (!photoUriString.isNullOrBlank()) {
                    localAvatarPath = AvatarManager.saveAvatarFromUri(
                        context = context,
                        imageUri = Uri.parse(photoUriString),
                        identifier = "contact_${cleanNumber.filter { it.isDigit() }}"
                    )
                }

                return PickedContact(
                    name = name.ifBlank { "Contact Répertoire" },
                    phoneNumber = cleanNumber,
                    avatarPath = localAvatarPath
                )
            }
        } catch (_: Exception) {
            // Permission or format exception
        }

        return null
    }

    data class NativeContactInfo(
        val displayName: String,
        val phoneNumber: String,
        val photoUri: String? = null
    )

    fun lookupNativeContact(context: Context, rawPhone: String?): NativeContactInfo? {
        if (rawPhone.isNullOrBlank()) return null
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.length < 6) return null

        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawPhone)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.NUMBER,
                    ContactsContract.PhoneLookup.PHOTO_URI
                ),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val numIdx = it.getColumnIndex(ContactsContract.PhoneLookup.NUMBER)
                    val photoIdx = it.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)

                    val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val num = if (numIdx >= 0) it.getString(numIdx) else rawPhone
                    val photo = if (photoIdx >= 0) it.getString(photoIdx) else null

                    if (!name.isNullOrBlank()) {
                        return NativeContactInfo(
                            displayName = name,
                            phoneNumber = num ?: rawPhone,
                            photoUri = photo
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }
}
