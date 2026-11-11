package com.hangulmate.ml

import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.repository.VocabularyRepository
import kotlinx.coroutines.flow.first

/**
 * One token found in scanned/typed text, with the dictionary entry it
 * resolved to (or null when the token is not in the dictionary).
 * [lookupForm] is the form that actually matched — the raw token, or a
 * particle-stripped form when the raw token did not match.
 */
data class BreakdownToken(
    val raw: String,
    val lookupForm: String,
    val word: VocabularyWord?,
)

/**
 * Result of running scanned or typed Korean text through the dictionary.
 * Everything here comes from real dictionary entries — no meanings are
 * invented. [phraseWord] is set when the whole text, as-is, is itself a
 * dictionary entry. [partial] pairs each token with its closest
 * dictionary entry; [unknown] lists tokens with no match at all.
 */
data class TranslationBreakdown(
    val phraseWord: VocabularyWord?,
    val tokens: List<BreakdownToken>,
    val exact: List<VocabularyWord>,
    val partial: List<Pair<String, VocabularyWord>>,
    val unknown: List<String>,
)

/** Korean particles / sentence endings stripped (longest first) for lookup only. */
private val PARTICLE_SUFFIXES = listOf(
    "에서부터", "으로부터", "에게서", "한테서",
    "에서", "으로", "에게", "한테", "보다", "처럼", "하고",
    "이나", "이랑", "부터", "까지", "든지",
    "입니다", "이에요", "예요", "아요", "어요", "해요", "습니다", "이다",
    "의", "에", "은", "는", "이", "가", "을", "를",
    "와", "과", "도", "만", "든", "야", "아", "어", "요", "다", "랑", "나",
).sortedByDescending { it.length }

/** Splits text into word tokens on anything that is not a letter or digit. */
fun tokenizeKorean(text: String): List<String> =
    text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }

/**
 * Lookup forms for [token]: the raw token first, then particle-stripped
 * forms (up to two stripping passes). The raw token is always tried
 * first so an entry that genuinely ends in a particle is still found.
 */
fun lookupForms(token: String): List<String> {
    val forms = linkedSetOf(token)
    var current = listOf(token)
    repeat(2) {
        val next = mutableListOf<String>()
        for (form in current) {
            for (suffix in PARTICLE_SUFFIXES) {
                if (form.length > suffix.length && form.endsWith(suffix)) {
                    val stripped = form.dropLast(suffix.length)
                    if (stripped.isNotEmpty() && forms.add(stripped)) {
                        next += stripped
                    }
                }
            }
        }
        current = next
        if (current.isEmpty()) return@repeat
    }
    return forms.toList()
}

private suspend fun findPartial(
    token: String,
    repository: VocabularyRepository,
): VocabularyWord? =
    repository.search(token).first()
        .firstOrNull { it.korean.contains(token) || token.contains(it.korean) }

/**
 * Builds the word-by-word breakdown of [text] against the dictionary.
 * Never invents a translation: tokens with no entry land in [unknown].
 */
suspend fun buildTranslationBreakdown(
    text: String,
    repository: VocabularyRepository,
): TranslationBreakdown {
    val normalized = text.trim().replace(Regex("\\s+"), " ")
    val phraseWord = if (normalized.isNotEmpty()) {
        repository.getByKorean(normalized)
    } else {
        null
    }

    val tokens = mutableListOf<BreakdownToken>()
    val exact = mutableListOf<VocabularyWord>()
    val exactSeen = mutableSetOf<String>()
    val partial = mutableListOf<Pair<String, VocabularyWord>>()
    val unknown = mutableListOf<String>()

    for (token in tokenizeKorean(text)) {
        var word: VocabularyWord? = null
        var lookupForm = token
        for (candidate in lookupForms(token)) {
            word = repository.getByKorean(candidate)
            if (word != null) {
                lookupForm = candidate
                break
            }
        }
        when {
            word != null -> {
                tokens += BreakdownToken(token, lookupForm, word)
                if (exactSeen.add(word.id)) exact += word
            }
            else -> {
                val partialWord = findPartial(token, repository)
                if (partialWord != null) {
                    tokens += BreakdownToken(token, token, partialWord)
                    partial += token to partialWord
                } else {
                    tokens += BreakdownToken(token, token, null)
                    unknown += token
                }
            }
        }
    }

    return TranslationBreakdown(
        phraseWord = phraseWord,
        tokens = tokens,
        exact = exact,
        partial = partial,
        unknown = unknown,
    )
}
