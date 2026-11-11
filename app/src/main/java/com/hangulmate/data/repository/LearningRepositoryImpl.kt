package com.hangulmate.data.repository

import androidx.room.withTransaction
import com.hangulmate.data.local.DateUtils
import com.hangulmate.data.local.HangulMateDatabase
import com.hangulmate.domain.model.DailyStats
import com.hangulmate.domain.model.QuizResult
import com.hangulmate.domain.repository.LearningRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

/** [LearningRepository] implementation backed by Room. */
@Singleton
class LearningRepositoryImpl @Inject constructor(
    private val db: HangulMateDatabase
) : LearningRepository {

    override fun getStreak(): Flow<Int> =
        db.dailyActivityDao().getActivityDaysFlow().map { days ->
            computeStreak(days.toSet(), DateUtils.today())
        }

    override suspend fun recordActivity(dateEpochDay: Long, wordsLearned: Int, wordsReviewed: Int) {
        db.dailyActivityDao().addActivity(dateEpochDay, wordsLearned, wordsReviewed)
    }

    override fun getTodayStats(dateEpochDay: Long): Flow<DailyStats> {
        val dayStartMillis = DateUtils.startOfDayMillis(dateEpochDay)
        return db.dailyActivityDao().getForDay(dateEpochDay).mapLatest { activity ->
            val quizScore = db.quizDao().getLatestQuizScoreForDay(dayStartMillis) ?: 0
            DailyStats(
                date = dateEpochDay,
                wordsLearned = activity?.wordsLearned ?: 0,
                wordsReviewed = activity?.wordsReviewed ?: 0,
                quizScore = quizScore
            )
        }
    }

    override suspend fun saveQuizResult(result: QuizResult) {
        val quizDay = DateUtils.epochDay(result.dateEpoch)
        db.withTransaction {
            db.quizDao().insert(result.toEntity())
            // A quiz-only day still counts as a study day for the streak.
            if (db.dailyActivityDao().getForDayOnce(quizDay) == null) {
                db.dailyActivityDao().upsert(
                    com.hangulmate.data.local.DailyActivityEntity(
                        dateEpochDay = quizDay, wordsLearned = 0, wordsReviewed = 0
                    )
                )
            }
        }
    }

    override fun getQuizHistory(limit: Int): Flow<List<QuizResult>> =
        db.quizDao().getHistory(limit).map { rows -> rows.map { it.toDomain() } }

    override fun getQuizCount(): Flow<Int> = db.quizDao().getCount()

    companion object {
        /**
         * Consecutive study days ending today — or ending yesterday when
         * the user has not studied yet today (the streak survives until a
         * full day passes with no activity).
         */
        fun computeStreak(studyDays: Set<Long>, today: Long): Int {
            var cursor = when {
                today in studyDays -> today
                today - 1 in studyDays -> today - 1
                else -> return 0
            }
            var streak = 0
            while (cursor in studyDays) {
                streak++
                cursor--
            }
            return streak
        }
    }
}
