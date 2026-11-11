package com.hangulmate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * HangulMate Room database, version 2.
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
    version = 2,
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

        /**
         * v1 -> v2 (seed v2, NIKL dataset): two new columns on
         * `vocabulary`, both backfilled with the v1-equivalent values so
         * existing rows stay valid. No table is dropped or rebuilt, and
         * no user data (progress, favorites, collections) is touched.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE vocabulary ADD COLUMN nepaliStatus TEXT NOT NULL DEFAULT 'verified'"
                )
                db.execSQL(
                    "ALTER TABLE vocabulary ADD COLUMN sensesJson TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }
    }
}
