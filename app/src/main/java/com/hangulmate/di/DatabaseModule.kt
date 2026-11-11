package com.hangulmate.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.hangulmate.data.local.HangulMateDatabase
import com.hangulmate.data.local.SeedImportPrefs
import com.hangulmate.data.local.SeedImporter
import com.hangulmate.data.local.SeedState
import com.hangulmate.data.repository.CollectionRepositoryImpl
import com.hangulmate.data.repository.LearningRepositoryImpl
import com.hangulmate.data.repository.SearchHistoryRepositoryImpl
import com.hangulmate.data.repository.VocabularyRepositoryImpl
import com.hangulmate.domain.repository.CollectionRepository
import com.hangulmate.domain.repository.LearningRepository
import com.hangulmate.domain.repository.SearchHistoryRepository
import com.hangulmate.domain.repository.VocabularyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifier for the application-wide coroutine scope. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Runs the one-time seed import on first app launch.
 *
 * Hilt creates singletons lazily, so this class only starts when something
 * injects it: the Application class (foundation-owned) MUST inject
 * [SeedOnFirstLaunch] in `onCreate` (e.g. `@Inject lateinit var
 * seedOnFirstLaunch: SeedOnFirstLaunch` + a no-op access) so the import
 * starts at app start. The import itself is guarded by the
 * "seed_imported_v2" flag and is safe to run twice.
 *
 * UI layers observe [state]: Idle → Importing → Success | Failed.
 * On [SeedState.Failed] the app keeps working with whatever data exists
 * (possibly empty) and retries the import on the next launch, because the
 * guard flag is only set after a successful import.
 */
@Singleton
class SeedOnFirstLaunch @javax.inject.Inject constructor(
    private val seedImporter: SeedImporter,
    @ApplicationScope private val scope: CoroutineScope
) {
    /** Live state of the seed import (see [SeedImporter.state]). */
    val state: StateFlow<SeedState> = seedImporter.state

    init {
        scope.launch { seedImporter.importIfNeeded() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HangulMateDatabase =
        Room.databaseBuilder(context, HangulMateDatabase::class.java, HangulMateDatabase.DATABASE_NAME)
            // Deliberately NO fallbackToDestructiveMigration(): wiping the
            // database would destroy the user's learning progress. Schema
            // changes ship as versioned Migrations (see docs/DATA_MODEL.md).
            .addMigrations(HangulMateDatabase.MIGRATION_1_2)
            .build()

    @Provides
    @Singleton
    fun provideSeedImportPrefs(@ApplicationContext context: Context): SeedImportPrefs {
        val prefs: SharedPreferences =
            context.getSharedPreferences(SeedImporter.PREFS_NAME, Context.MODE_PRIVATE)
        return object : SeedImportPrefs {
            override fun isImported(): Boolean =
                prefs.getBoolean(SeedImporter.KEY_SEED_IMPORTED_V2, false)

            override fun markImported() {
                prefs.edit().putBoolean(SeedImporter.KEY_SEED_IMPORTED_V2, true).apply()
            }
        }
    }

    @Provides
    @Singleton
    fun provideSeedImporter(
        database: HangulMateDatabase,
        @ApplicationContext context: Context,
        prefs: SeedImportPrefs
    ): SeedImporter = SeedImporter(
        database = database,
        // Missing asset -> null -> SeedState.Failed (never a crash).
        seedJsonProvider = {
            runCatching { context.assets.open("seed/vocabulary_seed.json") }.getOrNull()
        },
        prefs = prefs
    )

    // -- DAOs ----------------------------------------------------------

    @Provides
    fun provideVocabularyDao(database: HangulMateDatabase) = database.vocabularyDao()

    @Provides
    fun provideRelationDao(database: HangulMateDatabase) = database.relationDao()

    @Provides
    fun provideExampleDao(database: HangulMateDatabase) = database.exampleDao()

    @Provides
    fun provideCategoryDao(database: HangulMateDatabase) = database.categoryDao()

    @Provides
    fun provideTranslationDao(database: HangulMateDatabase) = database.translationDao()

    @Provides
    fun provideProgressDao(database: HangulMateDatabase) = database.progressDao()

    @Provides
    fun provideFavoriteDao(database: HangulMateDatabase) = database.favoriteDao()

    @Provides
    fun provideHistoryDao(database: HangulMateDatabase) = database.historyDao()

    @Provides
    fun provideQuizDao(database: HangulMateDatabase) = database.quizDao()

    @Provides
    fun provideDailyActivityDao(database: HangulmateDatabase) = database.dailyActivityDao()

    @Provides
    fun provideCollectionDao(database: HangulMateDatabase) = database.collectionDao()

    // -- Repositories --------------------------------------------------

    @Provides
    @Singleton
    fun provideVocabularyRepository(
        database: HangulMateDatabase
    ): VocabularyRepository = VocabularyRepositoryImpl(database)

    @Provides
    @Singleton
    fun provideLearningRepository(
        database: HangulMateDatabase
    ): LearningRepository = LearningRepositoryImpl(database)

    @Provides
    @Singleton
    fun provideSearchHistoryRepository(
        database: HangulMateDatabase
    ): SearchHistoryRepository = SearchHistoryRepositoryImpl(database)

    @Provides
    @Singleton
    fun provideCollectionRepository(
        database: HangulMateDatabase
    ): CollectionRepository = CollectionRepositoryImpl(database)
}
