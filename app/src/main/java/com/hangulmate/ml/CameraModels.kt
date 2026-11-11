package com.hangulmate.ml

import android.graphics.Rect
import com.hangulmate.domain.model.VocabularyWord

/**
 * One OCR text block (a paragraph-sized region) from ML Kit, with its
 * bounding box in image pixel coordinates and the lines inside it.
 */
data class OcrBlock(
    val text: String,
    val boundingBox: Rect?,
    val words: List<OcrWord> = emptyList(),
)

/** One OCR line/word with its bounding box in image pixel coordinates. */
data class OcrWord(
    val text: String,
    val boundingBox: Rect?,
)

/** Full result of one ML Kit Korean text-recognition pass. */
data class OcrResult(
    val fullText: String,
    val blocks: List<OcrBlock> = emptyList(),
)

/** One MobileNet prediction: an ImageNet class index, its label, and a 0–1 confidence. */
data class Prediction(
    val labelIndex: Int,
    val label: String,
    val confidence: Float,
)

/**
 * One object-recognition candidate: the model prediction plus the Korean
 * word it maps to (via [LabelMapper]) and the vocabulary entry for that
 * word, when both exist. A null [korean]/[word] means the label has no
 * verified Korean mapping — never a guessed word.
 */
data class ClassificationCandidate(
    val prediction: Prediction,
    val korean: String?,
    val word: VocabularyWord?,
)

/** Raw top-5 output of one [TfLiteClassifier] run. */
data class ClassificationResult(
    val predictions: List<Prediction>,
)
