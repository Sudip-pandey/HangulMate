package com.hangulmate.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hangulmate.data.local.HangulMateDatabase
import com.hangulmate.data.local.VocabularyEntity
import com.hangulmate.data.gemini.GeminiWordSearchClient
import com.hangulmate.data.repository.VocabularyRepositoryImpl
import com.hangulmate.domain.repository.GeminiKeyStore
import com.hangulmate.domain.repository.OnlineSearchResult
import com.hangulmate.ml.GEMINI_TAG
import com.hangulmate.ml.GeminiError
import com.hangulmate.ml.GeminiRelatedWord
import com.hangulmate.ml.GeminiSearch
import com.hangulmate.ml.GeminiWordResult
import com.hangulmate.ml.GeminiWordSearchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gemini online word search tests: prompt building, response parsing
 * (including FULL similar / opposite lists with Nepali glosses), and
 * the repository fallback logic — cache-first, miss + key calls
 * Gemini and saves, miss + no key never calls, failures save nothing.
 *
 * Runs under Robolectric (like [GeminiOnlineTest]) only because the
 * production parser uses org.json, which is a stub on the plain JVM
 * classpath. The fake key below is an obviously fake placeholder,
 * never a real key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiWordSearchTest {

    // -- Helpers -------------------------------------------------------

    /** Wraps model text in a generateContent response body. */
    private fun wrapModelText(modelText: String): String {
        val escaped = modelText.replace("\"", "\\\"")
        return "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"$escaped\"}]}}]}"
    }

    private class FakeGeminiWordSearchClient(
        var result: GeminiWordSearchResult,
    ) : GeminiWordSearchClient {
        var calls = 0
        var lastQuery: String? = null
        var lastApiKey: String? = null

        override suspend fun searchWord(apiKey: String, query: String): GeminiWordSearchResult {
            calls++
            lastApiKey = apiKey
            lastQuery = query
            return result
        }
    }

    private class FakeGeminiKeyStore(var key: String? = "fake-key-for-tests") : GeminiKeyStore {
        override suspend fun getKey(): String? = key
        override suspend fun hasKey(): Boolean = !key.isNullOrBlank()
        override suspend fun saveKey(key: String) {
            this.key = key.trim().ifEmpty { null }
        }

        override suspend fun clearKey() {
            key = null
        }
    }

    private fun geminiWord(
        korean: String = "부드럽다",
        english: String = "to be soft",
        nepali: String = "नरम हुनु",
        similar: List<GeminiRelatedWord> = listOf(
            GeminiRelatedWord("연하다", "to be soft", "नरम हुनु", "verb"),
            GeminiRelatedWord("말랑하다", "to be squishy", "नरम हुनु", "adjective"),
            GeminiRelatedWord("유연하다", "to be flexible", "लचिलो हुनु", "adjective"),
        ),
        opposite: List<GeminiRelatedWord> = listOf(
            GeminiRelatedWord("딱딱하다", "to be hard", "कडा हुनु", "adjective"),
            GeminiRelatedWord("뻣뻣하다", "to be stiff", "कडा हुनु", "adjective"),
        ),
    ) = GeminiWordResult(
        korean = korean,
        english = english,
        nepali = nepali,
        romanization = "budeureopda",
        partOfSpeech = "verb",
        similar = similar,
        opposite = opposite,
    )

    // -- Prompt / request building -------------------------------------

    @Test
    fun requestJson_containsQueryAndJsonMimeType() {
        val json = GeminiSearch.buildRequestJson("사과")
        val root = JSONObject(json)
        val text = root.getJSONArray("contents")
            .getJSONObject(0).getJSONArray("parts")
            .getJSONObject(0).getString("text")
        assertTrue(text.contains("사과"))
        assertTrue(text.contains("similar"))
        assertTrue(text.contains("opposite"))
        assertEquals(
            "application/json",
            root.getJSONObject("generationConfig").getString("responseMimeType"),
        )
    }

    // -- Response parsing ----------------------------------------------

    @Test
    fun validResponse_parsesAllSimilarAndOpposite() {
        val inner = """
            {"korean": "부드럽다", "english": "to be soft", "nepali": "नरम हुनु",
             "romanization": "budeureopda", "partOfSpeech": "verb",
             "similar": [
               {"korean": "연하다", "english": "to be soft", "nepali": "नरम हुनु", "partOfSpeech": "verb"},
               {"korean": "말랑하다", "english": "to be squishy", "nepali": "नरम हुनु", "partOfSpeech": "adjective"},
               {"korean": "유연하다", "english": "to be flexible", "nepali": "लचिलो हुनु", "partOfSpeech": "adjective"},
               {"korean": "포근하다", "english": "to be cozy", "nepali": "न्यानो हुनु", "partOfSpeech": "adjective"}
             ],
             "opposite": [
               {"korean": "딱딱하다", "english": "to be hard", "nepali": "कडा हुनु", "partOfSpeech": "adjective"},
               {"korean": "뻣뻣하다", "english": "to be stiff", "nepali": "कडा हुनु", "partOfSpeech": "adjective"},
               {"korean": "거칠다", "english": "to be rough", "nepali": "खस्रो हुनु", "partOfSpeech": "adjective"}
             ]}
        """.trimIndent().replace("\n", " ")

        val result = GeminiSearch.parseWordResponse(wrapModelText(inner))

        assertTrue(result is GeminiWordSearchResult.Success)
        val word = (result as GeminiWordSearchResult.Success).word
        assertEquals("부드럽다", word.korean)
        assertEquals("नरम हुनु", word.nepali)
        // ALL similar / opposite words, in the model's order, with glosses.
        assertEquals(
            listOf("연하다", "말랑하다", "유연하다", "포근하다"),
            word.similar.map { it.korean },
        )
        assertEquals(
            listOf("딱딱하다", "뻣뻣하다", "거칠다"),
            word.opposite.map { it.korean },
        )
        assertEquals("नरम हुनु", word.similar[0].nepali)
        assertEquals("खस्रो हुनु", word.opposite[2].nepali)
    }

    @Test
    fun codeFencedResponse_parses() {
        val inner = "{\"korean\": \"사과\", \"english\": \"apple\", \"nepali\": \"स्याउ\", " +
            "\"similar\": [], \"opposite\": []}"
        val fenced = "```json $inner ```"

        val result = GeminiSearch.parseWordResponse(wrapModelText(fenced))

        assertEquals(
            GeminiWordSearchResult.Success(
                GeminiWordResult(
                    korean = "사과",
                    english = "apple",
                    nepali = "स्याउ",
                    romanization = "",
                    partOfSpeech = "",
                    similar = emptyList(),
                    opposite = emptyList(),
                ),
            ),
            result,
        )
    }

    @Test
    fun blankKorean_returnsNotFound() {
        val inner = "{\"korean\": \"   \", \"english\": \"\", \"nepali\": \"\"}"

        assertEquals(
            GeminiWordSearchResult.NotFound,
            GeminiSearch.parseWordResponse(wrapModelText(inner)),
        )
    }

    @Test
    fun malformedJson_returnsEmptyFailure() {
        val body = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"{not json\"}]}}]}"

        assertEquals(
            GeminiWordSearchResult.Failure(GeminiError.EMPTY_RESPONSE),
            GeminiSearch.parseWordResponse(body),
        )
    }

    @Test
    fun emptyCandidates_returnsEmptyFailure() {
        assertEquals(
            GeminiWordSearchResult.Failure(GeminiError.EMPTY_RESPONSE),
            GeminiSearch.parseWordResponse("{\"candidates\": []}"),
        )
    }

    @Test
    fun missingOptionalFields_defaultToEmpty() {
        val inner = "{\"korean\": \"책\"}"

        val result = GeminiSearch.parseWordResponse(wrapModelText(inner))

        assertEquals(
            GeminiWordSearchResult.Success(
                GeminiWordResult(
                    korean = "책",
                    english = "",
                    nepali = "",
                    romanization = "",
                    partOfSpeech = "",
                    similar = emptyList(),
                    opposite = emptyList(),
                ),
            ),
            result,
        )
    }

    @Test
    fun blankKoreanRelatedEntries_areSkipped() {
        val inner = "{\"korean\": \"사과\", \"similar\": [" +
            "{\"korean\": \"\"}, {\"korean\": \"배\", \"nepali\": \"नासपाती\"}], \"opposite\": []}"

        val result = GeminiSearch.parseWordResponse(wrapModelText(inner))

        assertTrue(result is GeminiWordSearchResult.Success)
        val word = (result as GeminiWordSearchResult.Success).word
        assertEquals(listOf("배"), word.similar.map { it.korean })
        assertEquals("नासपाती", word.similar[0].nepali)
    }

    // -- Repository fallback -------------------------------------------

    private lateinit var db: HangulMateDatabase
    private lateinit var fakeClient: FakeGeminiWordSearchClient
    private lateinit var fakeKeyStore: FakeGeminiKeyStore
    private lateinit var repo: VocabularyRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HangulMateDatabase::class.java,
        ).allowMainThreadQueries().build()
        fakeClient = FakeGeminiWordSearchClient(GeminiWordSearchResult.NotFound)
        fakeKeyStore = FakeGeminiKeyStore()
        repo = VocabularyRepositoryImpl(db, fakeClient, fakeKeyStore)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedWordSuspended(id: String, korean: String, nepali: String = "अर्थ", english: String = "meaning") {
        db.vocabularyDao().insert(
            VocabularyEntity(
                id = id, korean = korean, nepali = nepali, english = english,
                romanization = "", syllablesJson = "[]",
                partOfSpeech = "noun", topic = "general", difficulty = 1,
                epsImportance = "high",
                exampleKo = "", exampleNe = "", exampleEn = "",
                tagsJson = "[]", isUserCreated = false,
            ),
        )
    }

    @Test
    fun hit_returnsCachedWordWithoutCallingGemini() = runTest {
        seedWordSuspended("HM-0001", "사과", "स्याउ", "apple")

        val result = repo.searchOnline("사과")

        assertTrue(result is OnlineSearchResult.Found)
        val found = result as OnlineSearchResult.Found
        assertTrue(found.fromCache)
        assertEquals("HM-0001", found.word.id)
        assertEquals(0, fakeClient.calls)
    }

    @Test
    fun missWithKey_callsGeminiSavesWordAndAllRelations() = runTest {
        fakeClient.result = GeminiWordSearchResult.Success(geminiWord())

        val result = repo.searchOnline("부드럽다")

        assertTrue(result is OnlineSearchResult.Found)
        val found = result as OnlineSearchResult.Found
        assertFalse(found.fromCache)
        assertEquals(1, fakeClient.calls)
        assertEquals("부드럽다", fakeClient.lastQuery)
        assertEquals("fake-key-for-tests", fakeClient.lastApiKey)
        assertTrue(found.word.id.startsWith("HM-G"))

        // What the word detail screen renders: the full relation lists.
        val detail = repo.getById(found.word.id).first()!!
        assertEquals(
            listOf("연하다", "말랑하다", "유연하다"),
            detail.similar.map { it.targetKorean },
        )
        assertEquals(
            listOf("딱딱하다", "뻣뻣하다"),
            detail.opposite.map { it.targetKorean },
        )
        assertEquals("नरम हुनु", detail.similar[0].noteNe)
        assertEquals("कडा हुनु", detail.opposite[0].noteNe)
        // Every relation resolves to a real row (tappable in the UI).
        assertTrue(detail.similar.all { it.targetWordId != null })
        assertTrue(detail.opposite.all { it.targetWordId != null })
        // AI labelling: gemini tag + mt_review, never "verified".
        assertTrue(GEMINI_TAG in detail.tags)
        assertEquals("mt_review", detail.nepaliStatus)
        assertTrue(detail.isUserCreated)
        assertEquals(listOf("부", "드", "럽", "다"), detail.syllables)
    }

    @Test
    fun secondSearchForSameWord_usesCacheWithoutSecondCall() = runTest {
        fakeClient.result = GeminiWordSearchResult.Success(geminiWord())
        val first = repo.searchOnline("부드럽다") as OnlineSearchResult.Found
        assertFalse(first.fromCache)

        val second = repo.searchOnline("부드럽다")

        assertTrue(second is OnlineSearchResult.Found)
        assertTrue((second as OnlineSearchResult.Found).fromCache)
        assertEquals(first.word.id, second.word.id)
        assertEquals(1, fakeClient.calls)
    }

    @Test
    fun missWithoutKey_returnsNoKeyWithoutCalling() = runTest {
        fakeKeyStore.key = null

        val result = repo.searchOnline("부드럽다")

        assertEquals(OnlineSearchResult.NoKey, result)
        assertEquals(0, fakeClient.calls)
        assertEquals(0, repo.getTotalCount().first())
    }

    @Test
    fun noClientConfigured_returnsNoKey() = runTest {
        val offlineOnlyRepo = VocabularyRepositoryImpl(db)

        val result = offlineOnlyRepo.searchOnline("부드럽다")

        assertEquals(OnlineSearchResult.NoKey, result)
        assertEquals(0, fakeClient.calls)
    }

    @Test
    fun geminiFailure_returnsFailedAndSavesNothing() = runTest {
        fakeClient.result = GeminiWordSearchResult.Failure(GeminiError.QUOTA_EXHAUSTED)

        val result = repo.searchOnline("부드럽다")

        assertEquals(
            OnlineSearchResult.Failed(GeminiError.QUOTA_EXHAUSTED),
            result,
        )
        assertEquals(1, fakeClient.calls)
        assertEquals(0, repo.getTotalCount().first())
    }

    @Test
    fun geminiNotFound_returnsNotFoundAndSavesNothing() = runTest {
        fakeClient.result = GeminiWordSearchResult.NotFound

        val result = repo.searchOnline("없는단어")

        assertEquals(OnlineSearchResult.NotFound, result)
        assertEquals(1, fakeClient.calls)
        assertEquals(0, repo.getTotalCount().first())
    }

    @Test
    fun relatedWordAlreadyInDb_resolvesToExistingId() = runTest {
        seedWordSuspended("HM-0009", "바나나", "केरा", "banana")
        fakeClient.result = GeminiWordSearchResult.Success(
            geminiWord(
                similar = listOf(
                    GeminiRelatedWord("바나나", "banana", "केरा", "noun"),
                    GeminiRelatedWord("연하다", "to be soft", "नरम हुनु", "verb"),
                ),
            ),
        )

        val result = repo.searchOnline("부드럽다") as OnlineSearchResult.Found
        val detail = repo.getById(result.word.id).first()!!

        // "바나나" resolves to the seeded row — no duplicate stub.
        val bananaRelation = detail.similar.first { it.targetKorean == "바나나" }
        assertEquals("HM-0009", bananaRelation.targetWordId)
        assertEquals("HM-0009", db.vocabularyDao().getByKorean("바나나")?.id)
        assertEquals(2, detail.similar.size)
    }

    @Test
    fun blankQuery_returnsNotFoundWithoutCalling() = runTest {
        val result = repo.searchOnline("   ")

        assertEquals(OnlineSearchResult.NotFound, result)
        assertEquals(0, fakeClient.calls)
    }

    @Test
    fun selfReferenceInRelated_isDropped() = runTest {
        fakeClient.result = GeminiWordSearchResult.Success(
            geminiWord(
                similar = listOf(
                    GeminiRelatedWord("부드럽다", "to be soft", "नरम हुनु", "verb"),
                    GeminiRelatedWord("연하다", "to be soft", "नरम हुनु", "verb"),
                ),
            ),
        )

        val result = repo.searchOnline("부드럽다") as OnlineSearchResult.Found
        val detail = repo.getById(result.word.id).first()!!

        assertEquals(listOf("연하다"), detail.similar.map { it.targetKorean })
    }

    @Test
    fun wordWithNoMeanings_returnsNotFoundWithoutSaving() = runTest {
        fakeClient.result = GeminiWordSearchResult.Success(
            geminiWord(english = "", nepali = ""),
        )

        val result = repo.searchOnline("부드럽다")

        assertEquals(OnlineSearchResult.NotFound, result)
        assertEquals(0, repo.getTotalCount().first())
    }

    // -- Multilingual search -------------------------------------------

    @Test
    fun promptMentionsRomanNepaliAndMostCommon() {
        val prompt = GeminiSearch.buildPrompt("pani")

        assertTrue(prompt.contains("Roman"))
        assertTrue(prompt.contains("most common"))
    }

    @Test
    fun nepaliQueryMiss_callsGeminiWithRawQueryAndSaves() = runTest {
        fakeClient.result = GeminiWordSearchResult.Success(geminiWord())

        val result = repo.searchOnline("नरम")

        assertTrue(result is OnlineSearchResult.Found)
        val found = result as OnlineSearchResult.Found
        assertFalse(found.fromCache)
        assertEquals(1, fakeClient.calls)
        // The raw Nepali text goes to Gemini unchanged.
        assertEquals("नरम", fakeClient.lastQuery)
        assertEquals("부드럽다", found.word.korean)
        assertEquals("नरम हुनु", found.word.nepali)
        assertTrue(GEMINI_TAG in found.word.tags)
    }

    @Test
    fun geminiReturnsSpacedVariantOfSeedWord_returnsSeedWordWithoutDuplicating() = runTest {
        seedWordSuspended("HM-0010", "사과주스", "स्याउको रस", "apple juice")
        fakeClient.result = GeminiWordSearchResult.Success(
            geminiWord(
                korean = "사과 주스",
                english = "apple juice",
                nepali = "स्याउको रस",
                similar = emptyList(),
                opposite = emptyList(),
            ),
        )

        val result = repo.searchOnline("사과 주스")

        assertTrue(result is OnlineSearchResult.Found)
        val found = result as OnlineSearchResult.Found
        // The seed word wins over a duplicate Gemini save.
        assertTrue(found.fromCache)
        assertEquals("HM-0010", found.word.id)
        assertEquals("사과주스", found.word.korean)
        assertEquals(1, fakeClient.calls)
        assertEquals(1, repo.getTotalCount().first())
    }
}
