package com.hangulmate.domain.model

/**
 * A user-created word collection (a named folder of words).
 *
 * [wordCount] is the number of words currently in the collection; it is
 * joined in by the repository and is 0 for a fresh collection.
 * [createdEpoch] is epoch millis at creation time.
 */
data class UserCollection(
    val id: Long,
    val name: String,
    val createdEpoch: Long,
    val wordCount: Int = 0
)
