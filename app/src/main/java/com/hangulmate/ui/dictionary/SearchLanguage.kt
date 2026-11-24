package com.hangulmate.ui.dictionary

import com.hangulmate.domain.model.VocabularyWord

/**
 * Which language the user typed into the Dictionary search box, by
 * script — pure logic, unit-testable as plain JUnit.
 *
 * The search box accepts Korean, Nepali and English (see the search
 * hint). The app needs to know which one it got, so that:
 * - a Nepali / English meaning with no exact local match still gets
 *   a Gemini translation (see DictionaryViewModel's online trigger),
 *   instead of stopping at partial LIKE matches;
 * - the typo-tolerant fuzzy pass (choseong + edit distance on Korean /
 *   romanization / English) is skipped for Devanagari input, where it
 *   can never match anything.
 *
 * Roman Nepali ("pani") is Latin script, so it reads as [OTHER] —
 * the same as English. That is fine: both go to Gemini, whose prompt
 * accepts a Nepali meaning in Devanagari or Roman letters (see
 * [com.hangulmate.ml.GeminiSearch.buildPrompt]).
 */
enum class SearchLanguage {
    /** At least one Hangul character (syllable or jamo). */
    KOREAN,

    /** At least one Devanagari character, no Hangul. */
    NEPALI,

    /** Latin letters, digits, or anything else. */
    OTHER,
}

private fun Char.isHangul(): Boolean =
    this in '\uAC00'..'\uD7A3' || // Hangul syllables
        this in '\u1100'..'\u11FF' || // Hangul jamo
        this in '\u3130'..'\u318F' // Hangul compatibility jamo (choseong etc.)

private fun Char.isDevanagari(): Boolean = this in '\u0900'..'\u097F'

/**
 * Detects the input language by script. Hangul wins on mixed input:
 * a query containing any Hangul is a Korean query, even if it also
 * carries Devanagari or Latin characters. Blank input is [OTHER].
 */
fun detectSearchLanguage(query: String): SearchLanguage {
    var sawDevanagari = false
    for (ch in query) {
        if (ch.isHangul()) return SearchLanguage.KOREAN
        if (ch.isDevanagari()) sawDevanagari = true
    }
    return if (sawDevanagari) SearchLanguage.NEPALI else SearchLanguage.OTHER
}

/**
 * True when [query] exactly equals one word's Korean, Nepali,
 * romanization, or English meaning (case-insensitive for the Latin
 * fields) — the same fields the DAO's exact-first ordering uses.
 * The online trigger uses this for non-Korean queries: an exact
 * local match means the word is already at the top of the list, so
 * no Gemini call is spent on it.
 */
fun hasExactMatch(words: List<VocabularyWord>, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return false
    return words.any { word ->
        word.korean == trimmed ||
            word.nepali == trimmed ||
            word.romanization == trimmed ||
            word.english.equals(trimmed, ignoreCase = true)
    }
}
