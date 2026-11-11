package com.hangulmate.data.repository

import com.hangulmate.data.local.LearningProgressEntity
import com.hangulmate.domain.model.LearningState

/**
 * SM-2-lite spaced-repetition schedule (see docs/DATA_MODEL.md).
 *
 * Correct recall advances through the fixed interval ladder
 * 1 → 3 → 7 → 15 → 30 days by consecutive-repetition count.
 * A wrong answer resets the interval to 1 day, resets repetitions to 0
 * and marks the word DIFFICULT. The easiness factor is stored for
 * future tuning but does not drive the interval in the -lite schedule.
 */
object SrsScheduler {

    /** Interval in days after the [repetitions]-th consecutive correct recall. */
    fun intervalForRepetitions(repetitions: Int): Int = when {
        repetitions <= 1 -> 1
        repetitions == 2 -> 3
        repetitions == 3 -> 7
        repetitions == 4 -> 15
        else -> 30
    }

    const val MAX_INTERVAL_DAYS = 30
    const val MASTERED_REPETITIONS = 5
    const val INITIAL_EASINESS = 2.5f
    const val MIN_EASINESS = 1.3f
    const val MAX_EASINESS = 2.5f

    /** Result of applying one review answer to a progress row. */
    fun applyReview(
        current: LearningProgressEntity?,
        wordId: String,
        remembered: Boolean,
        todayEpochDay: Long
    ): LearningProgressEntity {
        val base = current ?: LearningProgressEntity.newWord(wordId, todayEpochDay)
        return if (remembered) {
            val repetitions = base.repetitions + 1
            val interval = intervalForRepetitions(repetitions)
            base.copy(
                state = if (repetitions >= MASTERED_REPETITIONS) {
                    LearningState.MASTERED.name
                } else {
                    LearningState.KNOWN.name
                },
                easiness = (base.easiness + 0.1f).coerceAtMost(MAX_EASINESS),
                intervalDays = interval,
                repetitions = repetitions,
                nextReviewEpochDay = todayEpochDay + interval,
                lastReviewedEpochDay = todayEpochDay,
                correctCount = base.correctCount + 1
            )
        } else {
            base.copy(
                state = LearningState.DIFFICULT.name,
                easiness = (base.easiness - 0.2f).coerceAtLeast(MIN_EASINESS),
                intervalDays = 1,
                repetitions = 0,
                nextReviewEpochDay = todayEpochDay + 1,
                lastReviewedEpochDay = todayEpochDay,
                wrongCount = base.wrongCount + 1
            )
        }
    }

    /**
     * Applies a direct state change (e.g. user taps "mark as difficult").
     * DIFFICULT always forces the next review to tomorrow, even when the
     * word was previously scheduled further out. MASTERED keeps the
     * longest interval; other states keep the current schedule.
     */
    fun applyStateChange(
        current: LearningProgressEntity?,
        wordId: String,
        state: LearningState,
        todayEpochDay: Long
    ): LearningProgressEntity {
        val base = current ?: LearningProgressEntity.newWord(wordId, todayEpochDay)
        return when (state) {
            LearningState.DIFFICULT -> base.copy(
                state = LearningState.DIFFICULT.name,
                intervalDays = 1,
                nextReviewEpochDay = todayEpochDay + 1
            )
            LearningState.MASTERED -> base.copy(
                state = LearningState.MASTERED.name,
                intervalDays = MAX_INTERVAL_DAYS,
                repetitions = maxOf(base.repetitions, MASTERED_REPETITIONS),
                nextReviewEpochDay = todayEpochDay + MAX_INTERVAL_DAYS
            )
            LearningState.NEW -> base.copy(state = LearningState.NEW.name)
            else -> base.copy(state = state.name)
        }
    }
}
