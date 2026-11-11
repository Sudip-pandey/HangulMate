package com.hangulmate.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hangulmate.domain.model.LearningState

/**
 * Normalized Room entities for HangulMate (database version 1).
 *
 * Design notes (see docs/DATA_MODEL.md for the full ER description):
 * - Nepali and English meanings live BOTH as columns on [VocabularyEntity]
 *   (fast display) AND as rows in [VocabularyTranslationEntity] (the
 *   normalized table, genuinely used by search).
 * - Example sentences live in [VocabularyExampleEntity], not only as
 *   columns, so more examples can be added later without a migration.
 * - Every foreign key cascades on delete so removing a word never leaves
 *   orphan rows.
 */

// ---------------------------------------------------------------------------
// Vocabulary
// ---------------------------------------------------------------------------

@Entity(
    tableName = "vocabulary",
    indices = [Index(value = ["korean"], unique = true)]
)
data class VocabularyEntity(
    @PrimaryKey val id: String,
    val korean: String,
    /** Nepali meaning (Devanagari). Also mirrored in [VocabularyTranslationEntity]. */
    val nepali: String,
    /** English meaning. Also mirrored in [VocabularyTranslationEntity]. */
    val english: String,
    val romanization: String,
    /** JSON array of single-syllable strings, e.g. ["안","녕","하","세","요"]. */
    val syllablesJson: String,
    /** Lowercase key: noun | verb | adjective | adverb | expression | number | pronoun | particle | interjection. */
    val partOfSpeech: String,
    /** Lowercase topic key, one of the 14 values in [com.hangulmate.domain.model.Topic]. */
    val topic: String,
    /** 1 = beginner, 2 = intermediate, 3 = advanced. */
    val difficulty: Int,
    /** high | medium | low. */
    val epsImportance: String,
    val exampleKo: String,
    val exampleNe: String,
    val exampleEn: String,
    /** JSON array of tag strings. */
    val tagsJson: String,
    /** True for user-created / imported words; false for seed words. */
    val isUserCreated: Boolean = false,
    /**
     * Nepali translation status (seed v2): "verified" (human-checked),
     * "mt_review" (machine-translated, awaiting review) or "missing"
     * (no Nepali yet -- [nepali] then holds the English gloss as a
     * display fallback, and no "ne" translation row is stored).
     */
    val nepaliStatus: String = "verified",
    /** JSON array of senses, e.g. [{"pos":"noun","definitions":[...]}]. */
    val sensesJson: String = "[]"
)

@Entity(
    tableName = "vocabulary_translations",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocabId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vocabId"]),
        Index(value = ["vocabId", "language"], unique = true)
    ]
)
data class VocabularyTranslationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vocabId: String,
    /** "ne" (Nepali) or "en" (English). */
    val language: String,
    val meaning: String
)

@Entity(
    tableName = "vocabulary_relations",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sourceId"]), Index(value = ["targetId"])]
)
data class VocabularyRelationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceId: String,
    /** Null when the target word could not be resolved at import (the
     * relation row is then skipped by the importer, so a stored null
     * should not normally occur — the column stays nullable per spec). */
    val targetId: String?,
    /** Korean spelling of the target, kept for display even if the target is later removed. */
    val targetKorean: String,
    /** SIMILAR | OPPOSITE | CONFUSING (stored as the enum name). */
    val kind: String,
    /** Nepali note; for CONFUSING relations this is the seed `differenceNe`. */
    val noteNe: String
)

@Entity(
    tableName = "vocabulary_examples",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocabId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vocabId"])]
)
data class VocabularyExampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vocabId: String,
    val ko: String,
    val ne: String,
    val en: String
)

@Entity(tableName = "vocabulary_categories")
data class VocabularyCategoryEntity(
    /** Topic key, e.g. "daily" — the primary key. */
    @PrimaryKey val topic: String,
    val nameNe: String,
    val nameEn: String,
    val iconName: String
)

@Entity(
    tableName = "vocabulary_images",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocabId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vocabId"])]
)
data class VocabularyImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vocabId: String,
    /** Asset path, e.g. "images/words/HM-0001.jpg". */
    val assetPath: String,
    val caption: String
)

// ---------------------------------------------------------------------------
// Learning progress, favorites, history, quizzes
// ---------------------------------------------------------------------------

@Entity(tableName = "learning_progress")
data class LearningProgressEntity(
    @PrimaryKey val vocabId: String,
    /** LearningState enum name. */
    val state: String,
    /** SM-2 easiness factor; starts at 2.5, bounded [1.3, 2.5]. */
    val easiness: Float = 2.5f,
    /** Current review interval in days (SM-2-lite: 1, 3, 7, 15, 30). */
    val intervalDays: Int = 0,
    /** Consecutive correct recalls; resets to 0 on a wrong answer. */
    val repetitions: Int = 0,
    /** Epoch day the word is next due for review. */
    val nextReviewEpochDay: Long,
    /** Epoch day of the last review, or null if never reviewed. */
    val lastReviewedEpochDay: Long? = null,
    val wrongCount: Int = 0,
    val correctCount: Int = 0
) {
    companion object {
        fun newWord(id: String, todayEpochDay: Long): LearningProgressEntity =
            LearningProgressEntity(
                vocabId = id,
                state = LearningState.NEW.name,
                nextReviewEpochDay = todayEpochDay
            )
    }
}

@Entity(tableName = "favorite_words")
data class FavoriteWordEntity(
    @PrimaryKey val vocabId: String,
    /** Epoch millis when the word was starred. */
    val addedEpoch: Long
)

@Entity(
    tableName = "search_history",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocabId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vocabId"], unique = true)]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vocabId: String,
    /** Epoch millis of the (latest) search. One row per word; re-searching updates the timestamp. */
    val searchedEpoch: Long
)

@Entity(tableName = "quiz_results")
data class QuizResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Quiz mode, e.g. "multiple_choice". */
    val type: String,
    val score: Int,
    val total: Int,
    /** Epoch millis when the quiz finished. */
    val epochMillis: Long,
    /** JSON array of wrongly answered word ids. */
    val wrongWordIdsJson: String
)

/** One row per calendar day with study activity; drives the streak. */
@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    /** Epoch day — the primary key. */
    @PrimaryKey val dateEpochDay: Long,
    val wordsLearned: Int = 0,
    val wordsReviewed: Int = 0
)

// ---------------------------------------------------------------------------
// User collections
// ---------------------------------------------------------------------------

@Entity(tableName = "user_collections")
data class UserCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** Epoch millis when the collection was created. */
    val createdEpoch: Long
)

@Entity(
    tableName = "collection_words",
    primaryKeys = ["collectionId", "vocabId"],
    foreignKeys = [
        ForeignKey(
            entity = UserCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocabId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vocabId"])]
)
data class CollectionWordEntity(
    val collectionId: Long,
    val vocabId: String
)
