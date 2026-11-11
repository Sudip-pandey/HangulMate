package com.hangulmate.locale

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Applies the interface language chosen in settings.
 *
 * Uses AppCompat's per-app locales so the choice survives process death
 * and does not require the app to manage Configuration itself.
 */
object LocaleManager {

    /** Apply [languageTag] ("ne" or "en") as the app's locale. */
    fun applyLanguage(context: Context, languageTag: String) {
        val tag = if (languageTag == "en") "en" else "ne"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Recreate [activity] so the new locale takes effect on screen. */
    fun recreateActivity(activity: Activity) {
        activity.recreate()
    }
}
