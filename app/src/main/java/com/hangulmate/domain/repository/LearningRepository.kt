package com.hangulmate.domain.repository

import com.hangulmate.domain.model.DailyStats
import com.hangulmate.domain.model.QuizResult
import kotlinx.coroutines.flow.Flow

/**
 * Contract for streaks, daily activity and quiz history.
 *
 * A "study day" is any epoch day with at least one recorded activity
 * (words learned/reviewed) or one saved quiz result. The streak counts
 * consecutive study days ending today — or ending yesterday when the user
 * has not studied yet today (the streak is not broken until a full day
 * passes with no activity).
 */
interface LearningRepository {

    /** Consecutive study days up to today (or yesterday if today is empty). */
    fun getStreak(): Flow<Int>

    /** Adds to the activity totals of one epoch day (upserts). */
    suspend fun recordActivity(dateEpochDay: Long, wordsLearned: Int, wordsReviewed: Int)

    /** Aggregated stats for one epoch day; zeroes and quizScore 0 when no data. */
    fun getTodayStats(dateEpochDay: Long): Flow<DailyStats>

    /** Persists one completed quiz run. */
    suspend fun saveQuizResult(result: QuizResult)

    /** Most recent quiz results, newest first. */
    fun getQuizHistory(limit: Int): Flow<List<QuizResult>>

    /** Total number of completed quiz runs (0 when none taken yet). */
    fun getQuizCount(): Flow<Int>
}
