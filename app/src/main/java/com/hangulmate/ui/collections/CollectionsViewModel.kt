package com.hangulmate.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hangulmate.domain.model.UserCollection
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
) : ViewModel() {

    val collections: StateFlow<List<UserCollection>> =
        collectionRepository.getCollections()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _expandedId = MutableStateFlow<Long?>(null)
    val expandedId: StateFlow<Long?> = _expandedId

    /** Words of the currently expanded collection (empty when none expanded). */
    val expandedWords: StateFlow<List<VocabularyWord>> =
        _expandedId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else collectionRepository.getWords(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleExpanded(id: Long) {
        _expandedId.value = if (_expandedId.value == id) null else id
    }

    /** Creates a collection; blank names are rejected (the UI disables Save, this is the backstop). */
    fun createCollection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { collectionRepository.createCollection(trimmed) }
    }

    fun renameCollection(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { collectionRepository.renameCollection(id, trimmed) }
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch {
            collectionRepository.deleteCollection(id)
            if (_expandedId.value == id) _expandedId.value = null
        }
    }

    fun addWord(collectionId: Long, wordId: String) {
        viewModelScope.launch { collectionRepository.addWord(collectionId, wordId) }
    }

    /** Removes a word from the collection only — the vocabulary entry itself stays. */
    fun removeWord(collectionId: Long, wordId: String) {
        viewModelScope.launch { collectionRepository.removeWord(collectionId, wordId) }
    }
}
