package com.hangulmate.di

import android.content.Context
import com.hangulmate.data.preferences.EncryptedGeminiKeyStore
import com.hangulmate.domain.repository.GeminiKeyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Gemini online-identification bindings only, kept in its own module
 * so the existing [AppModule] / [DatabaseModule] stay untouched.
 *
 * The API key lives in EncryptedSharedPreferences (see
 * [EncryptedGeminiKeyStore]) — never in plain DataStore, never in the
 * APK. [GeminiVisionClient] carries an `@Inject` constructor, so Hilt
 * builds it directly; only the key-store binding needs a provider
 * here (its implementation needs an application [Context]).
 */
@Module
@InstallIn(SingletonComponent::class)
object GeminiModule {

    @Provides
    @Singleton
    fun provideGeminiKeyStore(
        @ApplicationContext context: Context,
    ): GeminiKeyStore = EncryptedGeminiKeyStore(context)
}
