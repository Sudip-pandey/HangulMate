package com.hangulmate.ui.camera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.MlKitException
import com.hangulmate.R
import com.hangulmate.domain.model.LearningState
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.repository.VocabularyRepository
import com.hangulmate.ml.ClassificationCandidate
import com.hangulmate.ml.OcrResult
import com.hangulmate.ml.OcrService
import com.hangulmate.ml.Prediction
import com.hangulmate.ml.TfLiteClassifier
import com.hangulmate.ml.TranslationBreakdown
import com.hangulmate.ml.buildTranslationBreakdown
import com.hangulmate.speech.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

    /** ML Kit could not run (the bundled model failed to initialise).
     * Manual text entry is the fallback. */
    data object Unavailable : OcrUiState

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
    /** True when the warm-up failed — OCR is unavailable, manual entry stays. */
    val ocrModelFailed: Boolean = false,
    val ttsAvailable: Boolean = false,
    /** One-shot notice (a string resource id), cleared after display. */
    val noticeRes: Int? = null,
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
            val ready = withContext(Dispatchers.Default) {
                ocrService.warmUp()
            }
            _state.update {
                it.copy(
                    ocrModelPreparing = false,
                    ocrModelReady = ready,
                    ocrModelFailed = !ready,
                )
            }
        }
        viewModelScope.launch {
            ttsManager.state.collect {
                _state.update { current -> current.copy(ttsAvailable = ttsManager.isKoreanAvailable()) }
            }
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
                    )
                }
                analyzeText(ocr.fullText)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        ocrState = if (error is MlKitException) {
                            OcrUiState.Unavailable
                        } else {
                            OcrUiState.Error(error.message ?: "")
                        },
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
        private const val LOW_CONFIDENCE_THRESHOLD = 0.25f
    }
}
