package com.hangulmate.data.gemini

import android.util.Base64
import com.hangulmate.ml.GeminiError
import com.hangulmate.ml.GeminiIdentifyResult
import com.hangulmate.ml.GeminiOnline
import com.hangulmate.ml.GeminiRequest
import com.hangulmate.ml.GeminiSearch
import com.hangulmate.ml.GeminiWordSearchResult
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Calls Google's Gemini API to identify the main object in a photo and
 * to look a word up in the online dictionary fallback.
 *
 * Plain [HttpURLConnection] on the IO dispatcher — the project has no
 * OkHttp/Retrofit, and this feature adds no networking dependency.
 * 30-second connect/read timeouts. The API key is the user's own,
 * passed in by the caller (read from [com.hangulmate.domain.repository.GeminiKeyStore]
 * at call time); it is never stored or logged here — it only appears
 * in the request URL's `key` query parameter, per the API's
 * documented placement.
 *
 * Never throws: every failure maps to a typed Failure result with a
 * distinct [GeminiError] the UI turns into a simple message.
 */
class GeminiVisionClient @Inject constructor() : GeminiWordSearchClient {

    /**
     * Identifies the main object in [jpegBytes] (a JPEG photo, already
     * downscaled by the caller to max 1024px on the long edge).
     */
    suspend fun identify(apiKey: String, jpegBytes: ByteArray): GeminiIdentifyResult =
        withContext(Dispatchers.IO) {
            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            val requestBody = GeminiRequest.buildRequestJson(base64)
            runGeminiCall(
                apiKey = apiKey,
                requestBody = requestBody,
                parse = GeminiRequest::parseResponse,
                failure = GeminiIdentifyResult::Failure,
            )
        }

    /**
     * Looks [query] up online (see [GeminiSearch] for the prompt and
     * response shape). Text only — no photo is sent.
     */
    override suspend fun searchWord(apiKey: String, query: String): GeminiWordSearchResult =
        withContext(Dispatchers.IO) {
            runGeminiCall(
                apiKey = apiKey,
                requestBody = GeminiSearch.buildRequestJson(query),
                parse = GeminiSearch::parseWordResponse,
                failure = GeminiWordSearchResult::Failure,
            )
        }

    /**
     * One POST to the Gemini API, with the shared transport-error
     * mapping: HTTP auth errors become [GeminiError.INVALID_KEY],
     * 429 becomes [GeminiError.QUOTA_EXHAUSTED], timeouts and
     * unreachable hosts become [GeminiError.TIMEOUT] /
     * [GeminiError.NO_INTERNET]. [parse] turns a 200 body into the
     * caller's result type; it never throws for a bad body (parsers
     * return their own Failure), so a throw here is always a transport
     * or HTTP problem.
     */
    private fun <T> runGeminiCall(
        apiKey: String,
        requestBody: String,
        parse: (String) -> T,
        failure: (GeminiError) -> T,
    ): T {
        return try {
            parse(postJson(apiKey, requestBody))
        } catch (e: HttpStatusException) {
            failure(GeminiRequest.mapHttpStatus(e.code))
        } catch (e: SocketTimeoutException) {
            failure(GeminiError.TIMEOUT)
        } catch (e: UnknownHostException) {
            failure(GeminiError.NO_INTERNET)
        } catch (e: ConnectException) {
            failure(GeminiError.NO_INTERNET)
        } catch (e: IOException) {
            failure(GeminiError.NO_INTERNET)
        } catch (e: Exception) {
            failure(GeminiError.UNKNOWN)
        }
    }

    /** POSTs [requestBody] and returns the 200 response body as text. */
    private fun postJson(apiKey: String, requestBody: String): String {
        val connection = URL(GeminiOnline.buildUrl(apiKey)).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.outputStream.use { out ->
                out.write(requestBody.toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                // Drain the error body so the connection can be reused;
                // its contents are never shown (they may echo the key).
                runCatching { connection.errorStream?.use { it.readBytes() } }
                throw HttpStatusException(code)
            }
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    /** Non-200 HTTP status from the Gemini API (thrown only inside [postJson]). */
    private class HttpStatusException(val code: Int) : IOException("Gemini HTTP $code")

    companion object {
        private const val TIMEOUT_MS = 30_000
    }
}
