package com.hangulmate.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Contract for the search-history list (recently searched word ids).
 * The word-level join lives on
 * [VocabularyRepository.getRecentlySearched]; this repository only
 * stores and returns ids.
 */
interface SearchHistoryRepository {

    /** Records that [wordId] was searched now (upserts recency). */
    suspend fun record(wordId: String)

    /** Most recently searched word ids, newest first. */
    fun recent(limit: Int): Flow<List<String>>

    /** Clears the whole search history. */
    suspend fun clear()
}
