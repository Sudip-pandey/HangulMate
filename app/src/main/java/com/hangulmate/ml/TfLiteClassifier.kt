package com.hangulmate.ml

import android.content.Context
import android.graphics.Bitmap
import java.nio.channels.FileChannel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

/**
 * On-device image classification with the bundled quantized MobileNetV1
 * (`assets/ml/mobilenet_v1_1.0_224_quant.tflite`, uint8 in/out, 1001 classes).
 *
 * Input is resized to 224×224 with the TFLite support library; output is
 * the top-5 predictions (index 0, the `background` class, is skipped),
 * with English labels resolved through [LabelMapper].
 * Nothing leaves the device. When the model file cannot be loaded,
 * [modelAvailable] is false and [classify] returns an empty list — the
 * caller must show the honest "model not installed" state, never fake
 * results. Call [close] when done.
 */
class TfLiteClassifier(context: Context) {

    companion object {
        private const val MODEL_PATH = "ml/mobilenet_v1_1.0_224_quant.tflite"
        private const val INPUT_SIZE = 224
        private const val LABEL_COUNT = 1001
        private const val TOP_K = 5
    }

    private val interpreter: Interpreter? = runCatching {
        val assetFd = context.assets.openFd(MODEL_PATH)
        val buffer = assetFd.createInputStream().channel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFd.startOffset,
            assetFd.declaredLength,
        )
        Interpreter(buffer, Interpreter.Options().setNumThreads(4))
    }.getOrNull()

    /** True only when the bundled model actually loaded. */
    val modelAvailable: Boolean get() = interpreter != null

    /** Labels + verified Korean aliases, loaded from the bundled assets. */
    val labelMapper: LabelMapper = LabelMapper(context)

    private val imageProcessor: ImageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    /**
     * Classifies [bitmap] and returns the top-[TOP_K] predictions sorted by
     * confidence (0–1), highest first. Returns an empty list when the
     * model is unavailable.
     */
    fun classify(bitmap: Bitmap): List<Prediction> {
        val activeInterpreter = interpreter ?: return emptyList()
        val tensorImage = TensorImage(DataType.UINT8)
        tensorImage.load(bitmap)
        val processed = imageProcessor.process(tensorImage)

        val output = Array(1) { ByteArray(LABEL_COUNT) }
        activeInterpreter.run(processed.buffer, output)
        val scores = output[0]

        return scores.indices
            .asSequence()
            .filter { it != 0 } // skip the "background" class
            .sortedByDescending { scores[it].toInt() and 0xFF }
            .take(TOP_K)
            .map { index ->
                Prediction(
                    labelIndex = index,
                    label = labelMapper.labelForIndex(index) ?: "class $index",
                    confidence = (scores[index].toInt() and 0xFF) / 255f,
                )
            }
            .toList()
    }

    fun close() {
        interpreter?.close()
    }
}
