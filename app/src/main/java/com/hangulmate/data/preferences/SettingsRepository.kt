package com.hangulmate.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hangulmate_settings",
)

/**
 * User settings, persisted in a Preferences DataStore named "hangulmate_settings".
 * All values are exposed as Flows with the defaults listed below.
 *
 * NOTE: this file intentionally contains the interface AND its DataStore
 * implementation. The data-layer agent owns domain/ and data/repository/;
 * the settings contract lives here because the foundation (theme, locale,
 * TTS, profile screen) depends on it directly.
 */
interface SettingsRepository {

    /** Words to learn per day. Default 10. */
    val dailyGoal: Flow<Int>

    /** Interface language: "ne" (Nepali, default) or "en" (English). */
    val interfaceLanguage: Flow<String>

    /** Theme: "system" (default), "light" or "dark". */
    val themeMode: Flow<String>

    /** TTS speech rate, 0.5f–2.0f. Default 1.0f. */
    val ttsRate: Flow<Float>

    /** TTS pitch, 0.5f–2.0f. Default 1.0f. */
    val ttsPitch: Flow<Float>

    /** Daily study reminders. Default false (off until the user opts in). */
    val remindersEnabled: Flow<Boolean>

    /** Hour of day (0–23) for the study reminder. Default 19 (7 PM). */
    val reminderHour: Flow<Int>

    /** True once the user has completed onboarding. Default false. */
    val onboardingCompleted: Flow<Boolean>

    /** Epoch day the [todayShownWordIds] set belongs to (0 = none recorded). */
    val todayShownWordDay: Flow<Long>

    /** Ids shown in the Home "today's new words" strip today. */
    val todayShownWordIds: Flow<Set<String>>

    /** Ids shown in that strip on days before [todayShownWordDay]. */
    val shownNewWordIdsBeforeToday: Flow<Set<String>>

    suspend fun setDailyGoal(goal: Int)
    suspend fun setInterfaceLanguage(language: String)
    suspend fun setThemeMode(mode: String)
    suspend fun setTtsRate(rate: Float)
    suspend fun setTtsPitch(pitch: Float)
    suspend fun setRemindersEnabled(enabled: Boolean)
    suspend fun setReminderHour(hour: Int)
    suspend fun setOnboardingCompleted(completed: Boolean)

    /**
     * Records a new day's strip in one edit: [day] becomes the current
     * strip day, [todayIds] replaces today's shown set and
     * [beforeTodayIds] replaces the earlier-days set (rollover).
     */
    suspend fun setTodayShownWords(day: Long, todayIds: Set<String>, beforeTodayIds: Set<String>)

    /** Unions [ids] into today's shown set. */
    suspend fun addTodayShownWordIds(ids: Set<String>)
}

class SettingsRepositoryImpl(
    context: Context,
) : SettingsRepository {

    private val dataStore: DataStore<Preferences> = context.applicationContext.settingsDataStore

    override val dailyGoal: Flow<Int> =
        dataStore.data.map { it[Keys.DAILY_GOAL] ?: Defaults.DAILY_GOAL }

    override val interfaceLanguage: Flow<String> =
        dataStore.data.map { it[Keys.INTERFACE_LANGUAGE] ?: Defaults.INTERFACE_LANGUAGE }

    override val themeMode: Flow<String> =
        dataStore.data.map { it[Keys.THEME_MODE] ?: Defaults.THEME_MODE }

    override val ttsRate: Flow<Float> =
        dataStore.data.map { it[Keys.TTS_RATE] ?: Defaults.TTS_RATE }

    override val ttsPitch: Flow<Float> =
        dataStore.data.map { it[Keys.TTS_PITCH] ?: Defaults.TTS_PITCH }

    override val remindersEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.REMINDERS_ENABLED] ?: Defaults.REMINDERS_ENABLED }

    override val reminderHour: Flow<Int> =
        dataStore.data.map { it[Keys.REMINDER_HOUR] ?: Defaults.REMINDER_HOUR }

    override val onboardingCompleted: Flow<Boolean> =
        dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: Defaults.ONBOARDING_COMPLETED }

    override val todayShownWordDay: Flow<Long> =
        dataStore.data.map { it[Keys.TODAY_SHOWN_WORD_DAY] ?: Defaults.TODAY_SHOWN_WORD_DAY }

    override val todayShownWordIds: Flow<Set<String>> =
        dataStore.data.map { it[Keys.TODAY_SHOWN_WORD_IDS] ?: Defaults.TODAY_SHOWN_WORD_IDS }

    override val shownNewWordIdsBeforeToday: Flow<Set<String>> =
        dataStore.data.map {
            it[Keys.SHOWN_NEW_WORD_IDS_BEFORE_TODAY] ?: Defaults.SHOWN_NEW_WORD_IDS_BEFORE_TODAY
        }

    override suspend fun setDailyGoal(goal: Int) {
        dataStore.edit { it[Keys.DAILY_GOAL] = goal.coerceIn(1, 50) }
    }

    override suspend fun setInterfaceLanguage(language: String) {
        val normalised = if (language == "en") "en" else "ne"
        dataStore.edit { it[Keys.INTERFACE_LANGUAGE] = normalised }
    }

    override suspend fun setThemeMode(mode: String) {
        val normalised = when (mode) {
            "light", "dark", "system" -> mode
            else -> "system"
        }
        dataStore.edit { it[Keys.THEME_MODE] = normalised }
    }

    override suspend fun setTtsRate(rate: Float) {
        dataStore.edit { it[Keys.TTS_RATE] = rate.coerceIn(0.5f, 2.0f) }
    }

    override suspend fun setTtsPitch(pitch: Float) {
        dataStore.edit { it[Keys.TTS_PITCH] = pitch.coerceIn(0.5f, 2.0f) }
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.REMINDERS_ENABLED] = enabled }
    }

    override suspend fun setReminderHour(hour: Int) {
        dataStore.edit { it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23) }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setTodayShownWords(
        day: Long,
        todayIds: Set<String>,
        beforeTodayIds: Set<String>,
    ) {
        dataStore.edit {
            it[Keys.TODAY_SHOWN_WORD_DAY] = day
            it[Keys.TODAY_SHOWN_WORD_IDS] = todayIds
            it[Keys.SHOWN_NEW_WORD_IDS_BEFORE_TODAY] = beforeTodayIds
        }
    }

    override suspend fun addTodayShownWordIds(ids: Set<String>) {
        dataStore.edit {
            val current = it[Keys.TODAY_SHOWN_WORD_IDS] ?: emptySet()
            it[Keys.TODAY_SHOWN_WORD_IDS] = current + ids
        }
    }

    private object Keys {
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val INTERFACE_LANGUAGE = stringPreferencesKey("interface_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val TODAY_SHOWN_WORD_DAY = longPreferencesKey("today_shown_word_day")
        val TODAY_SHOWN_WORD_IDS = stringSetPreferencesKey("today_shown_word_ids")
        val SHOWN_NEW_WORD_IDS_BEFORE_TODAY =
            stringSetPreferencesKey("shown_new_word_ids_before_today")
    }

    private object Defaults {
        const val DAILY_GOAL = 10
        const val INTERFACE_LANGUAGE = "ne"
        const val THEME_MODE = "system"
        const val TTS_RATE = 1.0f
        const val TTS_PITCH = 1.0f
        const val REMINDERS_ENABLED = false
        const val REMINDER_HOUR = 19
        const val ONBOARDING_COMPLETED = false
        const val TODAY_SHOWN_WORD_DAY = 0L
        val TODAY_SHOWN_WORD_IDS: Set<String> = emptySet()
        val SHOWN_NEW_WORD_IDS_BEFORE_TODAY: Set<String> = emptySet()
    }
}
