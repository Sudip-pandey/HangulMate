package com.hangulmate.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hangulmate.domain.repository.GeminiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [GeminiKeyStore] backed by EncryptedSharedPreferences (Tink):
 * MasterKey AES256_GCM, key encryption AES256_SIV, value encryption
 * AES256_GCM. The key never lives in plain storage, and this class
 * never logs it.
 *
 * The implementation itself is NOT unit-tested (it needs Android
 * framework classes); the interface is covered by a fake in tests.
 */
class EncryptedGeminiKeyStore(context: Context) : GeminiKeyStore {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun getKey(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    override suspend fun hasKey(): Boolean = getKey() != null

    override suspend fun saveKey(key: String) = withContext(Dispatchers.IO) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_API_KEY).apply()
        } else {
            prefs.edit().putString(KEY_API_KEY, trimmed).apply()
        }
    }

    override suspend fun clearKey() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    companion object {
        private const val PREFS_NAME = "hangulmate_gemini_key"
        private const val KEY_API_KEY = "gemini_api_key"
    }
}
