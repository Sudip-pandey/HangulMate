package com.hangulmate.domain.repository

import com.hangulmate.domain.model.UserCollection
import com.hangulmate.domain.model.VocabularyWord
import kotlinx.coroutines.flow.Flow

/**
 * Contract for user-created word collections.
 *
 * Collections group words the user picks (e.g. "EPS verbs", "words I keep
 * mixing up"). A collection never owns its words: deleting a collection
 * (or a word from a collection) never deletes the vocabulary entry itself.
 */
interface CollectionRepository {

    /** All collections, newest first, each with its current word count. */
    fun getCollections(): Flow<List<UserCollection>>

    /** Creates a collection; returns the new collection's id. Blank names are rejected. */
    suspend fun createCollection(name: String): Long

    /** Deletes a collection and its word links. The words themselves stay. */
    suspend fun deleteCollection(id: Long)

    /** Renames a collection. Blank names are ignored. */
    suspend fun renameCollection(id: Long, name: String)

    /** Adds a word to a collection; a no-op when already present. */
    suspend fun addWord(collectionId: Long, wordId: String)

    /** Removes a word from a collection. The word itself stays. */
    suspend fun removeWord(collectionId: Long, wordId: String)

    /** Words in a collection, Korean alphabetical order. */
    fun getWords(collectionId: Long): Flow<List<VocabularyWord>>
}
