package com.hangulmate.domain.repository

import com.hangulmate.domain.model.ImportReport
import com.hangulmate.domain.model.LearningState
import com.hangulmate.domain.model.Topic
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.model.WordRelation
import kotlinx.coroutines.flow.Flow

/**
 * Contract for all vocabulary data access. Feature agents code against
 * this interface only — never against DAOs directly.
 *
 * Conventions:
 * - All [Flow] results are cold and re-emit when the underlying tables
 *   change (Room observable queries).
 * - A blank [search] query returns an empty list (never the whole table).
 * - [getWordOfTheDay] is deterministic: the same [dateEpochDay] always
 *   returns the same word. [dateEpochDay] is days since 1970-01-01 in
 *   the device time zone (see [com.hangulmate.data.local.DateUtils]).
 * - Relations are embedded in the [VocabularyWord] returned by [getById]
 *   (and by [getByKorean]); there is no separate getRelated call.
 * - [recordReview] applies the SM-2-lite schedule (see docs/DATA_MODEL.md).
 */
interface VocabularyRepository {

    /**
     * Three-way search across Korean, Nepali, English and romanization.
     * Partial matching (SQL LIKE) on all four fields; results are ordered
     * exact-match first, then EPS importance (high → medium → low).
     * A blank query returns an empty list.
     */
    fun search(query: String): Flow<List<VocabularyWord>>

    /** The word with this id, with its relations embedded, or null. */
    fun getById(id: String): Flow<VocabularyWord?>

    /** One-shot lookup by exact Korean spelling (used by TTS / camera scan). */
    suspend fun getByKorean(korean: String): VocabularyWord?

    /**
     * Deterministic word of the day for the given epoch day.
     * Throws [IllegalStateException] when the database is still empty
     * (seed import has not run yet).
     */
    suspend fun getWordOfTheDay(dateEpochDay: Long): VocabularyWord

    /**
     * New words for today's lesson: words the user has not learned yet
     * (not in [learnedIds] and with no learning progress), highest EPS
     * importance first. [learnedIds] is the set of ids already shown or
     * learned, so the caller can page through fresh words.
     */
    fun getTodayNewWords(limit: Int, learnedIds: Set<String>): Flow<List<VocabularyWord>>

    /** All words of a topic, optionally filtered by [difficulty] (1–3) and/or EPS-only. */
    fun getByTopic(topic: Topic, difficulty: Int? = null, epsOnly: Boolean = false): Flow<List<VocabularyWord>>

    /** Words the user has starred, most recently starred first. */
    fun getFavorites(): Flow<List<VocabularyWord>>

    /** Stars or un-stars a word. */
    suspend fun toggleFavorite(id: String)

    /** Words whose learning state is DIFFICULT, most-wrong first. */
    fun getDifficult(): Flow<List<VocabularyWord>>

    /**
     * Words from the search history, most recently searched first.
     * Implemented as a join with the search-history table.
     */
    fun getRecentlySearched(limit: Int): Flow<List<VocabularyWord>>

    /** Records that [wordId] was opened/searched (updates history + recency). */
    suspend fun recordSearch(wordId: String)

    /** Learning state of one word; NEW when no progress row exists yet. */
    fun getLearningState(id: String): Flow<LearningState>

    /** Sets the learning state directly (e.g. "mark as difficult"). */
    suspend fun setLearningState(id: String, state: LearningState)

    /**
     * Words due for review today (SRS next-review day <= [todayEpochDay]),
     * ordered by wrong count (most-wrong first) then next-review day,
     * followed by up to [DAILY_NEW_WORD_GOAL] brand-new words.
     */
    fun getRevisionQueue(todayEpochDay: Long): Flow<List<VocabularyWord>>

    /**
     * Records one review answer. Correct recall advances the SM-2-lite
     * interval (1, 3, 7, 15, 30 days by repetitions); a wrong answer
     * resets the interval to 1 day, bumps the wrong count and marks the
     * word DIFFICULT. Also records today's study activity.
     */
    suspend fun recordReview(id: String, remembered: Boolean)

    /** Number of words with any progress beyond NEW. */
    fun getLearnedCount(): Flow<Int>

    /** Number of words in state MASTERED. */
    fun getMasteredCount(): Flow<Int>

    /** Total number of words in the database (seed + user-created). */
    fun getTotalCount(): Flow<Int>

    /**
     * Inserts a user-created word. Returns the new word's id
     * ("HM-U" + epoch millis when the caller leaves [VocabularyWord.id]
     * blank, otherwise the given id). Relations on the passed word are
     * stored as well; targets are resolved by Korean spelling and skipped
     * when the target word does not exist.
     */
    suspend fun insertUserWord(word: VocabularyWord): String

    /** Exports all words plus learning progress as one JSON string (backup format). */
    suspend fun exportAll(): String

    /** Imports JSON produced by [exportAll]; returns counts of added / skipped / errored entries. */
    suspend fun importJson(json: String): ImportReport

    /**
     * Clears ALL learning progress in one transaction: learning-progress
     * rows, favorites, search history, quiz results, daily activity
     * (which resets the streak) and user collections. Vocabulary words —
     * seed words AND user-created words — are NEVER deleted by this call.
     */
    suspend fun clearAllProgress()

    companion object {
        /** New words appended to the revision queue each day. */
        const val DAILY_NEW_WORD_GOAL = 10
    }
}
