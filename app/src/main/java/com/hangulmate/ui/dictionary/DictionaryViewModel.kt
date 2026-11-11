package com.hangulmate.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hangulmate.domain.model.Topic
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.repository.SearchHistoryRepository
import com.hangulmate.domain.repository.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the Dictionary screen needs, in one snapshot. */
data class DictionaryUiState(
    val query: String = "",
    val results: List<VocabularyWord> = emptyList(),
    /** Top 5 matches, shown as live suggestions while typing. */
    val suggestions: List<VocabularyWord> = emptyList(),
    val favoritesOnly: Boolean = false,
    val selectedTopic: Topic? = null,
    val recentWords: List<VocabularyWord> = emptyList(),
    val totalCount: Int = 0,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _favoritesOnly = MutableStateFlow(false)
    private val _selectedTopic = MutableStateFlow<Topic?>(null)

    /** True once the initial query (from navigation) has been applied; guards rotation. */
    private var initialQueryApplied = false

    /**
     * Applies the query the screen was opened with (from the `query` nav
     * argument). Runs once only, so rotating the device never wipes what
     * the user has typed since.
     */
    fun applyInitialQuery(initialQuery: String) {
        if (initialQueryApplied) return
        initialQueryApplied = true
        if (initialQuery.isNotBlank()) {
            _query.value = initialQuery
        }
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun setFavoritesOnly(enabled: Boolean) {
        _favoritesOnly.value = enabled
    }

    fun selectTopic(topic: Topic?) {
        _selectedTopic.value = topic
    }

    /** Re-runs the search for a word from the history chips. */
    fun searchAgain(word: VocabularyWord) {
        _query.value = word.korean
    }

    fun clearHistory() {
        viewModelScope.launch { searchHistoryRepository.clear() }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { vocabularyRepository.toggleFavorite(id) }
    }

    /** Records that a word was opened (updates history + recency). */
    fun onWordOpened(wordId: String) {
        viewModelScope.launch { vocabularyRepository.recordSearch(wordId) }
    }

    // 300ms debounce: the repository's LIKE search runs only once the user pauses.
    private val debouncedQuery: StateFlow<String> = _query
        .debounce(300)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /**
     * Full word list (all topics) — the capped candidate set for the
     * typo-tolerant second pass. A few hundred words at most, so the
     * pass stays cheap.
     */
    private val allWords: StateFlow<List<VocabularyWord>> = combine(
        Topic.entries.map { vocabularyRepository.getByTopic(it) },
    ) { arrays -> arrays.flatMap { it } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class SearchInputs(
        val query: String,
        val favoritesOnly: Boolean,
        val topic: Topic?,
        val allWords: List<VocabularyWord>,
    )

    /**
     * The [uiState] combine's first five inputs. `combine` only has
     * typed overloads up to 5 flows (kotlinx-coroutines 1.8.1), so the
     * 6-flow state is built as a 5-flow combine plus the topic flow.
     */
    private data class DictionaryInputs(
        val query: String,
        val results: List<VocabularyWord>,
        val recentWords: List<VocabularyWord>,
        val totalCount: Int,
        val favoritesOnly: Boolean,
    )

    private val results: Flow<List<VocabularyWord>> =
        combine(
            debouncedQuery,
            _favoritesOnly,
            _selectedTopic,
            allWords,
        ) { query, favoritesOnly, topic, allWords ->
            SearchInputs(query, favoritesOnly, topic, allWords)
        }.flatMapLatest { inputs ->
            when {
                // 3-way search (Korean / Nepali / English / romanization) —
                // LIKE matching lives in the repository; only filter here.
                // When LIKE finds little, the typo-tolerant second pass
                // (choseong + edit distance ≤ 2) runs over allWords.
                inputs.query.isNotBlank() -> vocabularyRepository.search(inputs.query).map { list ->
                    val filtered = list.filter {
                        (!inputs.favoritesOnly || it.isFavorite) &&
                            (inputs.topic == null || it.topic == inputs.topic)
                    }
                    if (filtered.size >= FUZZY_MIN_RESULTS) {
                        filtered
                    } else {
                        filtered + fuzzyMatches(
                            query = inputs.query,
                            candidates = inputs.allWords,
                            alreadyHave = filtered.map { it.id }.toSet(),
                        ).filter {
                            (!inputs.favoritesOnly || it.isFavorite) &&
                                (inputs.topic == null || it.topic == inputs.topic)
                        }
                    }
                }
                inputs.favoritesOnly -> vocabularyRepository.getFavorites().map { list ->
                    list.filter { inputs.topic == null || it.topic == inputs.topic }
                }
                inputs.topic != null -> vocabularyRepository.getByTopic(inputs.topic)
                // No query, no filter: no results (never the whole table).
                else -> flowOf(emptyList())
            }
        }

    val uiState: StateFlow<DictionaryUiState> = combine(
        combine(
            _query,
            results,
            vocabularyRepository.getRecentlySearched(limit = 8),
            vocabularyRepository.getTotalCount(),
            _favoritesOnly,
        ) { query, results, recentWords, totalCount, favoritesOnly ->
            DictionaryInputs(query, results, recentWords, totalCount, favoritesOnly)
        },
        _selectedTopic,
    ) { inputs, selectedTopic ->
        DictionaryUiState(
            query = inputs.query,
            results = inputs.results,
            suggestions = if (inputs.query.isBlank()) emptyList() else inputs.results.take(5),
            favoritesOnly = inputs.favoritesOnly,
            selectedTopic = selectedTopic,
            recentWords = inputs.recentWords,
            totalCount = inputs.totalCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DictionaryUiState(),
    )

    companion object {
        /** LIKE results at or above this count skip the fuzzy second pass. */
        private const val FUZZY_MIN_RESULTS = 5
    }
}
