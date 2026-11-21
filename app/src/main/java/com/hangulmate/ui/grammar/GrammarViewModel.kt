package com.hangulmate.ui.grammar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hangulmate.domain.model.GrammarPoint
import com.hangulmate.domain.repository.GrammarLoadState
import com.hangulmate.domain.repository.GrammarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the grammar list: bundled points, Korean / Nepali / English
 * search, and the Book 1 / Book 2 filter.
 *
 * Search matches the form, the Nepali and English meanings, and the unit
 * titles — the same 3-way search idea as the Dictionary, over the
 * in-memory list (120 rows, no database involved).
 */
@HiltViewModel
class GrammarViewModel @Inject constructor(
    private val grammarRepository: GrammarRepository,
) : ViewModel() {

    val loadState: StateFlow<GrammarLoadState> = grammarRepository.state

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Null = both books; 1 or 2 = one book only. */
    private val _bookFilter = MutableStateFlow<Int?>(null)
    val bookFilter: StateFlow<Int?> = _bookFilter.asStateFlow()

    val filteredPoints: StateFlow<List<GrammarPoint>> = combine(
        loadState,
        query,
        bookFilter,
    ) { state, queryText, book ->
        val points = (state as? GrammarLoadState.Ready)?.points.orEmpty()
        val byBook = if (book == null) points else points.filter { it.book == book }
        val trimmed = queryText.trim()
        if (trimmed.isEmpty()) {
            byBook
        } else {
            byBook.filter { point ->
                point.form.contains(trimmed, ignoreCase = true) ||
                    point.meaningNe.contains(trimmed, ignoreCase = true) ||
                    point.meaningEn.contains(trimmed, ignoreCase = true) ||
                    point.unitTitle.contains(trimmed, ignoreCase = true) ||
                    point.unitTitleEn.contains(trimmed, ignoreCase = true)
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    init {
        viewModelScope.launch { grammarRepository.loadIfNeeded() }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun setBookFilter(book: Int?) {
        _bookFilter.value = book
    }

    fun getPoint(id: String): GrammarPoint? = grammarRepository.getById(id)
}
