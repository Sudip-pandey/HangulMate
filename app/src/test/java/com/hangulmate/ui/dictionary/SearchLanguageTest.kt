package com.hangulmate.ui.dictionary

import com.hangulmate.domain.model.EpsImportance
import com.hangulmate.domain.model.PartOfSpeech
import com.hangulmate.domain.model.Topic
import com.hangulmate.domain.model.VocabularyWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Language detection + exact-match checks for multilingual dictionary
 * search. Plain JUnit — no Android framework is involved.
 */
class SearchLanguageTest {

    private fun word(
        korean: String,
        nepali: String,
        english: String,
        romanization: String = "",
    ) = VocabularyWord(
        id = "HM-T1",
        korean = korean,
        nepali = nepali,
        english = english,
        romanization = romanization,
        syllables = emptyList(),
        partOfSpeech = PartOfSpeech.NOUN,
        topic = Topic.GENERAL,
        difficulty = 1,
        epsImportance = EpsImportance.LOW,
        exampleKo = "",
        exampleNe = "",
        exampleEn = "",
        tags = emptyList(),
    )

    // -- Detection -----------------------------------------------------

    @Test
    fun hangulSyllables_detectedAsKorean() {
        assertEquals(SearchLanguage.KOREAN, detectSearchLanguage("사과"))
        assertEquals(SearchLanguage.KOREAN, detectSearchLanguage("한국어"))
    }

    @Test
    fun choseongJamo_detectedAsKorean() {
        assertEquals(SearchLanguage.KOREAN, detectSearchLanguage("ㅎㄱ"))
    }

    @Test
    fun devanagari_detectedAsNepali() {
        assertEquals(SearchLanguage.NEPALI, detectSearchLanguage("स्याउ"))
        assertEquals(SearchLanguage.NEPALI, detectSearchLanguage("पानी"))
    }

    @Test
    fun latin_detectedAsOther() {
        assertEquals(SearchLanguage.OTHER, detectSearchLanguage("apple"))
        // Roman Nepali is Latin script too — Gemini resolves it.
        assertEquals(SearchLanguage.OTHER, detectSearchLanguage("pani"))
    }

    @Test
    fun mixedHangulAndDevanagari_koreanWins() {
        assertEquals(SearchLanguage.KOREAN, detectSearchLanguage("사과पानी"))
    }

    @Test
    fun blank_detectedAsOther() {
        assertEquals(SearchLanguage.OTHER, detectSearchLanguage(""))
        assertEquals(SearchLanguage.OTHER, detectSearchLanguage("   "))
    }

    // -- Exact match ---------------------------------------------------

    private val words = listOf(
        word(korean = "사과", nepali = "स्याउ", english = "apple", romanization = "sagwa"),
        word(korean = "물", nepali = "पानी", english = "water", romanization = "mul"),
    )

    @Test
    fun exactNepaliMatch_found() {
        assertTrue(hasExactMatch(words, "स्याउ"))
    }

    @Test
    fun exactEnglishMatch_caseInsensitive() {
        assertTrue(hasExactMatch(words, "apple"))
        assertTrue(hasExactMatch(words, "APPLE"))
    }

    @Test
    fun exactKoreanAndRomanizationMatch_found() {
        assertTrue(hasExactMatch(words, "사과"))
        assertTrue(hasExactMatch(words, "sagwa"))
    }

    @Test
    fun queryWithSurroundingSpaces_trimmed() {
        assertTrue(hasExactMatch(words, "  पानी  "))
    }

    @Test
    fun partialOrDifferentMeaning_notExact() {
        assertFalse(hasExactMatch(words, "सेब"))
        assertFalse(hasExactMatch(words, "apples"))
        assertFalse(hasExactMatch(words, "पानी पिउनु"))
    }

    @Test
    fun emptyListOrBlankQuery_notExact() {
        assertFalse(hasExactMatch(emptyList(), "स्याउ"))
        assertFalse(hasExactMatch(words, "   "))
    }
}
