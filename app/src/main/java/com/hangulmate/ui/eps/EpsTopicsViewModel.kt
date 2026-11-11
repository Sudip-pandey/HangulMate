package com.hangulmate.ui.eps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hangulmate.domain.model.EpsImportance
import com.hangulmate.domain.model.Topic
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.repository.VocabularyRepository
import com.hangulmate.speech.TtsManager
import com.hangulmate.speech.TtsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of one EPS topic flashcard study session. */
sealed interface StudyUiState {
    data object Idle : StudyUiState
    data object Loading : StudyUiState

    /** The selected topic has no words matching the current filters. */
    data class Empty(val topic: Topic) : StudyUiState

    data class Active(
        val topic: Topic,
        val words: List<VocabularyWord>,
        val index: Int,
        val revealed: Boolean,
    ) : StudyUiState {
        val current: VocabularyWord? get() = words.getOrNull(index)
        val isFinished: Boolean get() = index >= words.size
    }
}

/**
 * Owns the EPS topics list (live per-topic counts) and the flashcard study
 * sessions run from it. Filters (difficulty, EPS importance) are applied
 * to the study session; the topic list itself always shows total counts.
 *
 * Study answers feed the SRS through the repository only:
 * "I know" → recordReview(true); "I don't know" → recordReview(false).
 */
@HiltViewModel
class EpsTopicsViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val ttsManager: TtsManager,
) : ViewModel() {

    val ttsState: StateFlow<TtsState> = ttsManager.state

    private val _topicCounts = MutableStateFlow<Map<Topic, Int>>(emptyMap())
    val topicCounts: StateFlow<Map<Topic, Int>> = _topicCounts.asStateFlow()

    private val _difficultyFilter = MutableStateFlow<Int?>(null)
    val difficultyFilter: StateFlow<Int?> = _difficultyFilter.asStateFlow()

    private val _importanceFilter = MutableStateFlow<EpsImportance?>(null)
    val importanceFilter: StateFlow<EpsImportance?> = _importanceFilter.asStateFlow()

    private val _studyState = MutableStateFlow<StudyUiState>(StudyUiState.Idle)
    val studyState: StateFlow<StudyUiState> = _studyState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(Topic.entries.map { vocabularyRepository.getByTopic(it) }) { arrays ->
                Topic.entries.zip(arrays.map { it.size }).toMap()
            }.collect { counts ->
                _topicCounts.value = counts
            }
        }
    }

    fun setDifficultyFilter(difficulty: Int?) {
        _difficultyFilter.value = difficulty
    }

    fun setImportanceFilter(importance: EpsImportance?) {
        _importanceFilter.value = importance
    }

    /** Starts a flashcard run through [topic], filtered by the current filters. */
    fun startStudy(topic: Topic) {
        _studyState.value = StudyUiState.Loading
        viewModelScope.launch {
            val words = vocabularyRepository
                .getByTopic(topic, difficulty = _difficultyFilter.value)
                .first()
                .filter { word ->
                    _importanceFilter.value == null || word.epsImportance == _importanceFilter.value
                }
            _studyState.value = if (words.isEmpty()) {
                StudyUiState.Empty(topic)
            } else {
                StudyUiState.Active(topic = topic, words = words, index = 0, revealed = false)
            }
        }
    }

    /** Returns from a study session to the topic list. */
    fun exitStudy() {
        _studyState.value = StudyUiState.Idle
    }

    /** Flips the current flashcard between front and back. */
    fun toggleReveal() {
        val state = _studyState.value as? StudyUiState.Active ?: return
        _studyState.value = state.copy(revealed = !state.revealed)
    }

    /** "I know": correct recall, then advance to the next card. */
    fun markKnown() {
        val state = _studyState.value as? StudyUiState.Active ?: return
        val word = state.current ?: return
        viewModelScope.launch {
            vocabularyRepository.recordReview(word.id, true)
            val current = _studyState.value as? StudyUiState.Active ?: return@launch
            _studyState.value = current.copy(index = current.index + 1, revealed = false)
        }
    }

    /** "I don't know": failed recall (SRS marks it DIFFICULT), then advance. */
    fun markUnknown() {
        val state = _studyState.value as? StudyUiState.Active ?: return
        val word = state.current ?: return
        viewModelScope.launch {
            vocabularyRepository.recordReview(word.id, false)
            val current = _studyState.value as? StudyUiState.Active ?: return@launch
            _studyState.value = current.copy(index = current.index + 1, revealed = false)
        }
    }

    /** Speaks [text] when the Korean voice is ready; otherwise a no-op. */
    fun speak(text: String) {
        ttsManager.speak(text)
    }
}
