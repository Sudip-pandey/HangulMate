package com.hangulmate.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hangulmate.domain.model.Topic
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.repository.OnlineSearchResult
import com.hangulmate.domain.repository.SearchHistoryRepository
import com.hangulmate.domain.repository.VocabularyRepository
import com.hangulmate.ml.GeminiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the Gemini online word search (dictionary fallback). */
sealed interface OnlineSearchState {
    /** Nothing to show (no query, local results exist, or a filter is on). */
    data object Idle : OnlineSearchState

    /** Waiting on Gemini for the current query. */
    data object Searching : OnlineSearchState

    /** Gemini found the word — it is saved locally now. */
    data class Found(val word: VocabularyWord, val fromCache: Boolean = false) : OnlineSearchState

    /** Neither the seed nor Gemini has this word. */
    data object NotFound : OnlineSearchState

    /** No Gemini key saved, so no online lookup was attempted. */
    data object NoKey : OnlineSearchState

    /** The online lookup failed (invalid key, quota, no internet, …). */
    data class Error(val error: GeminiError) : OnlineSearchState
}

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
    /** Gemini online-search state for the current query (Idle when local results exist). */
    val onlineState: OnlineSearchState = OnlineSearchState.Idle,
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

    /** Gemini online-search state for the current query. */
    private val _onlineState = MutableStateFlow<OnlineSearchState>(OnlineSearchState.Idle)

    /**
     * The last query an online lookup was attempted for. Guards
     * against re-calling Gemini for the same query when the local
     * results re-emit (e.g. a favorite toggle); cleared when the
     * query changes or local results appear.
     */
    private var lastOnlineQuery: String? = null

    private fun mapOnlineResult(result: OnlineSearchResult): OnlineSearchState =
        when (result) {
            is OnlineSearchResult.Found -> OnlineSearchState.Found(result.word, result.fromCache)
            OnlineSearchResult.NoKey -> OnlineSearchState.NoKey
            OnlineSearchResult.NotFound -> OnlineSearchState.NotFound
            is OnlineSearchResult.Failed -> OnlineSearchState.Error(result.error)
        }

    /** Re-runs the online lookup for the current query (e.g. after an error). */
    fun retryOnlineSearch() {
        val query = debouncedQuery.value
        if (query.isBlank()) return
        // Mark it attempted first, so the trigger collector above
        // does not fire a second lookup for the same query.
        lastOnlineQuery = query
        viewModelScope.launch {
            _onlineState.value = OnlineSearchState.Searching
            _onlineState.value = runCatching {
                vocabularyRepository.searchOnline(query)
            }.fold(
                onSuccess = ::mapOnlineResult,
                onFailure = { OnlineSearchState.Error(GeminiError.UNKNOWN) },
            )
        }
    }

    private data class OnlineTrigger(
        val query: String,
        val favoritesOnly: Boolean,
        val topic: Topic?,
        val resultsEmpty: Boolean,
        /** True when a local result matches the query exactly (see hasExactMatch). */
        val hasExactMatch: Boolean,
    )

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
     * state is built as a 5-flow combine plus the topic and online
     * flows on the outside.
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
                    if (filtered.size >= FUZZY_MIN_RESULTS ||
                        // Devanagari input: the fuzzy pass only compares
                        // Korean / romanization / English, so it can
                        // never match — skip it.
                        detectSearchLanguage(inputs.query) == SearchLanguage.NEPALI
                    ) {
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

    // NOTE: this init block must stay AFTER `debouncedQuery` and
    // `results` above — init blocks run in declaration order, and the
    // collector subscribes to both flows.
    init {
        viewModelScope.launch {
            // Online fallback: when the debounced query has NO local
            // results (and no favorites/topic filter is narrowing the
            // list), ask Gemini once for that query. A Nepali / English
            // meaning with no EXACT local match also gets a lookup —
            // partial LIKE matches must not hide the translation the
            // user asked for (see detectSearchLanguage / hasExactMatch).
            // collectLatest cancels an in-flight lookup when the query
            // changes, so fast typing never fires a lookup per keystroke.
            combine(
                debouncedQuery,
                results,
                _favoritesOnly,
                _selectedTopic,
            ) { query, results, favoritesOnly, topic ->
                OnlineTrigger(
                    query = query,
                    favoritesOnly = favoritesOnly,
                    topic = topic,
                    resultsEmpty = results.isEmpty(),
                    hasExactMatch = hasExactMatch(results, query),
                )
            }.collectLatest { trigger ->
                // A non-Korean query needs the online lookup when no
                // local word matches it exactly; a Korean query only
                // when the local list is empty.
                val needsOnline = trigger.resultsEmpty ||
                    (detectSearchLanguage(trigger.query) != SearchLanguage.KOREAN &&
                        !trigger.hasExactMatch)
                if (trigger.query.isBlank() || trigger.favoritesOnly ||
                    trigger.topic != null || !needsOnline
                ) {
                    lastOnlineQuery = null
                    if (_onlineState.value != OnlineSearchState.Idle) {
                        _onlineState.value = OnlineSearchState.Idle
                    }
                    return@collectLatest
                }
                if (trigger.query == lastOnlineQuery) return@collectLatest
                // Settle window: the `results` flow re-queries Room when
                // the debounced query changes, but this combine fires on
                // the query change first — with the PREVIOUS results
                // still showing. Waiting a beat lets Room answer for
                // the new query, so a word that IS local never spends
                // a Gemini call; collectLatest still cancels this block
                // outright when fresh results arrive.
                delay(150)
                lastOnlineQuery = trigger.query
                _onlineState.value = OnlineSearchState.Searching
                _onlineState.value = runCatching {
                    vocabularyRepository.searchOnline(trigger.query)
                }.fold(
                    onSuccess = ::mapOnlineResult,
                    onFailure = { OnlineSearchState.Error(GeminiError.UNKNOWN) },
                )
            }
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
        _onlineState,
    ) { inputs, selectedTopic, onlineState ->
        DictionaryUiState(
            query = inputs.query,
            results = inputs.results,
            suggestions = if (inputs.query.isBlank()) emptyList() else inputs.results.take(5),
            favoritesOnly = inputs.favoritesOnly,
            selectedTopic = selectedTopic,
            recentWords = inputs.recentWords,
            totalCount = inputs.totalCount,
            onlineState = onlineState,
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
