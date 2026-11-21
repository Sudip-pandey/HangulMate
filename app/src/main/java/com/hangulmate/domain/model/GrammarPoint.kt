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
 * One key usage condition for a grammar point (v0.3 research).
 * Nepali is primary — the app's users are Nepali EPS students — with an
 * English gloss. One key point per entry is the "follows" matrix:
 * which sentence types (question / command / request / suggestion /
 * statement) may follow this form, stated explicitly even when there is
 * no restriction, or that the form has no following clause at all.
 */
data class GrammarKeyPoint(
    val ne: String,
    val en: String,
)

/**
 * One EPS-TOPIK grammar point, exactly as bundled in
 * `assets/seed/grammar_seed.json` (v0.3: 120 points, 60 per book, each
 * with researched key_points).
 *
 * Book 1 = Standard Korean Textbook Korean 1 (units 1–30, everyday life);
 * Book 2 = Volume 2 (units 31–60, work life). Book 3 = KGIU extras
 * (Korean Grammar in Use sections not in the EPS 120, unit = KGIU unit
 * number, 0 = Introduction & Getting Ready). Every display string comes
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
    val keyPoints: List<GrammarKeyPoint> = emptyList(),
)
