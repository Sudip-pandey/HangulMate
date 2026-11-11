package com.hangulmate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * HangulMate Room database, version 1.
 *
 * Schema export is ON: generated schemas live under `app/schemas/`
 * (the Gradle module must set `room.schemaLocation` — see
 * docs/DATA_MODEL.md, "Migration policy"). Migrations are versioned and
 * additive; `fallbackToDestructiveMigration` is NEVER used for this
 * database, because it would wipe the user's learning progress.
 */
@Database(
    entities = [
        VocabularyEntity::class,
        VocabularyTranslationEntity::class,
        VocabularyRelationEntity::class,
        VocabularyExampleEntity::class,
        VocabularyCategoryEntity::class,
        VocabularyImageEntity::class,
        LearningProgressEntity::class,
        FavoriteWordEntity::class,
        SearchHistoryEntity::class,
        QuizResultEntity::class,
        DailyActivityEntity::class,
        UserCollectionEntity::class,
        CollectionWordEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HangulMateDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun relationDao(): RelationDao
    abstract fun exampleDao(): ExampleDao
    abstract fun categoryDao(): CategoryDao
    abstract fun translationDao(): TranslationDao
    abstract fun progressDao(): ProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun quizDao(): QuizDao
    abstract fun dailyActivityDao(): DailyActivityDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        const val DATABASE_NAME = "hangulmate.db"
    }
}
