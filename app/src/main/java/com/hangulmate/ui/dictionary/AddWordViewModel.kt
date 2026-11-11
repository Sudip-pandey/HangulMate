package com.hangulmate.ui.dictionary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hangulmate.domain.model.EpsImportance
import com.hangulmate.domain.model.PartOfSpeech
import com.hangulmate.domain.model.Topic
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.repository.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddWordUiState(
    val korean: String = "",
    val nepali: String = "",
    val english: String = "",
    val partOfSpeech: PartOfSpeech = PartOfSpeech.NOUN,
    val topic: Topic = Topic.GENERAL,
    val koreanError: Boolean = false,
    val saving: Boolean = false,
)

/**
 * Add-a-word form state. Korean is the only required field; the rest
 * default honestly (no invented romanization or examples — they stay
 * empty until the user fills them in). The saved word opens in the
 * word detail screen via [savedWordId].
 */
@HiltViewModel
class AddWordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vocabularyRepository: VocabularyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddWordUiState(
            korean = savedStateHandle.get<String>("prefillKorean").orEmpty(),
        ),
    )
    val uiState: StateFlow<AddWordUiState> = _uiState.asStateFlow()

    /** Id of the saved word, set once the insert succeeds. */
    private val _savedWordId = MutableStateFlow<String?>(null)
    val savedWordId: StateFlow<String?> = _savedWordId.asStateFlow()

    fun setKorean(value: String) {
        _uiState.update { it.copy(korean = value, koreanError = false) }
    }

    fun setNepali(value: String) {
        _uiState.update { it.copy(nepali = value) }
    }

    fun setEnglish(value: String) {
        _uiState.update { it.copy(english = value) }
    }

    fun setPartOfSpeech(value: PartOfSpeech) {
        _uiState.update { it.copy(partOfSpeech = value) }
    }

    fun setTopic(value: Topic) {
        _uiState.update { it.copy(topic = value) }
    }

    /** Validates (Korean must be non-empty) and saves via [VocabularyRepository.insertUserWord]. */
    fun save() {
        val state = _uiState.value
        if (state.saving || _savedWordId.value != null) return
        val korean = state.korean.trim()
        if (korean.isEmpty()) {
            _uiState.update { it.copy(koreanError = true) }
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = vocabularyRepository.insertUserWord(
                VocabularyWord(
                    id = "",
                    korean = korean,
                    nepali = state.nepali.trim(),
                    english = state.english.trim(),
                    romanization = "",
                    syllables = emptyList(),
                    partOfSpeech = state.partOfSpeech,
                    topic = state.topic,
                    difficulty = 1,
                    epsImportance = EpsImportance.MEDIUM,
                    exampleKo = "",
                    exampleNe = "",
                    exampleEn = "",
                    tags = emptyList(),
                ),
            )
            _savedWordId.value = id
            _uiState.update { it.copy(saving = false) }
        }
    }
}
