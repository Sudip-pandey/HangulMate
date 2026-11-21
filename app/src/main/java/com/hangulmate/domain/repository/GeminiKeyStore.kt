package com.hangulmate.domain.repository

/**
 * The user's OWN Gemini API key, for the optional online object
 * identification feature.
 *
 * Each student uses their own key, entered in Settings. The key is
 * stored encrypted on the device (see
 * [com.hangulmate.data.preferences.EncryptedGeminiKeyStore]) — never
 * in plain DataStore/SharedPreferences, never in the APK, never in a
 * log, and sent only to Google's Gemini API as part of an online
 * identification request the user started. No key = offline TFLite
 * mode still works; this store simply reports nothing saved.
 */
interface GeminiKeyStore {

    /** The saved key, or null when none is saved. */
    suspend fun getKey(): String?

    /** True when a non-blank key is saved. */
    suspend fun hasKey(): Boolean

    /** Saves [key] (trimmed). A blank key clears the saved key. */
    suspend fun saveKey(key: String)

    /** Removes the saved key. */
    suspend fun clearKey()
}
