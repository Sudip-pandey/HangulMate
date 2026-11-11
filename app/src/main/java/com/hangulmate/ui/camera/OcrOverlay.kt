package com.hangulmate.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hangulmate.ml.OcrBlock

/**
 * Draws ML Kit OCR bounding boxes over the result image.
 *
 * [imageWidth]/[imageHeight] are the pixel dimensions of the image the
 * boxes were measured on; the boxes are scaled to the composable's size,
 * so this must be laid out at the same aspect ratio as the image.
 * Blocks are drawn with the primary colour, individual lines with the
 * tertiary colour. A null bounding box is skipped — never drawn as a
 * fake rectangle.
 */
@Composable
fun OcrOverlay(
    blocks: List<OcrBlock>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
) {
    val blockColor = MaterialTheme.colorScheme.primary
    val wordColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas
        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight
        for (block in blocks) {
            for (word in block.words) {
                val rect = word.boundingBox ?: continue
                drawRect(
                    color = wordColor,
                    topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                    size = Size(rect.width() * scaleX, rect.height() * scaleY),
                    style = Stroke(width = 2f),
                )
            }
            val rect = block.boundingBox ?: continue
            drawRect(
                color = blockColor,
                topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                size = Size(rect.width() * scaleX, rect.height() * scaleY),
                style = Stroke(width = 3f),
            )
        }
    }
}
