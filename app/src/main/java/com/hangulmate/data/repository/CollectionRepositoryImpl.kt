package com.hangulmate.data.repository

import com.hangulmate.data.local.CollectionWordEntity
import com.hangulmate.data.local.HangulMateDatabase
import com.hangulmate.data.local.UserCollectionEntity
import com.hangulmate.domain.model.UserCollection
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.repository.CollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** [CollectionRepository] implementation backed by Room. */
@Singleton
class CollectionRepositoryImpl @Inject constructor(
    private val db: HangulMateDatabase
) : CollectionRepository {

    private val collectionDao get() = db.collectionDao()

    override fun getCollections(): Flow<List<UserCollection>> =
        collectionDao.getCollections().flatMapLatest { entities ->
            if (entities.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    entities.map { entity ->
                        collectionDao.getWordCount(entity.id).map { count ->
                            UserCollection(
                                id = entity.id,
                                name = entity.name,
                                createdEpoch = entity.createdEpoch,
                                wordCount = count
                            )
                        }
                    }
                ) { it.toList() }
            }
        }

    override suspend fun createCollection(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Collection name must not be blank" }
        return collectionDao.insertCollection(
            UserCollectionEntity(name = trimmed, createdEpoch = System.currentTimeMillis())
        )
    }

    override suspend fun deleteCollection(id: Long) {
        // The foreign key on collection_words cascades, so the word links
        // go with the collection; the vocabulary rows themselves are untouched.
        collectionDao.deleteCollection(id)
    }

    override suspend fun renameCollection(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        collectionDao.renameCollection(id, trimmed)
    }

    override suspend fun addWord(collectionId: Long, wordId: String) {
        // INSERT IGNORE: adding a word that is already in the collection is a no-op.
        collectionDao.addWord(CollectionWordEntity(collectionId, wordId))
    }

    override suspend fun removeWord(collectionId: Long, wordId: String) {
        collectionDao.removeWord(collectionId, wordId)
    }

    override fun getWords(collectionId: Long): Flow<List<VocabularyWord>> =
        combine(
            collectionDao.getWordsInCollection(collectionId),
            db.favoriteDao().getAllIdsFlow(),
            db.progressDao().getAllFlow()
        ) { entities, favoriteIds, progressList ->
            val favorites = favoriteIds.toSet()
            val progressById = progressList.associateBy { it.vocabId }
            entities.map { entity ->
                entity.toDomain(
                    isFavorite = entity.id in favorites,
                    learningState = progressById[entity.id]?.toLearningState()
                        ?: com.hangulmate.domain.model.LearningState.NEW
                )
            }
        }
}
