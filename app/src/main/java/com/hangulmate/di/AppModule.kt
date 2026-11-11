package com.hangulmate.di

import android.content.Context
import com.hangulmate.data.preferences.SettingsRepository
import com.hangulmate.data.preferences.SettingsRepositoryImpl
import com.hangulmate.speech.TtsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Foundation-level bindings only.
 *
 * VocabularyRepository / LearningRepository bindings are owned by the
 * data-layer agent and must NOT be added here.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
    ): SettingsRepository = SettingsRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideTtsManager(
        @ApplicationContext context: Context,
    ): TtsManager = TtsManager(context)
}
