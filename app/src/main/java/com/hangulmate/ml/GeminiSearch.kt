package com.hangulmate.ml

import org.json.JSONObject

/**
 * Pure (no Android framework) logic for the Gemini online word search:
 * when a word is not in the offline seed, look it up with Gemini and
 * save the answer locally (see
 * [com.hangulmate.data.repository.VocabularyRepositoryImpl.searchOnline]).
 *
 * Everything in this file is unit-testable as plain JUnit: prompt
 * building and response parsing. The HTTP call itself lives in
 * [com.hangulmate.data.gemini.GeminiVisionClient], which reuses
 * [GeminiOnline.MODEL] and [GeminiRequest.mapHttpStatus] — the same
 * model and error mapping as the online object identification.
 *
 * Privacy: the search text is sent to Google's Gemini API only when
 * the user has saved their own API key (see
 * [com.hangulmate.domain.repository.GeminiKeyStore]). No key = no
 * online search; nothing here ever sends anything.
 */

/** Tag stored on words saved from a Gemini lookup (see VocabularyWord.tags). */
const val GEMINI_TAG = "gemini"

/** One similar / opposite word, as returned by the model. */
data class GeminiRelatedWord(
    val korean: String,
    val english: String,
    val nepali: String,
    val partOfSpeech: String = "",
)

/** One word lookup, as returned by the model. */
data class GeminiWordResult(
    val korean: String,
    val english: String,
    val nepali: String,
    val romanization: String,
    val partOfSpeech: String,
    /** Every similar word the model knows — never truncated. */
    val similar: List<GeminiRelatedWord>,
    /** Every opposite word the model knows — never truncated. */
    val opposite: List<GeminiRelatedWord>,
)

/** Outcome of one online word lookup. */
sealed interface GeminiWordSearchResult {
    data class Success(val word: GeminiWordResult) : GeminiWordSearchResult

    /** The model answered, but no Korean word matches the query. */
    data object NotFound : GeminiWordSearchResult

    data class Failure(val error: GeminiError) : GeminiWordSearchResult
}

/**
 * Builds the generateContent request body for a word lookup (text
 * only — no photo) and parses the response.
 */
object GeminiSearch {

    /**
     * Instruction sent with every lookup. Asks for ONLY a JSON object
     * with the word's meanings plus its FULL similar and opposite
     * lists, so the parser has one shape to handle. The query may be
     * a Korean word, a Nepali meaning, or an English meaning.
     */
    fun buildPrompt(query: String): String =
        "Look up a Korean dictionary word for this search text: \"$query\". " +
            "The search text may be a Korean word, a Nepali meaning, or an English meaning. " +
            "Reply with ONLY a JSON object, no other text: " +
            "{\"korean\": \"...\", \"english\": \"...\", \"nepali\": \"...\", " +
            "\"romanization\": \"...\", \"partOfSpeech\": \"noun\", " +
            "\"similar\": [{\"korean\": \"...\", \"english\": \"...\", \"nepali\": \"...\", \"partOfSpeech\": \"noun\"}], " +
            "\"opposite\": [{\"korean\": \"...\", \"english\": \"...\", \"nepali\": \"...\", \"partOfSpeech\": \"noun\"}]}. " +
            "\"korean\" is one common Korean word in base/dictionary form. " +
            "\"english\" is the English meaning. \"nepali\" is the Nepali meaning in Devanagari script. " +
            "\"romanization\" is the romanized spelling. \"partOfSpeech\" is one of: " +
            "noun, verb, adjective, adverb, expression, number, pronoun, particle, interjection. " +
            "\"similar\" lists EVERY similar word (synonym) you know, each with its Nepali meaning — " +
            "include them all, never shorten the list. " +
            "\"opposite\" lists EVERY opposite word (antonym) you know, each with its Nepali meaning — " +
            "include them all, never shorten the list. " +
            "Use empty arrays when there are none. " +
            "Never invent a word: if no Korean word matches the search text, reply with {\"korean\": \"\"}."

    /**
     * Request body: one text part with the lookup instruction.
     * `responseMimeType` asks the model for JSON; the parser still
     * tolerates plain text and code fences.
     */
    fun buildRequestJson(query: String): String {
        val textPart = JSONObject().put("text", buildPrompt(query))
        val parts = org.json.JSONArray().put(textPart)
        val content = JSONObject().put("parts", parts)
        val contents = org.json.JSONArray().put(content)
        val generationConfig = JSONObject()
            .put("responseMimeType", "application/json")
            .put("temperature", 0.0)
        return JSONObject()
            .put("contents", contents)
            .put("generationConfig", generationConfig)
            .toString()
    }

    /**
     * Parses a raw generateContent response body. Never throws: any
     * malformed, empty or unexpected shape becomes
     * [GeminiWordSearchResult.Failure] with [GeminiError.EMPTY_RESPONSE].
     * A well-formed answer with a blank "korean" is [GeminiWordSearchResult.NotFound]
     * — the model saying plainly that nothing matches, not an error.
     * Similar / opposite entries with a blank Korean spelling are
     * skipped; every other entry is kept, in the model's order.
     */
    fun parseWordResponse(body: String): GeminiWordSearchResult {
        val text = GeminiRequest.extractModelText(body)
            ?: return GeminiWordSearchResult.Failure(GeminiError.EMPTY_RESPONSE)
        val jsonText = GeminiRequest.unwrapJsonObject(text)
            ?: return GeminiWordSearchResult.Failure(GeminiError.EMPTY_RESPONSE)
        val obj = runCatching { JSONObject(jsonText) }.getOrNull()
            ?: return GeminiWordSearchResult.Failure(GeminiError.EMPTY_RESPONSE)
        val korean = obj.optString("korean").trim()
        if (korean.isEmpty()) {
            return GeminiWordSearchResult.NotFound
        }
        return GeminiWordSearchResult.Success(
            GeminiWordResult(
                korean = korean,
                english = obj.optString("english").trim(),
                nepali = obj.optString("nepali").trim(),
                romanization = obj.optString("romanization").trim(),
                partOfSpeech = obj.optString("partOfSpeech").trim(),
                similar = obj.optRelated("similar"),
                opposite = obj.optRelated("opposite"),
            ),
        )
    }

    private fun JSONObject.optRelated(key: String): List<GeminiRelatedWord> {
        val array = optJSONArray(key) ?: return emptyList()
        val out = mutableListOf<GeminiRelatedWord>()
        for (i in 0 until array.length()) {
            val rel = array.optJSONObject(i) ?: continue
            val korean = rel.optString("korean").trim()
            if (korean.isEmpty()) continue
            out += GeminiRelatedWord(
                korean = korean,
                english = rel.optString("english").trim(),
                nepali = rel.optString("nepali").trim(),
                partOfSpeech = rel.optString("partOfSpeech").trim(),
            )
        }
        return out
    }
}
