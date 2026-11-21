package com.hangulmate.di

import android.content.Context
import com.hangulmate.data.repository.GrammarRepositoryImpl
import com.hangulmate.domain.repository.GrammarRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Grammar bindings only, kept in its own module so the existing
 * [AppModule] / [DatabaseModule] stay untouched.
 */
@Module
@InstallIn(SingletonComponent::class)
object GrammarModule {

    @Provides
    @Singleton
    fun provideGrammarRepository(
        @ApplicationContext context: Context,
    ): GrammarRepository = GrammarRepositoryImpl(context)
}
