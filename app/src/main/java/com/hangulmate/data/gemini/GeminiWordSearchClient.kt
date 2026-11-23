package com.hangulmate.data.gemini

import com.hangulmate.ml.GeminiWordSearchResult

/**
 * Looks a word up with Gemini (online dictionary fallback). The
 * implementation ([GeminiVisionClient]) makes the real HTTP call; tests
 * substitute a fake through this interface so no network is needed.
 *
 * The caller passes the user's own API key (read from
 * [com.hangulmate.domain.repository.GeminiKeyStore] at call time) —
 * it is never stored or logged here.
 */
interface GeminiWordSearchClient {

    /**
     * Looks up [query] (a Korean word, a Nepali meaning, or an English
     * meaning) and returns the best matching Korean word with its
     * meanings and ALL similar / opposite words, or [GeminiWordSearchResult.NotFound]
     * when nothing matches, or [GeminiWordSearchResult.Failure] when the
     * call itself failed. Never throws.
     */
    suspend fun searchWord(apiKey: String, query: String): GeminiWordSearchResult
}
