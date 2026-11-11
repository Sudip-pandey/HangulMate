package com.hangulmate.domain.model

/** Part of speech, stored as its lowercase seed-JSON value. */
enum class PartOfSpeech(val key: String) {
    NOUN("noun"),
    VERB("verb"),
    ADJECTIVE("adjective"),
    ADVERB("adverb"),
    EXPRESSION("expression"),
    NUMBER("number"),
    PRONOUN("pronoun"),
    PARTICLE("particle"),
    INTERJECTION("interjection");

    companion object {
        fun fromKey(key: String): PartOfSpeech =
            entries.firstOrNull { it.key == key } ?: NOUN
    }
}

/** EPS-TOPIK importance of a word, stored as its lowercase seed-JSON value. */
enum class EpsImportance(val key: String) {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    companion object {
        fun fromKey(key: String): EpsImportance =
            entries.firstOrNull { it.key == key } ?: MEDIUM

        /** Sort rank used by search: high-importance words first. */
        fun rank(value: EpsImportance): Int = when (value) {
            HIGH -> 0
            MEDIUM -> 1
            LOW -> 2
        }
    }
}
