package com.hangulmate.ui.dictionary

import com.hangulmate.domain.model.VocabularyWord

/**
 * Typo-tolerant second pass for dictionary search.
 *
 * The repository search is pure SQL LIKE `%pattern%` — it never catches
 * typos, and it cannot match choseong (initial-consonant) input such as
 * "ㅎㄱ" for "한국". When LIKE returns few results, the Dictionary
 * ViewModel runs this pass over the full word list (a few hundred words,
 * so it stays cheap) and appends what it finds. LIKE results always
 * come first; nothing here invents words.
 */

/** Choseong (initial consonants) in Hangul syllable order. */
private val CHOSEONG = listOf(
    'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
    'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
)

private const val HANGUL_SYLLABLE_START = 0xAC00
private const val HANGUL_SYLLABLE_COUNT = 11_172
private const val JUNGSEONG_COUNT = 21
private const val JONGSEONG_COUNT = 28

private fun isChoseongJamo(char: Char): Boolean = char in CHOSEONG

private fun choseongOfSyllable(char: Char): Char? {
    val code = char.code - HANGUL_SYLLABLE_START
    if (code < 0 || code >= HANGUL_SYLLABLE_COUNT) return null
    return CHOSEONG[code / (JUNGSEONG_COUNT * JONGSEONG_COUNT)]
}

/**
 * The choseong sequence of [text]: each Hangul syllable is replaced by
 * its initial consonant; every other character is kept as-is.
 * For example, choseongOf("한국어") is "ㅎㄱㅇ".
 */
fun choseongOf(text: String): String = buildString {
    for (char in text) {
        append(choseongOfSyllable(char) ?: char)
    }
}

/** True when the choseong letters typed in [query] appear, in order, in [korean]. */
fun isChoseongMatch(query: String, korean: String): Boolean {
    val queryChoseong = query.filter(::isChoseongJamo)
    if (queryChoseong.isEmpty()) return false
    return choseongOf(korean).contains(queryChoseong)
}

/**
 * Levenshtein distance between [a] and [b], capped: returns a value
 * greater than [maxDistance] as soon as the true distance is known to
 * exceed it, so long strings bail out early instead of filling the table.
 */
fun levenshteinWithin(a: String, b: String, maxDistance: Int): Int {
    if (a == b) return 0
    if (kotlin.math.abs(a.length - b.length) > maxDistance) return maxDistance + 1
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        var rowMin = current[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            rowMin = minOf(rowMin, current[j])
        }
        if (rowMin > maxDistance) return maxDistance + 1
        val tmp = previous
        previous = current
        current = tmp
    }
    return previous[b.length]
}

/**
 * Words from [candidates] that match [query] by choseong or by an edit
 * distance of at most [maxDistance] on Korean, romanization or English
 * (case-insensitive for the Latin fields). Words in [alreadyHave] are
 * skipped so LIKE results are never duplicated. At most [maxResults]
 * are returned, in database order.
 *
 * Prefix matching needs no separate pass: LIKE `%pattern%` already
 * matches every substring, so every prefix is covered by the first pass.
 */
fun fuzzyMatches(
    query: String,
    candidates: List<VocabularyWord>,
    alreadyHave: Set<String>,
    maxResults: Int = 15,
    maxDistance: Int = 2,
): List<VocabularyWord> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    val lowerQuery = trimmed.lowercase()
    val out = mutableListOf<VocabularyWord>()
    for (word in candidates) {
        if (word.id in alreadyHave) continue
        val match = isChoseongMatch(trimmed, word.korean) ||
            levenshteinWithin(lowerQuery, word.korean.lowercase(), maxDistance) <= maxDistance ||
            levenshteinWithin(lowerQuery, word.romanization.lowercase(), maxDistance) <= maxDistance ||
            levenshteinWithin(lowerQuery, word.english.lowercase(), maxDistance) <= maxDistance
        if (match) {
            out += word
            if (out.size >= maxResults) break
        }
    }
    return out
}
