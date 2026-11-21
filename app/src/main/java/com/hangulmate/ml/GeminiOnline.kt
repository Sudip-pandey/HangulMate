package com.hangulmate.ml

import org.json.JSONObject

/**
 * Pure (no Android framework) logic for the Gemini online object
 * identification feature. Everything in this file is unit-testable as
 * plain JUnit: prompt building, response parsing, HTTP-error mapping,
 * seed matching and key masking.
 *
 * Privacy: the photo is sent to Google's Gemini API only when the user
 * has saved their own API key (see [com.hangulmate.domain.repository.GeminiKeyStore]).
 * No key = offline TFLite mode only; nothing here ever sends anything.
 */

/** The ONE place the Gemini model name lives. Change it here only. */
object GeminiOnline {
    const val MODEL = "gemini-3.6-flash"

    /**
     * Endpoint for the model above. The key travels as the `key` query
     * parameter, matching the Generative Language API's documented
     * placement. The key is the user's own, supplied at call time —
     * never a constant in this file.
     */
    fun buildUrl(apiKey: String): String =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
}

/** One identified object, as returned by the model. */
data class GeminiObjectResult(
    val korean: String,
    val english: String,
    val nepali: String,
)

/** User-facing failure kinds for online identification. Never a crash. */
enum class GeminiError {
    NO_INTERNET,
    INVALID_KEY,
    QUOTA_EXHAUSTED,
    EMPTY_RESPONSE,
    TIMEOUT,
    UNKNOWN,
}

/** Outcome of one online identification attempt. */
sealed interface GeminiIdentifyResult {
    data class Success(val result: GeminiObjectResult) : GeminiIdentifyResult
    data class Failure(val error: GeminiError) : GeminiIdentifyResult
}

/**
 * Builds the generateContent request body for a JPEG photo (already
 * Base64-encoded by the caller) and parses the response.
 */
object GeminiRequest {

    /**
     * Instruction sent with every photo. Asks for ONLY a JSON object
     * with the three fields, so the response parser has one shape to
     * handle. The Korean name must be a single common word in base /
     * dictionary form — that is what the seed lookup matches against.
     */
    const val PROMPT_TEXT: String =
        "Identify the main object in this photo. Reply with ONLY a JSON " +
            "object, no other text: {\"korean\": \"...\", \"english\": \"...\", " +
            "\"nepali\": \"...\"}. \"korean\" is the object's Korean name " +
            "(one common word, base/dictionary form). \"english\" is the " +
            "English name. \"nepali\" is the Nepali meaning in Devanagari script."

    /**
     * Request body: one image part (inline JPEG data) + the instruction
     * text part. `responseMimeType` asks the model for JSON; the parser
     * still tolerates plain text and code fences.
     */
    fun buildRequestJson(base64Jpeg: String): String {
        val inlineData = JSONObject()
            .put("mime_type", "image/jpeg")
            .put("data", base64Jpeg)
        val imagePart = JSONObject().put("inline_data", inlineData)
        val textPart = JSONObject().put("text", PROMPT_TEXT)
        val parts = org.json.JSONArray()
            .put(imagePart)
            .put(textPart)
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
     * [GeminiIdentifyResult.Failure] with [GeminiError.EMPTY_RESPONSE].
     * The model sometimes wraps the JSON in a ```json code fence or
     * adds prose around it; both are stripped before parsing.
     */
    fun parseResponse(body: String): GeminiIdentifyResult {
        val text = extractModelText(body)
            ?: return GeminiIdentifyResult.Failure(GeminiError.EMPTY_RESPONSE)
        val jsonText = unwrapJsonObject(text)
            ?: return GeminiIdentifyResult.Failure(GeminiError.EMPTY_RESPONSE)
        val obj = runCatching { JSONObject(jsonText) }.getOrNull()
            ?: return GeminiIdentifyResult.Failure(GeminiError.EMPTY_RESPONSE)
        val korean = obj.optString("korean").trim()
        if (korean.isEmpty()) {
            return GeminiIdentifyResult.Failure(GeminiError.EMPTY_RESPONSE)
        }
        return GeminiIdentifyResult.Success(
            GeminiObjectResult(
                korean = korean,
                english = obj.optString("english").trim(),
                nepali = obj.optString("nepali").trim(),
            ),
        )
    }

    /** The model's text: candidates[0].content.parts[0].text, or null. */
    fun extractModelText(body: String): String? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val candidates = root.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null
        val text = parts.optJSONObject(0)?.optString("text").orEmpty().trim()
        return text.ifEmpty { null }
    }

    /**
     * Pulls the JSON object out of model text: strips a surrounding
     * ```json (or ```) fence, then takes the span from the first '{'
     * to the last '}'. Null when there is no '{...}' span at all.
     */
    fun unwrapJsonObject(text: String): String? {
        var cleaned = text.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```")
            if (cleaned.startsWith("json")) cleaned = cleaned.removePrefix("json")
            cleaned = cleaned.trim()
            if (cleaned.endsWith("```")) cleaned = cleaned.removeSuffix("```").trim()
        }
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return cleaned.substring(start, end + 1)
    }

    /** Maps an HTTP status to the user-facing error kind. */
    fun mapHttpStatus(code: Int): GeminiError = when (code) {
        400, 401, 403 -> GeminiError.INVALID_KEY
        429 -> GeminiError.QUOTA_EXHAUSTED
        else -> GeminiError.UNKNOWN
    }
}

/**
 * Matches a Korean word against seed words: exact match first, then a
 * normalized match (all whitespace stripped, so "사과 주스" and
 * "사과주스" compare equal). Returns the matched seed spelling, or
 * null. Never invents a word — a miss is a miss.
 */
object KoreanWordMatcher {

    /** Strips every whitespace character (spaces, newlines, tabs). */
    fun normalize(korean: String): String =
        korean.filterNot { it.isWhitespace() }

    /**
     * [candidates] are the seed's Korean spellings to compare against.
     * Exact (trimmed) match wins; otherwise the first candidate whose
     * normalized form equals the query's normalized form wins.
     */
    fun match(candidates: List<String>, query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val exact = candidates.firstOrNull { it.trim() == trimmed }
        if (exact != null) return exact
        val normalizedQuery = normalize(trimmed)
        if (normalizedQuery.isEmpty()) return null
        return candidates.firstOrNull { normalize(it) == normalizedQuery }
    }
}

/**
 * Shows only the last 4 characters of a key ("••••" + last 4), for the
 * Settings "saved" indicator. The full key is never displayed anywhere.
 */
fun maskApiKey(key: String): String {
    val trimmed = key.trim()
    if (trimmed.length <= 4) return "••••"
    return "••••" + trimmed.takeLast(4)
}
