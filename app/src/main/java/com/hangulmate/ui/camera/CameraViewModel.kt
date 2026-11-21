package com.hangulmate.ui.camera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.MlKitException
import com.hangulmate.R
import com.hangulmate.data.gemini.GeminiVisionClient
import com.hangulmate.domain.model.LearningState
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.repository.GeminiKeyStore
import com.hangulmate.domain.repository.VocabularyRepository
import com.hangulmate.ml.ClassificationCandidate
import com.hangulmate.ml.GeminiError
import com.hangulmate.ml.GeminiIdentifyResult
import com.hangulmate.ml.GeminiObjectResult
import com.hangulmate.ml.KoreanWordMatcher
import com.hangulmate.ml.OcrResult
import com.hangulmate.ml.OcrService
import com.hangulmate.ml.Prediction
import com.hangulmate.ml.TfLiteClassifier
import com.hangulmate.ml.TranslationBreakdown
import com.hangulmate.ml.buildTranslationBreakdown
import com.hangulmate.speech.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The two camera modes. */
enum class CameraMode { TEXT, OBJECT }

/** Meaning language for the word-by-word breakdown. */
enum class MeaningLanguage { NEPALI, ENGLISH }

/** State of the Scan Text (ML Kit OCR) pipeline. */
sealed interface OcrUiState {
    data object Idle : OcrUiState

    /**
     * OCR running. [preparingModel] is true only while the recognizer
     * is still warming up (warmed up in init when the Camera screen
     * opens); a capture never downloads anything — the Korean model
     * is bundled.
     */
    data class Processing(val preparingModel: Boolean) : OcrUiState

    data class Success(val result: OcrResult) : OcrUiState

    /** ML Kit could not run on a real capture (the bundled model
     * failed to initialise). [detail] carries the underlying
     * exception (class + message) so the next report shows the cause
     * instead of only the generic banner. Manual text entry is the
     * fallback. */
    data class Unavailable(val detail: String? = null) : OcrUiState

    data class Error(val message: String) : OcrUiState
}

/** State of the Scan Object (on-device TFLite) pipeline. */
sealed interface ObjectUiState {
    data object Idle : ObjectUiState
    data object Processing : ObjectUiState

    /** At least one candidate mapped to a real dictionary word and the
     * top prediction is confident enough. */
    data class Results(val candidates: List<ClassificationCandidate>) : ObjectUiState

    /** No candidate mapped to a dictionary word, or top confidence is
     * below the threshold. [topPrediction] is the model's raw (English)
     * guess, shown as-is — never translated into a fake Korean word. */
    data class NotIdentified(
        val topPrediction: Prediction,
        val candidates: List<ClassificationCandidate>,
    ) : ObjectUiState

    /** The bundled .tflite model could not be loaded at runtime. */
    data object ModelMissing : ObjectUiState

    data class Error(val message: String) : ObjectUiState
}

/** State of the Gemini online identification pipeline. */
sealed interface GeminiUiState {
    data object Idle : GeminiUiState
    data object Loading : GeminiUiState

    /** No API key saved — the UI offers the Settings key screen. */
    data object NeedsKey : GeminiUiState

    /** A failed attempt; [messageRes] is the user-facing string. */
    data class Error(val messageRes: Int) : GeminiUiState

    /**
     * Gemini answered, but the Korean word is not in the bundled seed.
     * Shown as a labelled result card — never inserted into the
     * dictionary and never navigated anywhere.
     */
    data class NotInDictionary(val result: GeminiObjectResult) : GeminiUiState
}

data class CameraUiState(
    val mode: CameraMode = CameraMode.TEXT,
    val torchOn: Boolean = false,
    val capturedBitmap: Bitmap? = null,
    val ocrState: OcrUiState = OcrUiState.Idle,
    val extractedText: String = "",
    val breakdown: TranslationBreakdown? = null,
    val breakdownLoading: Boolean = false,
    val meaningLanguage: MeaningLanguage = MeaningLanguage.NEPALI,
    val objectState: ObjectUiState = ObjectUiState.Idle,
    val manualObjectQuery: String = "",
    val manualObjectResults: List<VocabularyWord> = emptyList(),
    val manualObjectSearching: Boolean = false,
    val ocrModelReady: Boolean = false,
    /** True while the one-time recognizer warm-up is running. */
    val ocrModelPreparing: Boolean = false,
    /** True when a real capture failed because the model could not
     * start — OCR is unavailable, manual entry stays. A failed
     * warm-up alone never sets this (see init). */
    val ocrModelFailed: Boolean = false,
    val ttsAvailable: Boolean = false,
    /** One-shot notice (a string resource id), cleared after display. */
    val noticeRes: Int? = null,
    /** Gemini online identification state (object mode only). */
    val geminiState: GeminiUiState = GeminiUiState.Idle,
    /** True when the user has saved a Gemini API key. */
    val geminiHasKey: Boolean = false,
    /**
     * One-shot navigation: the id of the seed word Gemini's answer
     * matched. The screen navigates to it once, then calls
     * [CameraViewModel.consumeGeminiNavigation] to clear it.
     */
    val geminiMatchedWordId: String? = null,
)

/**
 * Camera feature state. All camera state lives here so rotation does not
 * lose the captured photo or the scan results. Photos are processed
 * on-device only and are never uploaded anywhere.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val repository: VocabularyRepository,
    @ApplicationContext private val context: Context,
    private val ttsManager: TtsManager,
    private val geminiKeyStore: GeminiKeyStore,
    private val geminiVisionClient: GeminiVisionClient,
) : ViewModel() {

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val ocrService = OcrService()
    private val classifier: TfLiteClassifier? =
        runCatching { TfLiteClassifier(context) }.getOrNull()

    init {
        // Warm up the bundled Korean OCR model as soon as the Camera
        // screen opens, so the first capture does not stall on the
        // one-time native initialisation. A failed warm-up only marks
        // OCR unavailable — manual text entry still works.
        viewModelScope.launch {
            _state.update { it.copy(ocrModelPreparing = true) }
            val warmUpResult = withContext(Dispatchers.Default) {
                ocrService.warmUp()
            }
            _state.update {
                it.copy(
                    ocrModelPreparing = false,
                    // A failed warm-up does NOT mean OCR is unavailable:
                    // the probe can fail on its own while real captures
                    // still work, so Capture stays offered. Only a
                    // failed recognition on a real photo (see runOcr)
                    // marks the model failed.
                    ocrModelReady = warmUpResult.getOrDefault(false),
                    ocrModelFailed = false,
                )
            }
        }
        viewModelScope.launch {
            ttsManager.state.collect {
                _state.update { current -> current.copy(ttsAvailable = ttsManager.isKoreanAvailable()) }
            }
        }
        refreshGeminiHasKey()
    }

    /** Re-reads whether a Gemini key is saved (e.g. after returning from Settings). */
    fun refreshGeminiHasKey() {
        viewModelScope.launch {
            val hasKey = runCatching { geminiKeyStore.hasKey() }.getOrDefault(false)
            _state.update { it.copy(geminiHasKey = hasKey) }
        }
    }

    // -- Mode / capture -------------------------------------------------

    fun setMode(mode: CameraMode) {
        val bitmap = _state.value.capturedBitmap
        _state.update { it.copy(mode = mode) }
        // Re-run the other pipeline on the already-captured photo.
        if (bitmap != null) {
            when (mode) {
                CameraMode.TEXT -> runOcr(bitmap)
                CameraMode.OBJECT -> classifyBitmap(bitmap)
            }
        }
    }

    fun setTorch(on: Boolean) {
        _state.update { it.copy(torchOn = on) }
    }

    /** Called with a fresh photo from the camera or the gallery. */
    fun onPhotoCaptured(bitmap: Bitmap) {
        val scaled = scaleDownIfNeeded(bitmap)
        _state.update {
            it.copy(
                capturedBitmap = scaled,
                ocrState = OcrUiState.Idle,
                extractedText = "",
                breakdown = null,
                objectState = ObjectUiState.Idle,
                manualObjectResults = emptyList(),
                geminiState = GeminiUiState.Idle,
                geminiMatchedWordId = null,
            )
        }
        when (_state.value.mode) {
            CameraMode.TEXT -> runOcr(scaled)
            CameraMode.OBJECT -> classifyBitmap(scaled)
        }
    }

    fun importImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.Default) {
            val bitmap = decodeUriBitmap(uri)
            if (bitmap == null) {
                _state.update { it.copy(noticeRes = R.string.camera_import_failed) }
                return@launch
            }
            withContext(Dispatchers.Main) { onPhotoCaptured(bitmap) }
        }
    }

    fun clearCapture() {
        _state.update {
            it.copy(
                capturedBitmap = null,
                ocrState = OcrUiState.Idle,
                extractedText = "",
                breakdown = null,
                objectState = ObjectUiState.Idle,
                manualObjectResults = emptyList(),
                geminiState = GeminiUiState.Idle,
                geminiMatchedWordId = null,
            )
        }
    }

    fun onCaptureFailed() {
        _state.update { it.copy(noticeRes = R.string.camera_capture_failed) }
    }

    // -- Scan Text / OCR ------------------------------------------------

    fun runOcr(bitmap: Bitmap) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    ocrState = OcrUiState.Processing(preparingModel = false),
                    extractedText = "",
                    breakdown = null,
                )
            }
            val result = runCatching {
                withContext(Dispatchers.Default) { ocrService.recognize(bitmap) }
            }
            result.onSuccess { ocr ->
                _state.update {
                    it.copy(
                        ocrState = OcrUiState.Success(ocr),
                        extractedText = ocr.fullText,
                        ocrModelReady = true,
                        ocrModelFailed = false,
                    )
                }
                analyzeText(ocr.fullText)
            }.onFailure { error ->
                val detail = "${error.javaClass.simpleName}: ${error.message ?: "no message"}"
                Log.e("HangulMateOcr", "OCR recognition failed on capture: $detail", error)
                _state.update {
                    it.copy(
                        ocrState = if (error is MlKitException) {
                            OcrUiState.Unavailable(detail)
                        } else {
                            OcrUiState.Error(detail)
                        },
                        // Only a real-capture failure marks the model
                        // failed; a warm-up failure never does.
                        ocrModelFailed = error is MlKitException,
                    )
                }
            }
        }
    }

    fun retryOcr() {
        _state.value.capturedBitmap?.let { runOcr(it) }
    }

    /** Manual text entry path — feeds the same breakdown pipeline as OCR. */
    fun analyzeText(text: String) {
        if (text.isBlank()) {
            _state.update { it.copy(extractedText = text, breakdown = null, breakdownLoading = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(extractedText = text, breakdownLoading = true) }
            val breakdown = withContext(Dispatchers.Default) {
                buildTranslationBreakdown(text, repository)
            }
            _state.update { it.copy(breakdown = breakdown, breakdownLoading = false) }
        }
    }

    fun updateExtractedText(text: String) {
        _state.update { it.copy(extractedText = text) }
    }

    fun setMeaningLanguage(language: MeaningLanguage) {
        _state.update { it.copy(meaningLanguage = language) }
    }

    fun copyExtractedText() {
        val text = _state.value.extractedText
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("HangulMate", text))
        _state.update { it.copy(noticeRes = R.string.camera_copied) }
    }

    fun shareExtractedText() {
        val state = _state.value
        if (state.extractedText.isBlank()) return
        val shareText = buildString {
            appendLine(state.extractedText)
            state.breakdown?.let { breakdown ->
                appendLine()
                appendLine("—")
                for (token in breakdown.tokens) {
                    val word = token.word
                    if (word != null) {
                        appendLine("${token.raw} — ${word.nepali} (${word.english})")
                    } else {
                        appendLine("${token.raw} — ?")
                    }
                }
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(
            Intent.createChooser(intent, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // -- Scan Object / TFLite -------------------------------------------

    fun classifyBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _state.update { it.copy(objectState = ObjectUiState.Processing) }
            val activeClassifier = classifier
            if (activeClassifier == null || !activeClassifier.modelAvailable) {
                _state.update { it.copy(objectState = ObjectUiState.ModelMissing) }
                return@launch
            }
            val predictions = withContext(Dispatchers.Default) {
                runCatching { activeClassifier.classify(bitmap) }.getOrNull()
            }
            if (predictions.isNullOrEmpty()) {
                _state.update {
                    it.copy(objectState = ObjectUiState.Error(""))
                }
                return@launch
            }
            val candidates = withContext(Dispatchers.Default) {
                predictions.map { prediction ->
                    val korean = activeClassifier.labelMapper.koreanForLabel(prediction.label)
                    val word = korean?.let { repository.getByKorean(it) }
                    ClassificationCandidate(prediction, korean, word)
                }
            }
            val top = predictions.first()
            val anyMapped = candidates.any { it.word != null }
            _state.update {
                it.copy(
                    objectState = if (anyMapped && top.confidence >= LOW_CONFIDENCE_THRESHOLD) {
                        ObjectUiState.Results(candidates)
                    } else {
                        ObjectUiState.NotIdentified(top, candidates)
                    },
                )
            }
        }
    }

    fun retryClassification() {
        _state.value.capturedBitmap?.let { classifyBitmap(it) }
    }

    /** Manual dictionary search inside object mode (still fully on-device). */
    fun searchObjectManually(query: String) {
        _state.update { it.copy(manualObjectQuery = query) }
        if (query.isBlank()) {
            _state.update { it.copy(manualObjectResults = emptyList(), manualObjectSearching = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(manualObjectSearching = true) }
            val results = withContext(Dispatchers.Default) {
                repository.search(query).first()
            }
            _state.update { it.copy(manualObjectResults = results, manualObjectSearching = false) }
        }
    }

    // -- Online identify / Gemini ---------------------------------------
    //
    // Optional online mode: the photo is sent to Google's Gemini API
    // ONLY when the user has saved their own API key in Settings.
    // No key -> NeedsKey (UI offers Settings). Offline -> a clear
    // error with offline mode as the fallback. A matched Korean word
    // navigates to its existing WordDetail; an unmatched word shows a
    // labelled result card and is never added to the dictionary.

    /** Starts online identification for the captured photo. */
    fun identifyOnline() {
        val bitmap = _state.value.capturedBitmap ?: return
        if (_state.value.geminiState is GeminiUiState.Loading) return
        viewModelScope.launch {
            _state.update { it.copy(geminiState = GeminiUiState.Loading) }
            val apiKey = runCatching { geminiKeyStore.getKey() }.getOrNull()
            if (apiKey.isNullOrBlank()) {
                _state.update {
                    it.copy(geminiState = GeminiUiState.NeedsKey, geminiHasKey = false)
                }
                return@launch
            }
            if (!isOnline()) {
                _state.update {
                    it.copy(geminiState = GeminiUiState.Error(R.string.gemini_error_no_internet))
                }
                return@launch
            }
            val jpegBytes = withContext(Dispatchers.Default) { toGeminiJpeg(bitmap) }
            if (jpegBytes == null) {
                _state.update {
                    it.copy(geminiState = GeminiUiState.Error(R.string.gemini_error_unknown))
                }
                return@launch
            }
            when (val outcome = geminiVisionClient.identify(apiKey, jpegBytes)) {
                is GeminiIdentifyResult.Success -> onGeminiResult(outcome.result)
                is GeminiIdentifyResult.Failure -> _state.update {
                    it.copy(geminiState = GeminiUiState.Error(outcome.error.toStringRes()))
                }
            }
        }
    }

    fun retryGemini() {
        identifyOnline()
    }

    fun clearGeminiResult() {
        _state.update { it.copy(geminiState = GeminiUiState.Idle, geminiMatchedWordId = null) }
    }

    /** Clears the one-shot navigation after the screen has consumed it. */
    fun consumeGeminiNavigation() {
        _state.update { it.copy(geminiMatchedWordId = null) }
    }

    private suspend fun onGeminiResult(result: GeminiObjectResult) {
        val word = withContext(Dispatchers.Default) { findWordByKorean(result.korean) }
        if (word != null) {
            _state.update {
                it.copy(
                    geminiState = GeminiUiState.Idle,
                    geminiMatchedWordId = word.id,
                )
            }
        } else {
            _state.update { it.copy(geminiState = GeminiUiState.NotInDictionary(result)) }
        }
    }

    /**
     * Seed lookup for a Gemini answer: exact match first
     * ([VocabularyRepository.getByKorean]), then a normalized match —
     * whitespace stripped on both sides — over the dictionary search
     * results for the query. Null when the word is not in the seed.
     */
    private suspend fun findWordByKorean(korean: String): VocabularyWord? {
        val trimmed = korean.trim()
        if (trimmed.isEmpty()) return null
        repository.getByKorean(trimmed)?.let { return it }
        for (query in listOf(trimmed, KoreanWordMatcher.normalize(trimmed))) {
            if (query.isEmpty()) continue
            val candidates = runCatching { repository.search(query).first() }.getOrNull()
                ?: continue
            val spellings = candidates.map { it.korean }
            val matched = KoreanWordMatcher.match(spellings, trimmed) ?: continue
            return candidates.firstOrNull { it.korean == matched }
        }
        return null
    }

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Downscales the photo to max 1024px on the long edge and encodes
     * it as JPEG (quality 85) for the Gemini request — keeps the
     * upload small. Null when the bitmap cannot be encoded.
     */
    private fun toGeminiJpeg(bitmap: Bitmap): ByteArray? = runCatching {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        val scaled = if (maxSide <= MAX_GEMINI_SIDE_PX) {
            bitmap
        } else {
            val scale = MAX_GEMINI_SIDE_PX.toFloat() / maxSide
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true,
            )
        }
        val output = ByteArrayOutputStream()
        if (!scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)) return null
        output.toByteArray()
    }.getOrNull()

    // -- Word actions ----------------------------------------------------

    /** Adds the word to the learn list (LEARNING state). */
    fun saveToLearnList(wordId: String) {
        viewModelScope.launch {
            repository.setLearningState(wordId, LearningState.LEARNING)
            _state.update { it.copy(noticeRes = R.string.camera_saved_to_learn) }
            refreshBreakdown()
        }
    }

    /** Toggles the favourite star, then refreshes the shown words. */
    fun toggleFavorite(wordId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(wordId)
            refreshBreakdown()
            refreshObjectCandidates()
        }
    }

    fun speak(korean: String) {
        if (ttsManager.isKoreanAvailable()) {
            ttsManager.speak(korean)
        }
    }

    fun clearNotice() {
        _state.update { it.copy(noticeRes = null) }
    }

    // -- Helpers ----------------------------------------------------------

    private fun refreshBreakdown() {
        val text = _state.value.extractedText
        if (text.isNotBlank()) analyzeText(text)
    }

    private suspend fun refreshObjectCandidates() {
        val current = _state.value.objectState
        val candidates = when (current) {
            is ObjectUiState.Results -> current.candidates
            is ObjectUiState.NotIdentified -> current.candidates
            else -> return
        }
        val refreshed = withContext(Dispatchers.Default) {
            candidates.map { candidate ->
                val word = candidate.korean?.let { repository.getByKorean(it) }
                candidate.copy(word = word)
            }
        }
        _state.update {
            when (val state = it.objectState) {
                is ObjectUiState.Results ->
                    it.copy(objectState = ObjectUiState.Results(refreshed))
                is ObjectUiState.NotIdentified ->
                    it.copy(objectState = ObjectUiState.NotIdentified(state.topPrediction, refreshed))
                else -> it
            }
        }
    }

    private fun decodeUriBitmap(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
    }.getOrNull()?.let { scaleDownIfNeeded(it) }

    /** Caps very large photos so OCR/classification stay fast and safe. */
    private fun scaleDownIfNeeded(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_PHOTO_SIDE_PX) return bitmap
        val scale = MAX_PHOTO_SIDE_PX.toFloat() / maxSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
    }

    override fun onCleared() {
        ocrService.close()
        classifier?.close()
        super.onCleared()
    }

    companion object {
        private const val MAX_PHOTO_SIDE_PX = 1600
        private const val MAX_GEMINI_SIDE_PX = 1024
        private const val LOW_CONFIDENCE_THRESHOLD = 0.25f
    }
}

private fun GeminiError.toStringRes(): Int = when (this) {
    GeminiError.NO_INTERNET -> R.string.gemini_error_no_internet
    GeminiError.INVALID_KEY -> R.string.gemini_error_invalid_key
    GeminiError.QUOTA_EXHAUSTED -> R.string.gemini_error_quota
    GeminiError.EMPTY_RESPONSE -> R.string.gemini_error_empty
    GeminiError.TIMEOUT -> R.string.gemini_error_timeout
    GeminiError.UNKNOWN -> R.string.gemini_error_unknown
}
