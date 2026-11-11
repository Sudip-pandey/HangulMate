package com.hangulmate.domain.model

/**
 * SRS learning state of a vocabulary word for the current user.
 *
 * NEW: never studied. LEARNING: seen at least once, not yet known.
 * KNOWN: recalled correctly at least once. DIFFICULT: answered wrong
 * (or explicitly marked) — reviewed again tomorrow. MASTERED: recalled
 * correctly 5 times in a row (repetitions >= 5 in the SM-2-lite schedule).
 */
enum class LearningState {
    NEW,
    LEARNING,
    KNOWN,
    DIFFICULT,
    MASTERED
}
