package com.sha.orbis.ui.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleManager {
    private const val PREFS_NAME = "orbis_locale_prefs"
    private const val KEY_SELECTED_LANGUAGE = "selected_language"
    private val supportedLocales = setOf("fr", "en", "ar")

    val currentLanguageState = mutableStateOf("fr")

    fun init(context: Context) {
        val saved = getSavedLanguage(context)
        val initial = saved ?: resolveSystemLocale(context).language
        currentLanguageState.value = if (supportedLocales.contains(initial)) initial else "fr"
        applyLocale(context, localeFromCode(currentLanguageState.value), persist = false)
    }

    fun applySystemLocale(context: Context) {
        val locale = resolveSystemLocale(context)
        currentLanguageState.value = locale.language
        applyLocale(context, locale, persist = false)
    }

    fun applySavedLocale(context: Context) {
        init(context)
    }

    fun setLanguage(context: Context, languageCode: String) {
        val cleanCode = if (supportedLocales.contains(languageCode.lowercase())) languageCode.lowercase() else "fr"
        currentLanguageState.value = cleanCode
        val locale = localeFromCode(cleanCode)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_SELECTED_LANGUAGE, cleanCode) }

        applyLocale(context, locale, persist = true)
    }

    fun getSavedLanguage(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_LANGUAGE, null)

    fun resolveSystemLocale(context: Context): Locale {
        val systemLanguage = AppCompatDelegate.getApplicationLocales().get(0)?.language
            ?: context.resources.configuration.locales.get(0)?.language
            ?: Locale.getDefault().language

        return localeFromCode(systemLanguage)
    }

    fun localeFromCode(languageCode: String): Locale {
        val normalized = languageCode.lowercase(Locale.ROOT)
        return if (supportedLocales.contains(normalized)) {
            Locale.forLanguageTag(normalized)
        } else {
            Locale.FRENCH
        }
    }

    fun applyLocale(context: Context, locale: Locale, persist: Boolean = true) {
        try {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale.toLanguageTag()))
        } catch (_: Exception) {}

        if (persist) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit { putString(KEY_SELECTED_LANGUAGE, locale.language) }
        }
    }
}

/**
 * Universal Reactive Locale & RTL Provider for Jetpack Compose.
 * Preserves the Activity context (preserving ActivityResultRegistryOwner, Window, etc.)
 * while dynamically updating strings and layout direction across the entire UI tree.
 */
@Composable
fun ProvideOrbisLocale(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val currentLang = LocaleManager.currentLanguageState.value
    val locale = remember(currentLang) { LocaleManager.localeFromCode(currentLang) }

    val baseConfig = LocalConfiguration.current
    val configuration = remember(locale, baseConfig) {
        Configuration(baseConfig).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }

    val layoutDirection = if (currentLang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    // Update Context Resources in real time without replacing the Activity instance
    DisposableEffect(locale) {
        val res = context.resources
        val conf = res.configuration
        conf.setLocale(locale)
        conf.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(conf, res.displayMetrics)
        onDispose { }
    }

    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides layoutDirection
    ) {
        content()
    }
}
