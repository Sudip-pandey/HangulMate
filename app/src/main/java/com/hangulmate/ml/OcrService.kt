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
 * The Korean model is BUNDLED with the app
 * (`com.google.mlkit:text-recognition-korean`) — it is statically linked
 * at build time, so there is no Play-services download and no internet
 * is needed. [recognize] therefore throws [MlKitException] only when
 * recognition itself fails — callers must surface that honestly and
 * offer manual text entry instead of pretending OCR worked. Nothing is
 * uploaded; recognition runs on-device. Call [close] when done.
 */
class OcrService {

    private val recognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build(),
    )

    /**
     * Reads Korean text from [bitmap]. Returns the full text plus blocks
     * and lines with bounding boxes in image pixel coordinates (for the
     * overlay). Throws [MlKitException] when recognition fails — most
     * commonly because the bundled model failed to initialise.
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

    /**
     * Warms up the bundled model by recognising a 1x1 blank image
     * (blank input reads as empty text). Returns true when the
     * recognizer answered. Run once when the Camera screen first opens
     * so the one-time native initialisation happens before the first
     * real capture instead of stalling it.
     */
    suspend fun warmUp(): Boolean = runCatching {
        val blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            recognize(blank)
            true
        } finally {
            blank.recycle()
        }
    }.getOrDefault(false)

    fun close() {
        recognizer.close()
    }
}
