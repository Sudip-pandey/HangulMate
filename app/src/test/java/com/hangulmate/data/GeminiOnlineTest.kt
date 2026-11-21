package com.hangulmate.data

import com.hangulmate.domain.repository.GeminiKeyStore
import com.hangulmate.ml.GeminiError
import com.hangulmate.ml.GeminiIdentifyResult
import com.hangulmate.ml.GeminiObjectResult
import com.hangulmate.ml.GeminiOnline
import com.hangulmate.ml.GeminiRequest
import com.hangulmate.ml.KoreanWordMatcher
import com.hangulmate.ml.maskApiKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gemini online-identification tests: prompt building, response
 * parsing, HTTP-error mapping, seed matching, key masking and the
 * key-store contract against an in-memory fake.
 *
 * Runs under Robolectric (like [GrammarParserTest]) only because the
 * production parser uses org.json, which is a stub on the plain JVM
 * classpath — the test itself imports no Android framework classes,
 * and the logic under test imports no android.* classes either.
 * The fake key below is an obviously fake placeholder, never a real key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiOnlineTest {

    // -- Helpers -------------------------------------------------------

    private fun validResponseBody(
        korean: String = "사과",
        english: String = "apple",
        nepali: String = "स्याउ",
    ): String {
        val inner = "{\"korean\": \"$korean\", \"english\": \"$english\", \"nepali\": \"$nepali\"}"
        val escaped = inner.replace("\"", "\\\"")
        return "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"$escaped\"}]}}]}"
    }

    private class FakeGeminiKeyStore : GeminiKeyStore {
        private var stored: String? = null
        override suspend fun getKey(): String? = stored
        override suspend fun hasKey(): Boolean = !stored.isNullOrBlank()
        override suspend fun saveKey(key: String) {
            stored = key.trim().ifEmpty { null }
        }

        override suspend fun clearKey() {
            stored = null
        }
    }

    // -- Prompt / request building -------------------------------------

    @Test
    fun requestJson_containsInlineImageAndJsonInstruction() {
        val json = GeminiRequest.buildRequestJson("QUJDREVG")

        assertTrue(json.contains("inline_data"))
        assertTrue(json.contains("image/jpeg"))
        assertTrue(json.contains("QUJDREVG"))
        assertTrue(json.contains("korean"))
        assertTrue(json.contains("english"))
        assertTrue(json.contains("nepali"))
        assertTrue(json.contains("application/json"))
    }

    @Test
    fun buildUrl_pointsAtSingleModelConstant() {
        val url = GeminiOnline.buildUrl("test-key-1234")

        assertTrue(url.startsWith("https://generativelanguage.googleapis.com/v1beta/models/"))
        assertTrue(url.contains(GeminiOnline.MODEL))
        assertTrue(url.contains(":generateContent?key=test-key-1234"))
    }

    // -- Response parsing ----------------------------------------------

    @Test
    fun validResponse_parsesAllThreeFields() {
        val result = GeminiRequest.parseResponse(validResponseBody())

        assertEquals(
            GeminiIdentifyResult.Success(
                GeminiObjectResult(korean = "사과", english = "apple", nepali = "स्याउ"),
            ),
            result,
        )
    }

    @Test
    fun codeFencedResponse_parses() {
        val inner = "{\"korean\": \"바나나\", \"english\": \"banana\", \"nepali\": \"केरा\"}"
        val fencedText = "```json $inner ```".replace("\"", "\\\"")
        val body =
            "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"$fencedText\"}]}}]}"

        val result = GeminiRequest.parseResponse(body)

        assertEquals(
            GeminiIdentifyResult.Success(
                GeminiObjectResult(korean = "바나나", english = "banana", nepali = "केरा"),
            ),
            result,
        )
    }

    @Test
    fun proseAroundJson_parses() {
        val inner = "{\"korean\": \"물\", \"english\": \"water\", \"nepali\": \"पानी\"}"
        val escaped = "Here is the object: $inner Hope this helps!"
            .replace("\"", "\\\"")
        val body = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"$escaped\"}]}}]}"

        val result = GeminiRequest.parseResponse(body)

        assertEquals(
            GeminiIdentifyResult.Success(
                GeminiObjectResult(korean = "물", english = "water", nepali = "पानी"),
            ),
            result,
        )
    }

    @Test
    fun malformedJson_returnsEmptyResponse() {
        val body = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"{not json\"}]}}]}"

        assertEquals(
            GeminiIdentifyResult.Failure(GeminiError.EMPTY_RESPONSE),
            GeminiRequest.parseResponse(body),
        )
    }

    @Test
    fun emptyCandidates_returnsEmptyResponse() {
        assertEquals(
            GeminiIdentifyResult.Failure(GeminiError.EMPTY_RESPONSE),
            GeminiRequest.parseResponse("{\"candidates\": []}"),
        )
    }

    @Test
    fun missingText_returnsEmptyResponse() {
        val body = "{\"candidates\": [{\"content\": {\"parts\": []}}]}"

        assertEquals(
            GeminiIdentifyResult.Failure(GeminiError.EMPTY_RESPONSE),
            GeminiRequest.parseResponse(body),
        )
    }

    @Test
    fun blankKorean_returnsEmptyResponse() {
        val result = GeminiRequest.parseResponse(validResponseBody(korean = "   "))

        assertEquals(
            GeminiIdentifyResult.Failure(GeminiError.EMPTY_RESPONSE),
            result,
        )
    }

    @Test
    fun missingEnglishAndNepali_stillParsesKorean() {
        val inner = "{\"korean\": \"책\"}"
        val escaped = inner.replace("\"", "\\\"")
        val body = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"$escaped\"}]}}]}"

        assertEquals(
            GeminiIdentifyResult.Success(
                GeminiObjectResult(korean = "책", english = "", nepali = ""),
            ),
            GeminiRequest.parseResponse(body),
        )
    }

    @Test
    fun garbageBody_returnsEmptyResponse() {
        assertEquals(
            GeminiIdentifyResult.Failure(GeminiError.EMPTY_RESPONSE),
            GeminiRequest.parseResponse("not json at all"),
        )
    }

    // -- HTTP error mapping --------------------------------------------

    @Test
    fun mapHttpStatus_authErrorsAreInvalidKey() {
        assertEquals(GeminiError.INVALID_KEY, GeminiRequest.mapHttpStatus(400))
        assertEquals(GeminiError.INVALID_KEY, GeminiRequest.mapHttpStatus(401))
        assertEquals(GeminiError.INVALID_KEY, GeminiRequest.mapHttpStatus(403))
    }

    @Test
    fun mapHttpStatus_quotaIs429() {
        assertEquals(GeminiError.QUOTA_EXHAUSTED, GeminiRequest.mapHttpStatus(429))
    }

    @Test
    fun mapHttpStatus_otherCodesAreUnknown() {
        assertEquals(GeminiError.UNKNOWN, GeminiRequest.mapHttpStatus(500))
        assertEquals(GeminiError.UNKNOWN, GeminiRequest.mapHttpStatus(503))
    }

    // -- Seed matching ---------------------------------------------------

    @Test
    fun matcher_exactMatchWins() {
        val candidates = listOf("사과", "사과주스", "바나나")

        assertEquals("사과", KoreanWordMatcher.match(candidates, "사과"))
    }

    @Test
    fun matcher_normalizedMatchStripsWhitespace() {
        val candidates = listOf("사과 주스", "바나나")

        assertEquals("사과 주스", KoreanWordMatcher.match(candidates, "사과주스"))
        assertEquals("사과 주스", KoreanWordMatcher.match(candidates, " 사과 주스 "))
    }

    @Test
    fun matcher_noMatchReturnsNull() {
        assertNull(KoreanWordMatcher.match(listOf("사과", "바나나"), "컴퓨터"))
    }

    @Test
    fun matcher_blankQueryReturnsNull() {
        assertNull(KoreanWordMatcher.match(listOf("사과"), "   "))
        assertNull(KoreanWordMatcher.match(emptyList(), "사과"))
    }

    // -- Key masking -----------------------------------------------------

    @Test
    fun maskApiKey_showsOnlyLastFour() {
        assertEquals("••••1234", maskApiKey("abcdefgh1234"))
    }

    @Test
    fun maskApiKey_shortKeyShowsNoCharacters() {
        assertEquals("••••", maskApiKey("abc"))
    }

    // -- Key store (fake) -------------------------------------------------

    @Test
    fun fakeKeyStore_roundTrip() = runTest {
        val store = FakeGeminiKeyStore()

        assertFalse(store.hasKey())
        assertNull(store.getKey())

        store.saveKey("  test-key-1234  ")
        assertTrue(store.hasKey())
        assertEquals("test-key-1234", store.getKey())

        store.clearKey()
        assertFalse(store.hasKey())
        assertNull(store.getKey())
    }

    @Test
    fun fakeKeyStore_blankSaveClears() = runTest {
        val store = FakeGeminiKeyStore()
        store.saveKey("test-key-1234")
        store.saveKey("   ")

        assertFalse(store.hasKey())
    }
}
