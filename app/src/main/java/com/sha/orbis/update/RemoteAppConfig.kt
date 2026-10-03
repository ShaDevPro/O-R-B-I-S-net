package com.sha.orbis.update

import com.sha.orbis.BuildConfig
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
        const val LATEST_APK_URL = "https://github.com/ShaDevPro/O-R-B-I-S-net/releases/latest/download/O.R.B.I.S.apk"

        val DEFAULT: RemoteAppConfig
            get() = RemoteAppConfig(
                minRequiredVersionCode = 100,
                minRequiredVersionName = "1.0.0",
                latestVersionCode = BuildConfig.VERSION_CODE,
                latestVersionName = BuildConfig.VERSION_NAME,
                forceUpdate = false,
                downloadUrl = LATEST_APK_URL,
                releaseNotesFr = "Dernière version officielle d'OrbisNet avec optimisations de performances, sécurité renforcée et correctifs.",
                releaseNotesEn = "Latest official release of OrbisNet with performance optimizations, enhanced security, and bug fixes.",
                releaseNotesAr = "أحدث إصدار رسمي من OrbisNet مع تحسينات الأداء، وتعزيز الأمان، وإصلاح الأخطاء."
            )

        fun fromJson(json: JSONObject): RemoteAppConfig {
            val notesObj = json.optJSONObject("releaseNotes")
            return RemoteAppConfig(
                minRequiredVersionCode = json.optInt("minRequiredVersionCode", 100),
                minRequiredVersionName = json.optString("minRequiredVersionName", "1.0.0"),
                latestVersionCode = json.optInt("latestVersionCode", BuildConfig.VERSION_CODE),
                latestVersionName = json.optString("latestVersionName", BuildConfig.VERSION_NAME),
                forceUpdate = json.optBoolean("forceUpdate", false),
                downloadUrl = json.optString("downloadUrl", LATEST_APK_URL),
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
