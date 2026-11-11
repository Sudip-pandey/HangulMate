package com.hangulmate.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hangulmate.data.local.HangulMateDatabase
import com.hangulmate.data.local.VocabularyEntity
import com.hangulmate.data.local.VocabularyTranslationEntity
import com.hangulmate.data.repository.VocabularyRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Search behaviour tests against the real repository implementation.
 * Covers all four match fields, empty-query behaviour and ordering.
 */
class SearchTest {

    private lateinit var db: HangulMateDatabase
    private lateinit var repo: VocabularyRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HangulMateDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = VocabularyRepositoryImpl(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun word(
        id: String, korean: String, nepali: String, english: String,
        romanization: String, eps: String = "high"
    ) = VocabularyEntity(
        id = id, korean = korean, nepali = nepali, english = english,
        romanization = romanization,
        syllablesJson = "[]",
        partOfSpeech = "noun", topic = "general", difficulty = 1,
        epsImportance = eps,
        exampleKo = "", exampleNe = "", exampleEn = "",
        tagsJson = "[]", isUserCreated = false
    )

    private suspend fun seed() {
        val dao = db.vocabularyDao()
        dao.insertAll(
            listOf(
                word("HM-0001", "안녕하세요", "नमस्ते", "Hello", "annyeonghaseyo", "high"),
                word("HM-0002", "안녕", "नमस्ते", "Hi", "annyeong", "low"),
                word("HM-0003", "감사합니다", "धन्यवाद", "Thank you", "gamsahamnida", "high"),
                word("HM-0004", "학교", "विद्यालय", "School", "hakgyo", "medium")
            )
        )
        db.translationDao().insertAll(
            listOf(
                VocabularyTranslationEntity(vocabId = "HM-0001", language = "ne", meaning = "नमस्ते"),
                VocabularyTranslationEntity(vocabId = "HM-0001", language = "en", meaning = "Hello"),
                VocabularyTranslationEntity(vocabId = "HM-0002", language = "ne", meaning = "नमस्ते"),
                VocabularyTranslationEntity(vocabId = "HM-0002", language = "en", meaning = "Hi"),
                VocabularyTranslationEntity(vocabId = "HM-0003", language = "ne", meaning = "धन्यवाद"),
                VocabularyTranslationEntity(vocabId = "HM-0003", language = "en", meaning = "Thank you"),
                VocabularyTranslationEntity(vocabId = "HM-0004", language = "ne", meaning = "विद्यालय"),
                VocabularyTranslationEntity(vocabId = "HM-0004", language = "en", meaning = "School")
            )
        )
    }

    @Test
    fun partialHangulMatch_findsBothGreetings() = runTest {
        seed()
        // HM-0002 ("안녕") is the exact match so it sorts first; HM-0001 is partial.
        val ids = repo.search("안녕").first().map { it.id }
        assertEquals(listOf("HM-0002", "HM-0001"), ids)
    }

    @Test
    fun devanagariMatch_findsByNepali() = runTest {
        seed()
        val ids = repo.search("धन्य").first().map { it.id }
        assertEquals(listOf("HM-0003"), ids)
    }

    @Test
    fun englishMatch_findsByEnglish() = runTest {
        seed()
        val ids = repo.search("School").first().map { it.id }
        assertEquals(listOf("HM-0004"), ids)
    }

    @Test
    fun romanizationMatch_findsByRomanizedSpelling() = runTest {
        seed()
        // "annyeong" exactly equals HM-0002's romanization, so it sorts first.
        val ids = repo.search("annyeong").first().map { it.id }
        assertEquals(listOf("HM-0002", "HM-0001"), ids)
    }

    @Test
    fun emptyQuery_returnsEmpty_neverWholeTable() = runTest {
        seed()
        // search("") returns an empty Flow (no emission at all).
        val emitted = withTimeoutOrNull(500) { repo.search("").first() }
        assertNull(emitted)
        val blankEmitted = withTimeoutOrNull(500) { repo.search("   ").first() }
        assertNull(blankEmitted)
    }

    @Test
    fun exactMatch_sortsBeforePartial_despiteLowerEps() = runTest {
        seed()
        // "안녕" is an exact match for HM-0002 (low EPS) and only a partial
        // match for HM-0001 (high EPS): exact must win.
        val ids = repo.search("안녕").first().map { it.id }
        assertEquals(listOf("HM-0002", "HM-0001"), ids)
    }

    @Test
    fun noMatch_returnsEmptyList() = runTest {
        seed()
        assertTrue(repo.search("zzz-no-such-word").first().isEmpty())
    }

    @Test
    fun wildcardCharacters_areLiteral_notPatterns() = runTest {
        seed()
        // "%" and "_" in the query must not act as LIKE wildcards.
        assertTrue(repo.search("%").first().isEmpty())
        assertTrue(repo.search("_").first().isEmpty())
    }
}
