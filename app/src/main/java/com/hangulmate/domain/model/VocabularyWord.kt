package com.hangulmate.domain.model

/**
 * A vocabulary word as the rest of the app sees it.
 *
 * [isFavorite] and [learningState] are per-user state joined in by the
 * repository; [similar], [opposite] and [confusing] are the resolved
 * relations embedded by [com.hangulmate.domain.repository.VocabularyRepository.getById]
 * (see docs/DATA_MODEL.md). [isUserCreated] is true for words the user
 * added themselves or imported; seed words are false.
 */
data class VocabularyWord(
    val id: String,
    val korean: String,
    val nepali: String,
    /**
     * Nepali translation state: "verified" (real Nepali), "mt_review"
     * (machine-translated, needs review), or "missing" (no Nepali yet —
     * [nepali] holds the English gloss as a display fallback).
     */
    val nepaliStatus: String = "verified",
    val english: String,
    val romanization: String,
    val syllables: List<String>,
    val partOfSpeech: PartOfSpeech,
    val topic: Topic,
    val difficulty: Int,
    val epsImportance: EpsImportance,
    val exampleKo: String,
    val exampleNe: String,
    val exampleEn: String,
    val tags: List<String>,
    val similar: List<WordRelation> = emptyList(),
    val opposite: List<WordRelation> = emptyList(),
    val confusing: List<WordRelation> = emptyList(),
    val isFavorite: Boolean = false,
    val learningState: LearningState = LearningState.NEW,
    val isUserCreated: Boolean = false
)
