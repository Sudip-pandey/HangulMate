package com.hangulmate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hangulmate.R
import com.hangulmate.data.local.DateUtils
import com.hangulmate.data.local.SeedState
import com.hangulmate.data.preferences.SettingsRepository
import com.hangulmate.di.SeedOnFirstLaunch
import com.hangulmate.domain.model.DailyStats
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.repository.LearningRepository
import com.hangulmate.domain.repository.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime
import javax.inject.Inject

/** Everything the Home feed needs, in one snapshot. All lists are real data — never faked. */
data class HomeUiState(
    /** True while the database is still empty and the seed import has not failed. */
    val isPreparing: Boolean = true,
    /** Non-null when the seed import failed and the database is still empty. */
    val seedFailureMessage: String? = null,
    val dailyGoal: Int = 10,
    val wordsLearnedToday: Int = 0,
    val wordsReviewedToday: Int = 0,
    val streakDays: Int = 0,
    val learnedCount: Int = 0,
    val masteredCount: Int = 0,
    val totalCount: Int = 0,
    val wordOfTheDay: VocabularyWord? = null,
    val todayNewWords: List<VocabularyWord> = emptyList(),
    /** Revision queue (due words first, then new words); first item is the Continue card. */
    val revisionQueue: List<VocabularyWord> = emptyList(),
    val recentlySearched: List<VocabularyWord> = emptyList(),
    val difficultWords: List<VocabularyWord> = emptyList(),
    val greetingRes: Int = R.string.home_greeting_morning,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val learningRepository: LearningRepository,
    settingsRepository: SettingsRepository,
    seedOnFirstLaunch: SeedOnFirstLaunch,
) : ViewModel() {

    private val todayEpochDay: Long = DateUtils.today()

    /** Live seed-import state (Idle → Importing → Success | Failed). */
    val seedState: StateFlow<SeedState> = seedOnFirstLaunch.state

    private val _wordOfTheDay = MutableStateFlow<VocabularyWord?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        seedState,
        settingsRepository.dailyGoal,
        learningRepository.getTodayStats(todayEpochDay),
        learningRepository.getStreak(),
        vocabularyRepository.getLearnedCount(),
        vocabularyRepository.getMasteredCount(),
        vocabularyRepository.getTotalCount(),
        vocabularyRepository.getTodayNewWords(
            limit = 10,
            // No "already shown" set is tracked; the repository returns the
            // first unlearned words by EPS importance.
            learnedIds = emptySet(),
        ),
        vocabularyRepository.getRevisionQueue(todayEpochDay),
        vocabularyRepository.getRecentlySearched(limit = 5),
        vocabularyRepository.getDifficult(),
        _wordOfTheDay,
    ) { seed: SeedState,
        dailyGoal: Int,
        todayStats: DailyStats,
        streak: Int,
        learnedCount: Int,
        masteredCount: Int,
        totalCount: Int,
        todayNewWords: List<VocabularyWord>,
        revisionQueue: List<VocabularyWord>,
        recentlySearched: List<VocabularyWord>,
        difficult: List<VocabularyWord>,
        wordOfTheDay: VocabularyWord?
        ->
        HomeUiState(
            isPreparing = totalCount == 0 && seed !is SeedState.Failed,
            seedFailureMessage = (seed as? SeedState.Failed)?.message
                ?.takeIf { totalCount == 0 },
            dailyGoal = dailyGoal,
            wordsLearnedToday = todayStats.wordsLearned,
            wordsReviewedToday = todayStats.wordsReviewed,
            streakDays = streak,
            learnedCount = learnedCount,
            masteredCount = masteredCount,
            totalCount = totalCount,
            wordOfTheDay = wordOfTheDay,
            todayNewWords = todayNewWords,
            revisionQueue = revisionQueue,
            recentlySearched = recentlySearched,
            difficultWords = difficult.take(5),
            greetingRes = greetingResForHour(LocalTime.now().hour),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        // Word of the day is a one-shot suspend call that throws while the
        // database is empty — load it once data exists, then never again
        // (it is deterministic for the day).
        vocabularyRepository.getTotalCount()
            .onEach { total ->
                if (total > 0 && _wordOfTheDay.value == null) {
                    _wordOfTheDay.value = runCatching {
                        vocabularyRepository.getWordOfTheDay(todayEpochDay)
                    }.getOrNull()
                }
            }
            .launchIn(viewModelScope)
    }

    companion object {
        fun greetingResForHour(hour: Int): Int = when (hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..16 -> R.string.home_greeting_afternoon
            in 17..20 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        }
    }
}
