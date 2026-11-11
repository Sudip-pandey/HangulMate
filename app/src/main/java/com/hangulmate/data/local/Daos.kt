package com.hangulmate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Vocabulary
// ---------------------------------------------------------------------------

@Dao
interface VocabularyDao {

    /**
     * Three-way search. [pattern] must already be a LIKE pattern
     * (e.g. "%안녕%") with LIKE wildcards escaped by the caller.
     * The translations table is genuinely searched (meaning LIKE),
     * and exact matches sort first, then EPS importance.
     */
    @Query(
        """
        SELECT DISTINCT v.* FROM vocabulary v
        LEFT JOIN vocabulary_translations t ON t.vocabId = v.id
        WHERE v.korean LIKE :pattern ESCAPE '\'
           OR v.nepali LIKE :pattern ESCAPE '\'
           OR v.english LIKE :pattern ESCAPE '\'
           OR v.romanization LIKE :pattern ESCAPE '\'
           OR t.meaning LIKE :pattern ESCAPE '\'
        ORDER BY
          CASE WHEN v.korean = :exact OR v.nepali = :exact
                    OR v.english = :exact OR v.romanization = :exact
               THEN 0 ELSE 1 END,
          CASE v.epsImportance WHEN 'high' THEN 0 WHEN 'medium' THEN 1 ELSE 2 END,
          v.korean ASC
        """
    )
    fun search(pattern: String, exact: String): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    fun getByIdFlow(id: String): Flow<VocabularyEntity?>

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    suspend fun getById(id: String): VocabularyEntity?

    @Query("SELECT * FROM vocabulary WHERE korean = :korean LIMIT 1")
    suspend fun getByKorean(korean: String): VocabularyEntity?

    @Query("SELECT * FROM vocabulary WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<VocabularyEntity>

    @Query("SELECT COUNT(*) FROM vocabulary")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocabulary")
    suspend fun getTotalCountOnce(): Int

    /** Deterministic word of the day: offset into id order. */
    @Query("SELECT * FROM vocabulary ORDER BY id ASC LIMIT 1 OFFSET :offset")
    suspend fun getByOffset(offset: Int): VocabularyEntity?

    @Query(
        """
        SELECT * FROM vocabulary
        WHERE topic = :topic
          AND (:difficulty IS NULL OR difficulty = :difficulty)
          AND (:epsOnly = 0 OR epsImportance = 'high')
        ORDER BY
          CASE epsImportance WHEN 'high' THEN 0 WHEN 'medium' THEN 1 ELSE 2 END,
          difficulty ASC, korean ASC
        """
    )
    fun getByTopic(topic: String, difficulty: Int?, epsOnly: Boolean): Flow<List<VocabularyEntity>>

    /**
     * New words: no learning-progress row and not in [learnedIds].
     * [excludedCount] is 0 when [learnedIds] is empty, which makes the
     * NOT IN clause a no-op (Room rejects an empty IN list otherwise).
     */
    @Query(
        """
        SELECT v.* FROM vocabulary v
        LEFT JOIN learning_progress p ON p.vocabId = v.id
        WHERE p.vocabId IS NULL
          AND (:excludedCount = 0 OR v.id NOT IN (:learnedIds))
        ORDER BY
          CASE v.epsImportance WHEN 'high' THEN 0 WHEN 'medium' THEN 1 ELSE 2 END,
          v.difficulty ASC, v.id ASC
        LIMIT :limit
        """
    )
    fun getNewWords(limit: Int, learnedIds: List<String>, excludedCount: Int): Flow<List<VocabularyEntity>>

    /** Same as [getNewWords] but with no exclusion set (avoids an empty IN list). */
    @Query(
        """
        SELECT v.* FROM vocabulary v
        LEFT JOIN learning_progress p ON p.vocabId = v.id
        WHERE p.vocabId IS NULL
        ORDER BY
          CASE v.epsImportance WHEN 'high' THEN 0 WHEN 'medium' THEN 1 ELSE 2 END,
          v.difficulty ASC, v.id ASC
        LIMIT :limit
        """
    )
    fun getNewWordsWithoutExclusions(limit: Int): Flow<List<VocabularyEntity>>

    /** Words due for SRS review on or before [todayEpochDay]. */
    @Query(
        """
        SELECT v.* FROM vocabulary v
        INNER JOIN learning_progress p ON p.vocabId = v.id
        WHERE p.nextReviewEpochDay <= :todayEpochDay
          AND p.state != 'MASTERED'
        ORDER BY p.wrongCount DESC, p.nextReviewEpochDay ASC
        """
    )
    fun getDueForReview(todayEpochDay: Long): Flow<List<VocabularyEntity>>

    /** Words the user starred, most recently starred first. */
    @Query(
        """
        SELECT v.* FROM vocabulary v
        INNER JOIN favorite_words f ON f.vocabId = v.id
        ORDER BY f.addedEpoch DESC
        """
    )
    fun getFavorites(): Flow<List<VocabularyEntity>>

    /** DIFFICULT words, most-wrong first. */
    @Query(
        """
        SELECT v.* FROM vocabulary v
        INNER JOIN learning_progress p ON p.vocabId = v.id
        WHERE p.state = 'DIFFICULT'
        ORDER BY p.wrongCount DESC, v.korean ASC
        """
    )
    fun getDifficult(): Flow<List<VocabularyEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<VocabularyEntity>): List<Long>

    /** Returns -1 for a row that already existed (IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: VocabularyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(word: VocabularyEntity)

    @Query("SELECT id FROM vocabulary")
    suspend fun getAllIds(): List<String>

    @Query("SELECT korean, id FROM vocabulary")
    suspend fun getKoreanToIdMap(): List<KoreanIdRow>

    /** All rows, in id order (used by export). */
    @Query("SELECT * FROM vocabulary ORDER BY id ASC")
    suspend fun getAll(): List<VocabularyEntity>

    @Query("SELECT * FROM vocabulary WHERE korean LIKE :pattern ESCAPE '\' LIMIT :limit")
    suspend fun findByKoreanPrefix(pattern: String, limit: Int): List<VocabularyEntity>
}

/** Minimal row for building the korean -> id lookup at import time. */
data class KoreanIdRow(val korean: String, val id: String)

@Dao
interface RelationDao {
    @Query("SELECT * FROM vocabulary_relations WHERE sourceId = :sourceId")
    fun getForSource(sourceId: String): Flow<List<VocabularyRelationEntity>>

    @Query("SELECT * FROM vocabulary_relations WHERE sourceId = :sourceId")
    suspend fun getForSourceOnce(sourceId: String): List<VocabularyRelationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(relations: List<VocabularyRelationEntity>)

    @Query("DELETE FROM vocabulary_relations WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: String)

    @Query("SELECT COUNT(*) FROM vocabulary_relations")
    suspend fun count(): Int
}

@Dao
interface ExampleDao {
    @Insert
    suspend fun insertAll(examples: List<VocabularyExampleEntity>)

    @Query("DELETE FROM vocabulary_examples WHERE vocabId = :vocabId")
    suspend fun deleteForVocab(vocabId: String)

    @Query("SELECT * FROM vocabulary_examples WHERE vocabId = :vocabId")
    suspend fun getForWord(vocabId: String): List<VocabularyExampleEntity>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<VocabularyCategoryEntity>)

    @Query("SELECT * FROM vocabulary_categories ORDER BY topic ASC")
    fun getAll(): Flow<List<VocabularyCategoryEntity>>

    @Query("SELECT * FROM vocabulary_categories WHERE topic = :topic")
    suspend fun getByTopic(topic: String): VocabularyCategoryEntity?
}

@Dao
interface TranslationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(translations: List<VocabularyTranslationEntity>)

    @Query("SELECT * FROM vocabulary_translations WHERE vocabId = :vocabId")
    suspend fun getForWord(vocabId: String): List<VocabularyTranslationEntity>
}

// ---------------------------------------------------------------------------
// Progress / favorites / history / quizzes / activity / collections
// ---------------------------------------------------------------------------

@Dao
interface ProgressDao {
    @Query("SELECT * FROM learning_progress WHERE vocabId = :vocabId")
    fun getProgressFlow(vocabId: String): Flow<LearningProgressEntity?>

    @Query("SELECT * FROM learning_progress WHERE vocabId = :vocabId")
    suspend fun getProgress(vocabId: String): LearningProgressEntity?

    @Query("SELECT * FROM learning_progress")
    fun getAllFlow(): Flow<List<LearningProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: LearningProgressEntity)

    @Update
    suspend fun update(progress: LearningProgressEntity)

    @Query("SELECT COUNT(*) FROM learning_progress WHERE state != 'NEW'")
    fun getLearnedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM learning_progress WHERE state = 'MASTERED'")
    fun getMasteredCount(): Flow<Int>

    @Query("SELECT * FROM learning_progress ORDER BY nextReviewEpochDay ASC")
    suspend fun getAll(): List<LearningProgressEntity>

    /** Deletes all learning progress (used by Reset Progress; vocabulary rows stay). */
    @Query("DELETE FROM learning_progress")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_words WHERE vocabId = :vocabId")
    suspend fun get(vocabId: String): FavoriteWordEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteWordEntity)

    @Query("DELETE FROM favorite_words WHERE vocabId = :vocabId")
    suspend fun delete(vocabId: String)

    @Query("SELECT vocabId FROM favorite_words")
    suspend fun getAllIds(): List<String>

    @Query("SELECT vocabId FROM favorite_words")
    fun getAllIdsFlow(): Flow<List<String>>

    /** Reactive "is this word starred" for single-word screens. */
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_words WHERE vocabId = :vocabId)")
    fun isFavorite(vocabId: String): Flow<Boolean>

    /** Deletes all favorites (used by Reset Progress). */
    @Query("DELETE FROM favorite_words")
    suspend fun clearAll()
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("SELECT vocabId FROM search_history ORDER BY searchedEpoch DESC LIMIT :limit")
    fun getRecentIds(limit: Int): Flow<List<String>>

    @Query(
        """
        SELECT v.* FROM vocabulary v
        INNER JOIN search_history h ON h.vocabId = v.id
        ORDER BY h.searchedEpoch DESC LIMIT :limit
        """
    )
    fun getRecentlySearched(limit: Int): Flow<List<VocabularyEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun count(): Int
}

@Dao
interface QuizDao {
    @Insert
    suspend fun insert(result: QuizResultEntity): Long

    @Query("SELECT * FROM quiz_results ORDER BY epochMillis DESC LIMIT :limit")
    fun getHistory(limit: Int): Flow<List<QuizResultEntity>>

    /** Latest quiz score (0-100) on a given epoch day, or null. */
    @Query(
        """
        SELECT (score * 100 / total) FROM quiz_results
        WHERE epochMillis >= :dayStartMillis AND epochMillis < :dayStartMillis + 86400000
          AND total > 0
        ORDER BY epochMillis DESC LIMIT 1
        """
    )
    suspend fun getLatestQuizScoreForDay(dayStartMillis: Long): Int?

    @Query("SELECT * FROM quiz_results ORDER BY epochMillis DESC")
    suspend fun getAll(): List<QuizResultEntity>

    /** Total number of completed quiz runs. */
    @Query("SELECT COUNT(*) FROM quiz_results")
    fun getCount(): Flow<Int>

    /** Deletes all quiz results (used by Reset Progress). */
    @Query("DELETE FROM quiz_results")
    suspend fun clearAll()
}

@Dao
interface DailyActivityDao {
    @Query("SELECT * FROM daily_activity WHERE dateEpochDay = :day")
    fun getForDay(day: Long): Flow<DailyActivityEntity?>

    @Query("SELECT * FROM daily_activity WHERE dateEpochDay = :day")
    suspend fun getForDayOnce(day: Long): DailyActivityEntity?

    /** All activity days, newest first (used to compute the streak). */
    @Query("SELECT dateEpochDay FROM daily_activity ORDER BY dateEpochDay DESC")
    suspend fun getActivityDaysDesc(): List<Long>

    /** Reactive activity days; any row counts as a study day (a quiz-only
     * day is stored as a 0/0 row by LearningRepositoryImpl). */
    @Query("SELECT dateEpochDay FROM daily_activity ORDER BY dateEpochDay DESC")
    fun getActivityDaysFlow(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activity: DailyActivityEntity)

    @Transaction
    suspend fun addActivity(day: Long, wordsLearned: Int, wordsReviewed: Int) {
        val existing = getForDayOnce(day)
        if (existing == null) {
            upsert(DailyActivityEntity(day, wordsLearned, wordsReviewed))
        } else {
            upsert(
                existing.copy(
                    wordsLearned = existing.wordsLearned + wordsLearned,
                    wordsReviewed = existing.wordsReviewed + wordsReviewed
                )
            )
        }
    }

    /** Deletes all daily activity (used by Reset Progress; also resets the streak). */
    @Query("DELETE FROM daily_activity")
    suspend fun clearAll()
}

@Dao
interface CollectionDao {
    @Insert
    suspend fun insertCollection(collection: UserCollectionEntity): Long

    @Query("SELECT * FROM user_collections ORDER BY createdEpoch DESC")
    fun getCollections(): Flow<List<UserCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addWord(entry: CollectionWordEntity)

    @Query("DELETE FROM collection_words WHERE collectionId = :collectionId AND vocabId = :vocabId")
    suspend fun removeWord(collectionId: Long, vocabId: String)

    @Query(
        """
        SELECT v.* FROM vocabulary v
        INNER JOIN collection_words cw ON cw.vocabId = v.id
        WHERE cw.collectionId = :collectionId
        ORDER BY v.korean ASC
        """
    )
    fun getWordsInCollection(collectionId: Long): Flow<List<VocabularyEntity>>

    @Query("DELETE FROM user_collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: Long)

    @Query("UPDATE user_collections SET name = :name WHERE id = :id")
    suspend fun renameCollection(id: Long, name: String)

    /** Number of words in one collection (reactive). */
    @Query("SELECT COUNT(*) FROM collection_words WHERE collectionId = :collectionId")
    fun getWordCount(collectionId: Long): Flow<Int>

    /**
     * Deletes all collections. The foreign key on collection_words
     * cascades, so the word links go too; vocabulary rows stay.
     */
    @Query("DELETE FROM user_collections")
    suspend fun clearAll()
}
