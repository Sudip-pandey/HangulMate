package com.hangulmate.ml

import android.graphics.Bitmap
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device Korean text recognition (ML Kit, Korean script).
 *
 * The Korean recognition model is NOT bundled in the APK: it downloads
 * once through Google Play services on first use (needs internet once,
 * then works offline). [recognize] therefore throws [MlKitException] when
 * the model cannot be downloaded or Play services is unavailable — callers
 * must surface that honestly and offer manual text entry instead of
 * pretending OCR worked. Nothing is uploaded; recognition runs on-device.
 * Call [close] when done.
 */
class OcrService {

    private val recognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build(),
    )

    /**
     * Reads Korean text from [bitmap]. Returns the full text plus blocks
     * and lines with bounding boxes in image pixel coordinates (for the
     * overlay). Throws [MlKitException] when recognition fails — most
     * commonly because the one-time Korean model download failed or
     * Play services is unavailable.
     */
    suspend fun recognize(bitmap: Bitmap): OcrResult =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    val blocks = text.textBlocks.map { block ->
                        OcrBlock(
                            text = block.text,
                            boundingBox = block.boundingBox,
                            words = block.lines.map { line ->
                                OcrWord(
                                    text = line.text,
                                    boundingBox = line.boundingBox,
                                )
                            },
                        )
                    }
                    continuation.resume(OcrResult(fullText = text.text, blocks = blocks))
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }

    fun close() {
        recognizer.close()
    }
}
