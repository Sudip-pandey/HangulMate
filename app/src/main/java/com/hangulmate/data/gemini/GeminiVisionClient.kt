package com.hangulmate.data.gemini

import android.util.Base64
import com.hangulmate.ml.GeminiError
import com.hangulmate.ml.GeminiIdentifyResult
import com.hangulmate.ml.GeminiOnline
import com.hangulmate.ml.GeminiRequest
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
 * Calls Google's Gemini API to identify the main object in a photo.
 *
 * Plain [HttpURLConnection] on the IO dispatcher — the project has no
 * OkHttp/Retrofit, and this feature adds no networking dependency.
 * 30-second connect/read timeouts. The API key is the user's own,
 * passed in by the caller (read from [com.hangulmate.domain.repository.GeminiKeyStore]
 * at call time); it is never stored or logged here — it only appears
 * in the request URL's `key` query parameter, per the API's
 * documented placement.
 *
 * Never throws: every failure maps to a [GeminiIdentifyResult.Failure]
 * with a distinct [GeminiError] the UI turns into a simple message.
 */
class GeminiVisionClient @Inject constructor() {

    /**
     * Identifies the main object in [jpegBytes] (a JPEG photo, already
     * downscaled by the caller to max 1024px on the long edge).
     */
    suspend fun identify(apiKey: String, jpegBytes: ByteArray): GeminiIdentifyResult =
        withContext(Dispatchers.IO) {
            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            val requestBody = GeminiRequest.buildRequestJson(base64)
            runCatching { post(apiKey, requestBody) }
                .getOrElse { error -> GeminiIdentifyResult.Failure(mapTransportError(error)) }
        }

    private fun post(apiKey: String, requestBody: String): GeminiIdentifyResult {
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
                return GeminiIdentifyResult.Failure(GeminiRequest.mapHttpStatus(code))
            }
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            return GeminiRequest.parseResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun mapTransportError(error: Throwable): GeminiError = when (error) {
        is SocketTimeoutException -> GeminiError.TIMEOUT
        is UnknownHostException, is ConnectException -> GeminiError.NO_INTERNET
        is IOException -> GeminiError.NO_INTERNET
        else -> GeminiError.UNKNOWN
    }

    companion object {
        private const val TIMEOUT_MS = 30_000
    }
}
