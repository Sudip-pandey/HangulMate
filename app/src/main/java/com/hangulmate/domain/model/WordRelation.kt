package com.hangulmate.domain.model

/** Kind of relation between two vocabulary words. */
enum class RelationKind {
    SIMILAR,
    OPPOSITE,
    CONFUSING
}

/**
 * A relation from one word to another.
 *
 * [targetWordId] is null only when the target word is not in the database
 * (never the case for seed data — every seed relation resolves — but
 * possible for user-imported words). [noteNe] is the Nepali explanation;
 * [noteEn] is an optional English explanation. For CONFUSING relations
 * the seed JSON field is `differenceNe` and it is stored here as [noteNe].
 */
data class WordRelation(
    val targetWordId: String?,
    val targetKorean: String,
    val kind: RelationKind,
    val noteNe: String,
    val noteEn: String? = null
)
