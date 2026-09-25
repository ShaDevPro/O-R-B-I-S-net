package com.sha.orbis.update

import org.json.JSONObject

data class RemoteAppConfig(
    val minRequiredVersionCode: Int,
    val minRequiredVersionName: String,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val forceUpdate: Boolean,
    val downloadUrl: String,
    val releaseNotesFr: String,
    val releaseNotesEn: String,
    val releaseNotesAr: String
) {
    fun getReleaseNoteForLocale(localeLanguage: String): String {
        return when (localeLanguage.lowercase()) {
            "ar" -> releaseNotesAr.ifBlank { releaseNotesFr }
            "en" -> releaseNotesEn.ifBlank { releaseNotesFr }
            else -> releaseNotesFr.ifBlank { releaseNotesEn }
        }
    }

    companion object {
        val DEFAULT = RemoteAppConfig(
            minRequiredVersionCode = 130,
            minRequiredVersionName = "1.3.0",
            latestVersionCode = 130,
            latestVersionName = "1.3.0",
            forceUpdate = true,
            downloadUrl = "https://github.com/ShaDevPro/O-R-B-I-S-net/releases/download/OrbisNet-v1.3.0/O.R.B.I.S.apk",
            releaseNotesFr = "Version 1.3.0 : Discussions privées, masquage de publications dans le fil d'actualité, stabilité des appels 4G/4G et correction de bugs.",
            releaseNotesEn = "Version 1.3.0: Private discussions, post hiding in feed, 4G/4G call stability and bug fixes.",
            releaseNotesAr = "الإصدار 1.3.0: محادثات خاصة، إخفاء المنشورات في الخلاصة، استقرار المكالمات 4G واصلاح الأخطاء."
        )

        fun fromJson(json: JSONObject): RemoteAppConfig {
            val notesObj = json.optJSONObject("releaseNotes")
            return RemoteAppConfig(
                minRequiredVersionCode = json.optInt("minRequiredVersionCode", 130),
                minRequiredVersionName = json.optString("minRequiredVersionName", "1.3.0"),
                latestVersionCode = json.optInt("latestVersionCode", 130),
                latestVersionName = json.optString("latestVersionName", "1.3.0"),
                forceUpdate = json.optBoolean("forceUpdate", true),
                downloadUrl = json.optString("downloadUrl", "https://github.com/ShaDevPro/O-R-B-I-S-net/releases/download/OrbisNet-v1.3.0/O.R.B.I.S.apk"),
                releaseNotesFr = notesObj?.optString("fr", "") ?: "",
                releaseNotesEn = notesObj?.optString("en", "") ?: "",
                releaseNotesAr = notesObj?.optString("ar", "") ?: ""
            )
        }

        fun toJson(config: RemoteAppConfig): JSONObject {
            val obj = JSONObject()
            obj.put("minRequiredVersionCode", config.minRequiredVersionCode)
            obj.put("minRequiredVersionName", config.minRequiredVersionName)
            obj.put("latestVersionCode", config.latestVersionCode)
            obj.put("latestVersionName", config.latestVersionName)
            obj.put("forceUpdate", config.forceUpdate)
            obj.put("downloadUrl", config.downloadUrl)
            val notes = JSONObject()
            notes.put("fr", config.releaseNotesFr)
            notes.put("en", config.releaseNotesEn)
            notes.put("ar", config.releaseNotesAr)
            obj.put("releaseNotes", notes)
            return obj
        }
    }
}
