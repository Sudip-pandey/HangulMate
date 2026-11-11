package com.hangulmate.domain.model

/**
 * One completed quiz run.
 *
 * [id] is 0 for a new, unsaved result (Room auto-generates it). [type]
 * is the quiz mode, e.g. "multiple_choice", "flashcard" or "listening".
 * [dateEpoch] is epoch milliseconds. [wrongWordIds] lists the word ids
 * the user answered wrongly, so Practice can offer them for revision.
 */
data class QuizResult(
    val id: Long = 0L,
    val type: String,
    val score: Int,
    val total: Int,
    val dateEpoch: Long,
    val wrongWordIds: List<String> = emptyList()
) {
    /** Score as a 0–100 percentage; 0 when [total] is 0. */
    val percentage: Int
        get() = if (total > 0) (score * 100 / total) else 0
}

/**
 * Aggregated study activity for one calendar day.
 *
 * [date] is the epoch day (days since 1970-01-01 in the device time
 * zone, see [com.hangulmate.data.local.DateUtils.epochDay]). [quizScore] is the
 * percentage of the latest quiz taken that day, or 0 when no quiz was
 * taken.
 */
data class DailyStats(
    val date: Long,
    val wordsLearned: Int,
    val wordsReviewed: Int,
    val quizScore: Int
)

/**
 * Result of importing a JSON export (see
 * [com.hangulmate.domain.repository.VocabularyRepository.importJson]).
 * [added] words were inserted, [skipped] already existed (same id),
 * [errors] counts entries that could not be parsed or inserted;
 * [errorMessages] carries the first few human-readable reasons.
 */
data class ImportReport(
    val added: Int,
    val skipped: Int,
    val errors: Int,
    val errorMessages: List<String> = emptyList()
)
