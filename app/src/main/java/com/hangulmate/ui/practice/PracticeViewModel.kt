package com.hangulmate.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hangulmate.data.local.DateUtils
import com.hangulmate.domain.model.LearningState
import com.hangulmate.domain.model.QuizResult
import com.hangulmate.domain.model.Topic
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.practice.QuizGenerator
import com.hangulmate.domain.practice.QuizQuestion
import com.hangulmate.domain.practice.QuizType
import com.hangulmate.domain.repository.LearningRepository
import com.hangulmate.domain.repository.VocabularyRepository
import com.hangulmate.speech.TtsManager
import com.hangulmate.speech.TtsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The quiz configuration used to start (and retry) a quiz run. */
data class QuizConfig(
    val type: QuizType,
    val topic: Topic? = null,
    val count: Int = QuizGenerator.DEFAULT_QUESTION_COUNT,
    val timed: Boolean = false,
)

/** State of the quiz flow, shared by QuizScreen and QuizResultScreen. */
sealed interface QuizUiState {
    data object Idle : QuizUiState
    data object Loading : QuizUiState

    /** The pool could not support a quiz (fewer than 4 usable words/questions). */
    data class NotEnoughWords(val available: Int) : QuizUiState

    data class Active(
        val config: QuizConfig,
        val questions: List<QuizQuestion>,
        val currentIndex: Int,
        val answers: Map<Int, Int>,
        /** Seconds left for the whole quiz; null when the quiz is untimed. */
        val remainingSeconds: Int?,
    ) : QuizUiState {
        val currentQuestion: QuizQuestion? get() = questions.getOrNull(currentIndex)
        val isLastQuestion: Boolean get() = currentIndex >= questions.lastIndex
    }

    data class Finished(
        val config: QuizConfig,
        val questions: List<QuizQuestion>,
        val answers: Map<Int, Int>,
        val result: QuizResult,
        val markedDifficult: Set<String> = emptySet(),
    ) : QuizUiState {
        /** Questions answered wrongly (unanswered counts as wrong). */
        val wrongQuestions: List<QuizQuestion>
            get() = questions.filterIndexed { index, question ->
                answers[index] != question.correctIndex
            }
    }
}

/** One of the three review answers on a revision flashcard. */
enum class RevisionAction {
    /** "I know this" — correct recall, advance the SRS interval. */
    KNOW,

    /** "I need practice" — correct recall, but keep the state LEARNING. */
    NEED_PRACTICE,

    /** "I forgot this" — wrong recall: DIFFICULT, re-queued in this session. */
    FORGOT,
}

/** State of today's SRS revision session. */
sealed interface RevisionUiState {
    data object Loading : RevisionUiState
    data object Empty : RevisionUiState
    data class Active(
        val queue: List<VocabularyWord>,
        val index: Int,
        val reviewed: Int,
    ) : RevisionUiState {
        val current: VocabularyWord? get() = queue.getOrNull(index)
        val isFinished: Boolean get() = index >= queue.size
    }
}

/**
 * Owns the quiz engine and today's SRS revision session for the Practice tab.
 *
 * Quiz questions are built by [QuizGenerator] from local words only.
 * The countdown timer lives here (in the ViewModel) so it survives
 * rotation; the ticker job is cancelled whenever a quiz ends or a new one
 * starts, and the ViewModel scope cancels it on dispose. SRS itself lives
 * in the repository — this ViewModel only calls recordReview /
 * setLearningState with the answers the user gives.
 */
@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val learningRepository: LearningRepository,
    private val ttsManager: TtsManager,
) : ViewModel() {

    val ttsState: StateFlow<TtsState> = ttsManager.state

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _revisionState = MutableStateFlow<RevisionUiState>(RevisionUiState.Loading)
    val revisionState: StateFlow<RevisionUiState> = _revisionState.asStateFlow()

    /** Most recent quiz results, newest first — for the quiz-history screen. */
    val quizHistory: StateFlow<List<QuizResult>> =
        learningRepository.getQuizHistory(QUIZ_HISTORY_LIMIT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _quizState = MutableStateFlow<QuizUiState>(QuizUiState.Idle)
    val quizState: StateFlow<QuizUiState> = _quizState.asStateFlow()

    private var timerJob: Job? = null
    private var lastQuizConfig: QuizConfig? = null

    /** Words already re-queued once in this revision session (never re-queued twice). */
    private val requeuedIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            _totalCount.value = vocabularyRepository.getTotalCount().first()
        }
        refreshRevisionQueue()
    }

    // ------------------------------------------------------------------
    // Revision queue
    // ------------------------------------------------------------------

    /** (Re)loads today's revision queue from the repository, in its own order. */
    fun refreshRevisionQueue() {
        viewModelScope.launch {
            val queue = vocabularyRepository.getRevisionQueue(DateUtils.today()).first()
            requeuedIds.clear()
            _revisionState.value = if (queue.isEmpty()) {
                RevisionUiState.Empty
            } else {
                RevisionUiState.Active(queue = queue, index = 0, reviewed = 0)
            }
        }
    }

    /** Records one flashcard answer and advances to the next card. */
    fun reviewCurrent(action: RevisionAction) {
        val state = _revisionState.value as? RevisionUiState.Active ?: return
        val word = state.current ?: return
        viewModelScope.launch {
            when (action) {
                RevisionAction.KNOW -> {
                    vocabularyRepository.recordReview(word.id, true)
                }
                RevisionAction.NEED_PRACTICE -> {
                    vocabularyRepository.recordReview(word.id, true)
                    vocabularyRepository.setLearningState(word.id, LearningState.LEARNING)
                }
                RevisionAction.FORGOT -> {
                    vocabularyRepository.recordReview(word.id, false)
                }
            }
            val newQueue = state.queue.toMutableList()
            if (action == RevisionAction.FORGOT && word.id !in requeuedIds) {
                requeuedIds += word.id
                newQueue.add(word)
            }
            _revisionState.value = state.copy(
                queue = newQueue,
                index = state.index + 1,
                reviewed = state.reviewed + 1,
            )
        }
    }

    // ------------------------------------------------------------------
    // Quiz
    // ------------------------------------------------------------------

    /** Starts a quiz of [type] over [topic] (null = all topics). */
    fun startQuiz(
        type: QuizType,
        topic: Topic? = null,
        count: Int = QuizGenerator.DEFAULT_QUESTION_COUNT,
        timed: Boolean = false,
    ) {
        if (_quizState.value is QuizUiState.Loading) return
        _quizState.value = QuizUiState.Loading
        cancelTimer()
        val config = QuizConfig(type = type, topic = topic, count = count, timed = timed)
        lastQuizConfig = config
        viewModelScope.launch {
            val pool = loadPool(topic)
            if (pool.size < QuizGenerator.MIN_POOL_SIZE) {
                _quizState.value = QuizUiState.NotEnoughWords(pool.size)
                return@launch
            }
            val listeningAvailable = ttsManager.isKoreanAvailable()
            // Relation questions need relations, which list queries do not
            // embed — enrich a random subset via getById for those types.
            val targets = when (type) {
                QuizType.RELATION -> enrichCandidates(pool, limit = 60, requireRelations = true)
                QuizType.EPS_MOCK -> enrichCandidates(pool, limit = 60, requireRelations = false)
                else -> pool
            }
            val questions = QuizGenerator.generate(
                type = type,
                targets = targets,
                distractorPool = pool,
                count = count,
                listeningAvailable = listeningAvailable,
            )
            if (questions.size < QuizGenerator.MIN_POOL_SIZE) {
                val available = if (type == QuizType.RELATION) targets.size else pool.size
                _quizState.value = QuizUiState.NotEnoughWords(available)
                return@launch
            }
            val totalSeconds = if (timed) {
                questions.size * QuizGenerator.EPS_SECONDS_PER_QUESTION
            } else {
                null
            }
            _quizState.value = QuizUiState.Active(
                config = config,
                questions = questions,
                currentIndex = 0,
                answers = emptyMap(),
                remainingSeconds = totalSeconds,
            )
            if (timed) startTimer()
            speakCurrentQuestionIfListening()
        }
    }

    /** Re-runs the last quiz with a fresh, re-randomised set of questions. */
    fun retryQuiz() {
        val config = lastQuizConfig ?: return
        startQuiz(config.type, config.topic, config.count, config.timed)
    }

    /** Leaves the quiz flow (e.g. back to the hub); stops the timer. */
    fun resetQuiz() {
        cancelTimer()
        _quizState.value = QuizUiState.Idle
    }

    /** Records the selected option for the current question (changeable until Next). */
    fun selectAnswer(optionIndex: Int) {
        val state = _quizState.value as? QuizUiState.Active ?: return
        if (optionIndex !in state.currentQuestion?.options.orEmpty().indices) return
        _quizState.value = state.copy(
            answers = state.answers + (state.currentIndex to optionIndex),
        )
    }

    /** Moves to the next question; finishes the quiz after the last one. */
    fun nextQuestion() {
        val state = _quizState.value as? QuizUiState.Active ?: return
        if (state.currentIndex !in state.answers) return
        if (state.isLastQuestion) {
            finishQuiz()
        } else {
            _quizState.value = state.copy(currentIndex = state.currentIndex + 1)
            speakCurrentQuestionIfListening()
        }
    }

    /** Scores the quiz, persists the result, and moves to the result state. */
    fun finishQuiz() {
        val state = _quizState.value as? QuizUiState.Active ?: return
        cancelTimer()
        viewModelScope.launch {
            val score = state.questions.indices.count { index ->
                state.answers[index] == state.questions[index].correctIndex
            }
            val wrongWordIds = state.questions.indices
                .filter { index -> state.answers[index] != state.questions[index].correctIndex }
                .map { index -> state.questions[index].targetWordId }
            val result = QuizResult(
                type = state.config.type.typeKey,
                score = score,
                total = state.questions.size,
                dateEpoch = System.currentTimeMillis(),
                wrongWordIds = wrongWordIds,
            )
            learningRepository.saveQuizResult(result)
            _quizState.value = QuizUiState.Finished(
                config = state.config,
                questions = state.questions,
                answers = state.answers,
                result = result,
            )
        }
    }

    /**
     * Marks a wrongly-answered word as difficult: records a failed review
     * (which the SRS counts as DIFFICULT) and then sets the state
     * explicitly, per the quiz-result "add to difficult" spec.
     */
    fun addWrongWordToDifficult(wordId: String) {
        val state = _quizState.value as? QuizUiState.Finished ?: return
        if (wordId in state.markedDifficult) return
        viewModelScope.launch {
            vocabularyRepository.recordReview(wordId, false)
            vocabularyRepository.setLearningState(wordId, LearningState.DIFFICULT)
            val current = _quizState.value as? QuizUiState.Finished ?: return@launch
            _quizState.value = current.copy(markedDifficult = current.markedDifficult + wordId)
        }
    }

    // ------------------------------------------------------------------
    // TTS
    // ------------------------------------------------------------------

    /** Speaks [text] when the Korean voice is ready; otherwise a no-op. */
    fun speak(text: String) {
        ttsManager.speak(text)
    }

    /** Speaks the current question when it is a listening question. */
    fun speakCurrentQuestionIfListening() {
        val state = _quizState.value as? QuizUiState.Active ?: return
        val question = state.currentQuestion ?: return
        if (question.type == QuizType.LISTENING) {
            question.ttsText?.let { ttsManager.speak(it) }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private suspend fun loadPool(topic: Topic?): List<VocabularyWord> {
        return if (topic != null) {
            vocabularyRepository.getByTopic(topic).first()
        } else {
            combine(Topic.entries.map { vocabularyRepository.getByTopic(it) }) { arrays ->
                arrays.flatMap { it }
            }.first()
        }
    }

    /**
     * Enriches a random subset of [pool] with relations (via getById, the
     * only list-source that embeds them). When [requireRelations] is true,
     * only words that actually have a similar/opposite relation are kept.
     */
    private suspend fun enrichCandidates(
        pool: List<VocabularyWord>,
        limit: Int,
        requireRelations: Boolean,
    ): List<VocabularyWord> {
        val candidates = pool.shuffled().take(limit)
        val enriched = candidates.mapNotNull { word ->
            runCatching { vocabularyRepository.getById(word.id).first() }.getOrNull()
        }
        return if (requireRelations) {
            enriched.filter { it.similar.isNotEmpty() || it.opposite.isNotEmpty() }
        } else {
            enriched
        }
    }

    /** Starts the whole-quiz countdown; time-up finishes the quiz. */
    private fun startTimer() {
        cancelTimer()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val state = _quizState.value as? QuizUiState.Active ?: break
                val remaining = (state.remainingSeconds ?: break) - 1
                if (remaining <= 0) {
                    finishQuiz()
                    break
                }
                _quizState.value = state.copy(remainingSeconds = remaining)
            }
        }
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        cancelTimer()
        ttsManager.stop()
        super.onCleared()
    }

    companion object {
        /** Quiz results kept for the history screen (newest first). */
        private const val QUIZ_HISTORY_LIMIT = 50
    }
}
