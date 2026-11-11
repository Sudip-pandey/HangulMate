package com.hangulmate.ui.dictionary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hangulmate.domain.model.LearningState
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.repository.VocabularyRepository
import com.hangulmate.speech.TtsManager
import com.hangulmate.speech.TtsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Previous / next word ids within the same topic; null at the ends. */
data class TopicNavigation(
    val previousWordId: String? = null,
    val nextWordId: String? = null,
)

@HiltViewModel
class WordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vocabularyRepository: VocabularyRepository,
    private val ttsManager: TtsManager,
) : ViewModel() {

    private val wordId: String = savedStateHandle.get<String>("wordId").orEmpty()

    /**
     * The word, with its relations embedded (see
     * [VocabularyRepository.getById]). Null when the id is not in the
     * database — rendered as a not-found state, never as fake data.
     */
    val word: StateFlow<VocabularyWord?> = vocabularyRepository.getById(wordId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True once the first database emission has arrived. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    /** Honest state of the Korean TTS voice on this device. */
    val ttsState: StateFlow<TtsState> = ttsManager.state

    /** Previous / next word within the same topic (hidden at the ends). */
    val navigation: StateFlow<TopicNavigation> = word
        .flatMapLatest { current ->
            if (current == null) {
                flowOf(TopicNavigation())
            } else {
                vocabularyRepository.getByTopic(current.topic).map { topicWords ->
                    val index = topicWords.indexOfFirst { it.id == current.id }
                    TopicNavigation(
                        previousWordId = topicWords.getOrNull(index - 1)?.id,
                        nextWordId = topicWords.getOrNull(index + 1)?.id,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TopicNavigation(),
        )

    init {
        viewModelScope.launch {
            // First emission of the raw (cold) database flow marks loading
            // done, whether the word exists or not. Do NOT call first() on
            // the stateIn'd flow — its initial value is null and would
            // mark loading done before the database answers.
            vocabularyRepository.getById(wordId).first()
            _loaded.value = true
        }
    }

    /** Speak Korean text. No-op unless the Korean voice is ready (see [TtsManager]). */
    fun speak(text: String) {
        ttsManager.speak(text)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun toggleFavorite() {
        viewModelScope.launch { vocabularyRepository.toggleFavorite(wordId) }
    }

    fun markLearned() {
        viewModelScope.launch {
            vocabularyRepository.setLearningState(wordId, LearningState.KNOWN)
        }
    }

    fun markDifficult() {
        viewModelScope.launch {
            vocabularyRepository.setLearningState(wordId, LearningState.DIFFICULT)
        }
    }

    /**
     * Records one review answer. `remembered = true` advances the SRS
     * interval; `false` resets it and marks the word difficult.
     */
    fun recordReview(remembered: Boolean) {
        viewModelScope.launch { vocabularyRepository.recordReview(wordId, remembered) }
    }
}
