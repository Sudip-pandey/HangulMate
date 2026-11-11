package com.hangulmate.ui.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hangulmate.data.backup.BackupManager
import com.hangulmate.data.preferences.SettingsRepository
import com.hangulmate.domain.model.ImportReport
import com.hangulmate.domain.repository.LearningRepository
import com.hangulmate.domain.repository.VocabularyRepository
import com.hangulmate.speech.TtsManager
import com.hangulmate.speech.TtsState
import com.hangulmate.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Real learning statistics for the Profile screen. Zero is a real value: a fresh user sees 0s. */
data class ProfileStats(
    val streak: Int = 0,
    val learned: Int = 0,
    val mastered: Int = 0,
    val totalWords: Int = 0,
    val quizzesTaken: Int = 0,
)

/** Metadata of the latest backup saved in app-private storage. */
data class LocalBackupInfo(
    val fileName: String,
    val lastModifiedMillis: Long,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ttsManager: TtsManager,
    private val vocabularyRepository: VocabularyRepository,
    private val learningRepository: LearningRepository,
    private val backupManager: BackupManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val dailyGoal: Flow<Int> = settingsRepository.dailyGoal
    val interfaceLanguage: Flow<String> = settingsRepository.interfaceLanguage
    val themeMode: Flow<String> = settingsRepository.themeMode
    val ttsRate: Flow<Float> = settingsRepository.ttsRate
    val ttsPitch: Flow<Float> = settingsRepository.ttsPitch
    val remindersEnabled: Flow<Boolean> = settingsRepository.remindersEnabled
    val reminderHour: Flow<Int> = settingsRepository.reminderHour

    val ttsState: StateFlow<TtsState> = ttsManager.state

    /** Live statistics; zeroes until the user starts learning. */
    val stats: StateFlow<ProfileStats> = combine(
        learningRepository.getStreak(),
        vocabularyRepository.getLearnedCount(),
        vocabularyRepository.getMasteredCount(),
        vocabularyRepository.getTotalCount(),
        learningRepository.getQuizCount(),
    ) { streak, learned, mastered, totalWords, quizzesTaken ->
        ProfileStats(streak, learned, mastered, totalWords, quizzesTaken)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileStats())

    private val _localBackupInfo = MutableStateFlow<LocalBackupInfo?>(null)
    val localBackupInfo: StateFlow<LocalBackupInfo?> = _localBackupInfo

    init {
        refreshLocalBackupInfo()
        // Re-assert the saved reminder schedule (e.g. after the work was
        // lost): a no-op when nothing changed.
        viewModelScope.launch {
            val enabled = settingsRepository.remindersEnabled.first()
            val hour = settingsRepository.reminderHour.first()
            ReminderScheduler.schedule(appContext, enabled, hour)
        }
    }

    fun setDailyGoal(goal: Int) {
        viewModelScope.launch { settingsRepository.setDailyGoal(goal) }
    }

    fun setInterfaceLanguage(language: String) {
        viewModelScope.launch { settingsRepository.setInterfaceLanguage(language) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    /** Apply a TTS rate live (to the engine) without persisting yet. */
    fun previewTtsRate(rate: Float) {
        ttsManager.setRate(rate)
    }

    /** Apply a TTS pitch live (to the engine) without persisting yet. */
    fun previewTtsPitch(pitch: Float) {
        ttsManager.setPitch(pitch)
    }

    fun setTtsRate(rate: Float) {
        viewModelScope.launch { settingsRepository.setTtsRate(rate) }
    }

    fun setTtsPitch(pitch: Float) {
        viewModelScope.launch { settingsRepository.setTtsPitch(pitch) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRemindersEnabled(enabled)
            val hour = settingsRepository.reminderHour.first()
            ReminderScheduler.schedule(appContext, enabled, hour)
        }
    }

    fun setReminderHour(hour: Int) {
        viewModelScope.launch {
            settingsRepository.setReminderHour(hour)
            val enabled = settingsRepository.remindersEnabled.first()
            ReminderScheduler.schedule(appContext, enabled, hour)
        }
    }

    /** True on Android < 13 (no runtime notification permission there). */
    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Speak the test greeting. Does nothing unless the Korean voice is ready. */
    fun testVoice() {
        ttsManager.speak("안녕하세요")
    }

    // ------------------------------------------------------------------
    // Backup & restore
    // ------------------------------------------------------------------

    /** Full export JSON (words + progress + favorites). */
    suspend fun exportBackupJson(): String = backupManager.exportJson()

    /** Writes the export to the user-picked document; false when the write fails. */
    suspend fun exportToUri(uri: Uri): Boolean = runCatching {
        appContext.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(exportBackupJson().toByteArray(Charsets.UTF_8))
        } ?: return false
        true
    }.getOrDefault(false)

    /**
     * Imports a backup file picked by the user. Returns the import report,
     * or null when the file cannot be read at all.
     */
    suspend fun importFromUri(uri: Uri): ImportReport? = runCatching {
        val text = appContext.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: return null
        backupManager.importJson(text)
    }.getOrNull()

    /** Saves a backup in app-private storage and refreshes [localBackupInfo]. */
    suspend fun writeLocalBackup(): Boolean = runCatching {
        backupManager.writeLocalBackup()
        refreshLocalBackupInfo()
        true
    }.getOrDefault(false)

    fun refreshLocalBackupInfo() {
        _localBackupInfo.value = backupManager.latestLocalBackup()?.let { file ->
            LocalBackupInfo(fileName = file.name, lastModifiedMillis = file.lastModified())
        }
    }

    // ------------------------------------------------------------------
    // Reset progress
    // ------------------------------------------------------------------

    /**
     * Clears learning progress, favorites, search history, quiz results
     * and collections. Vocabulary words (seed + user-created) are never
     * deleted — see [VocabularyRepository.clearAllProgress].
     */
    suspend fun resetProgress() {
        vocabularyRepository.clearAllProgress()
    }
}
