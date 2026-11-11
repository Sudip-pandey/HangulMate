package com.hangulmate.data.repository

import com.hangulmate.data.local.HangulMateDatabase
import com.hangulmate.data.local.SearchHistoryEntity
import com.hangulmate.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** [SearchHistoryRepository] implementation backed by Room. */
@Singleton
class SearchHistoryRepositoryImpl @Inject constructor(
    private val db: HangulMateDatabase
) : SearchHistoryRepository {

    override suspend fun record(wordId: String) {
        db.historyDao().upsert(
            SearchHistoryEntity(vocabId = wordId, searchedEpoch = System.currentTimeMillis())
        )
    }

    override fun recent(limit: Int): Flow<List<String>> =
        db.historyDao().getRecentIds(limit)

    override suspend fun clear() {
        db.historyDao().clear()
    }
}
