package com.hangulmate.domain.model

/** One trilingual example for a grammar point. */
data class GrammarExample(
    val ko: String,
    val ne: String,
    val en: String,
    val source: ExampleSource,
)

/**
 * Where an example comes from. Shown honestly on screen: examples copied
 * from the textbook say so, examples written for this guide say so.
 */
enum class ExampleSource {
    BOOK,
    AUTHORED,
}

/**
 * Whether the past tense (-았/었-) can attach after this grammar point.
 * NOT_STATED means the book is silent — never shown as a rule.
 */
enum class TenseRule {
    YES,
    NO,
    NOT_STATED,
    NA,
}

/** Which person (1st / 2nd / 3rd) this grammar point may be used with. */
enum class PersonRule {
    ALL,
    FIRST_ONLY,
    SECOND_ONLY,
    FIRST_SECOND,
    SECOND_THIRD,
    NOT_STATED,
}

/**
 * One EPS-TOPIK grammar point, exactly as bundled in
 * `assets/seed/grammar_seed.json` (v0.2: 120 points, 60 per book).
 *
 * Book 1 = Standard Korean Textbook Korean 1 (units 1–30, everyday life);
 * Book 2 = Volume 2 (units 31–60, work life). Every display string comes
 * from the seed file — nothing here is invented, and a "not_stated" rule
 * is displayed as the book being silent, never as a made-up restriction.
 */
data class GrammarPoint(
    val id: String,
    val book: Int,
    val unit: Int,
    val unitTitle: String,
    val unitTitleEn: String,
    val form: String,
    val structure: String,
    val meaningKo: String,
    val meaningEn: String,
    val meaningNe: String,
    val tensePast: TenseRule,
    val tenseNote: String,
    val person: PersonRule,
    val personNote: String,
    val examples: List<GrammarExample>,
    val mistakes: String,
    val sourcePage: Int,
)
