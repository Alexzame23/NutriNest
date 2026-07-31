package com.zame.nutrinest.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.zame.nutrinest.R
import java.util.Locale

/** The languages the app ships translations for. */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    ENGLISH("en", R.string.language_english),
    SPANISH("es", R.string.language_spanish);

    /** Two-letter badge for the switch button; the same in every language. */
    val shortLabel: String get() = tag.uppercase(Locale.ROOT)

    companion object {
        fun forTag(tag: String?): AppLanguage? {
            val language = tag?.substringBefore('-')?.lowercase(Locale.ROOT)
            return entries.firstOrNull { it.tag == language }
        }
    }
}

/**
 * Per-app language, chosen in the app rather than in Android's settings.
 *
 * Android 13 gave the platform a proper per-app language API, and where it
 * exists it is the one to use: the choice shows up in system settings, survives
 * reinstalls and restarts the activity itself. Below that there is no platform
 * support at all, so the choice is stored in preferences and applied by handing
 * every Activity a context whose configuration carries the locale — which is
 * why [wrap] has to run from `attachBaseContext`, before any resource is read.
 */
object AppLocale {

    private const val PREFS = "nutrinest_settings"
    private const val KEY_LANGUAGE = "app_language"

    /** The language currently in effect, whether chosen here or inherited. */
    fun current(context: Context): AppLanguage {
        val chosen = stored(context)
        if (chosen != null) return chosen
        // Configuration rather than Locale.getDefault(): it reflects the
        // override applied by wrap(), and the device language when there is none.
        val configured = context.resources.configuration.locales[0].language
        return AppLanguage.forTag(configured) ?: AppLanguage.ENGLISH
    }

    /** The explicit choice, or null when the app is following the device. */
    fun stored(context: Context): AppLanguage? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?.takeIf { !it.isEmpty }
                ?.get(0)
                ?.language
        } else {
            prefs(context).getString(KEY_LANGUAGE, null)
        }
        return AppLanguage.forTag(tag)
    }

    /** Applies [language] and restarts the activity so every string re-reads. */
    fun apply(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The framework persists this and recreates the activity for us.
            activity.getSystemService(LocaleManager::class.java)
                ?.applicationLocales = LocaleList.forLanguageTags(language.tag)
        } else {
            prefs(activity).edit().putString(KEY_LANGUAGE, language.tag).apply()
            Locale.setDefault(Locale.forLanguageTag(language.tag))
            activity.recreate()
        }
    }

    /**
     * Wraps a base context in the stored language. A no-op on Android 13+,
     * where the platform has already applied the choice.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY_LANGUAGE, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        // Also the process default, so Collator, java.time and String.format in
        // the ViewModels agree with what the screens are showing.
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Walks out of Compose's wrapped contexts to the hosting Activity. */
fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
