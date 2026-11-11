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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val settingsRepository: SettingsRepository,
    seedOnFirstLaunch: SeedOnFirstLaunch,
) : ViewModel() {

    private val todayEpochDay: Long = DateUtils.today()

    /** Word ids already shown in the "today's new words" strip on earlier days. */
    private val _excludedIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Today's new words, excluding ids already shown on earlier days
     * (until the word gains learning progress — the repository already
     * excludes learned words). Within one day the same 10 keep showing.
     */
    private val todayNewWords: Flow<List<VocabularyWord>> =
        _excludedIds.flatMapLatest { ids ->
            vocabularyRepository.getTodayNewWords(
                limit = TODAY_NEW_WORDS_LIMIT,
                learnedIds = ids,
            )
        }

    /** Live seed-import state (Idle → Importing → Success | Failed). */
    val seedState: StateFlow<SeedState> = seedOnFirstLaunch.state

    private val _wordOfTheDay = MutableStateFlow<VocabularyWord?>(null)

    /**
     * The [uiState] combine's inputs, grouped so each `combine` stays
     * within the typed overloads (`combine` only has typed overloads up
     * to 5 flows in kotlinx-coroutines 1.8.1; a 12-flow call silently
     * falls back to the vararg overload and fails to compile).
     */
    private data class HomeInputsA(
        val seed: SeedState,
        val dailyGoal: Int,
        val todayStats: DailyStats,
        val streak: Int,
        val learnedCount: Int,
    )

    private data class HomeInputsB(
        val masteredCount: Int,
        val totalCount: Int,
        val todayNewWords: List<VocabularyWord>,
        val revisionQueue: List<VocabularyWord>,
        val recentlySearched: List<VocabularyWord>,
    )

    private data class HomeInputsC(
        val difficult: List<VocabularyWord>,
        val wordOfTheDay: VocabularyWord?,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            seedState,
            settingsRepository.dailyGoal,
            learningRepository.getTodayStats(todayEpochDay),
            learningRepository.getStreak(),
            vocabularyRepository.getLearnedCount(),
        ) { seed, dailyGoal, todayStats, streak, learnedCount ->
            HomeInputsA(seed, dailyGoal, todayStats, streak, learnedCount)
        },
        combine(
            vocabularyRepository.getMasteredCount(),
            vocabularyRepository.getTotalCount(),
            todayNewWords,
            vocabularyRepository.getRevisionQueue(todayEpochDay),
            vocabularyRepository.getRecentlySearched(limit = 5),
        ) { masteredCount, totalCount, todayNewWords, revisionQueue, recentlySearched ->
            HomeInputsB(masteredCount, totalCount, todayNewWords, revisionQueue, recentlySearched)
        },
        combine(
            vocabularyRepository.getDifficult(),
            _wordOfTheDay,
        ) { difficult, wordOfTheDay ->
            HomeInputsC(difficult, wordOfTheDay)
        },
    ) { a, b, c ->
        HomeUiState(
            isPreparing = b.totalCount == 0 && a.seed !is SeedState.Failed,
            seedFailureMessage = (a.seed as? SeedState.Failed)?.message
                ?.takeIf { b.totalCount == 0 },
            dailyGoal = a.dailyGoal,
            wordsLearnedToday = a.todayStats.wordsLearned,
            wordsReviewedToday = a.todayStats.wordsReviewed,
            streakDays = a.streak,
            learnedCount = a.learnedCount,
            masteredCount = b.masteredCount,
            totalCount = b.totalCount,
            wordOfTheDay = c.wordOfTheDay,
            todayNewWords = b.todayNewWords,
            revisionQueue = b.revisionQueue,
            recentlySearched = b.recentlySearched,
            difficultWords = c.difficult.take(5),
            greetingRes = greetingResForHour(LocalTime.now().hour),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        // "Today's 10" rotation: ids shown in the strip on earlier days
        // are excluded until learned. On a new day, yesterday's set rolls
        // into the before-today set and the strip shows the next 10.
        // Sequenced in one coroutine so the strip is recorded only after
        // the exclusions are applied (no stale-set race).
        viewModelScope.launch {
            val storedDay = settingsRepository.todayShownWordDay.first()
            val todayIds = settingsRepository.todayShownWordIds.first()
            val beforeIds = settingsRepository.shownNewWordIdsBeforeToday.first()
            val excluded = if (storedDay == todayEpochDay) {
                beforeIds
            } else {
                val rolled = beforeIds + todayIds
                settingsRepository.setTodayShownWords(
                    day = todayEpochDay,
                    todayIds = emptySet(),
                    beforeTodayIds = rolled,
                )
                rolled
            }
            _excludedIds.value = excluded
            val shown = todayNewWords.first { it.isNotEmpty() }
            settingsRepository.addTodayShownWordIds(shown.map { it.id }.toSet())
        }
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
        /** Words shown in the Home "today's new words" strip. */
        private const val TODAY_NEW_WORDS_LIMIT = 10

        fun greetingResForHour(hour: Int): Int = when (hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..16 -> R.string.home_greeting_afternoon
            in 17..20 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        }
    }
}
