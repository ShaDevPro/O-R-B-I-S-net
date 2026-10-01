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
            minRequiredVersionCode = 100,
            minRequiredVersionName = "1.0.0",
            latestVersionCode = 140,
            latestVersionName = "1.4.0",
            forceUpdate = false,
            downloadUrl = "https://github.com/ShaDevPro/O-R-B-I-S-net/releases/download/OrbisNet-v1.4.0/O.R.B.I.S.apk",
            releaseNotesFr = "Version 1.4.0 : Optimisations majeures du fil d'actualité, aperçus fluides des liens externes sans blocage, navigation épurée dans le journal d'appels et stabilité renforcée.",
            releaseNotesEn = "Version 1.4.0: Major feed performance optimizations, smooth external link previews without freezing, refined call history navigation, and enhanced stability.",
            releaseNotesAr = "الإصدار 1.4.0: تحسينات كبرى في أداء الخلاصة ومعاينات الروابط الخارجية بسلاسة، واجهة تنقل محسنة في سجل المكالمات واستقرار عام."
        )

        fun fromJson(json: JSONObject): RemoteAppConfig {
            val notesObj = json.optJSONObject("releaseNotes")
            return RemoteAppConfig(
                minRequiredVersionCode = json.optInt("minRequiredVersionCode", 100),
                minRequiredVersionName = json.optString("minRequiredVersionName", "1.0.0"),
                latestVersionCode = json.optInt("latestVersionCode", 140),
                latestVersionName = json.optString("latestVersionName", "1.4.0"),
                forceUpdate = json.optBoolean("forceUpdate", false),
                downloadUrl = json.optString("downloadUrl", "https://github.com/ShaDevPro/O-R-B-I-S-net/releases/download/OrbisNet-v1.4.0/O.R.B.I.S.apk"),
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
