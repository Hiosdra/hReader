package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt

internal const val READING_POSITION_COMPLETE_THRESHOLD = 0.98f

internal fun articleScrollProgress(value: Int, maxValue: Int): Float =
    if (maxValue <= 0) 0f else (value.toFloat() / maxValue).coerceIn(0f, 1f)

internal fun articleScrollOffset(progress: Float, maxValue: Int): Int =
    (progress.coerceIn(0f, 1f) * maxValue.coerceAtLeast(0)).roundToInt()

internal fun articleListScrollProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    totalItemsCount: Int,
    averageItemSizePx: Int,
    viewportSizePx: Int,
    isAtEnd: Boolean
): Float {
    if (totalItemsCount <= 0 || averageItemSizePx <= 0 || viewportSizePx <= 0) return 0f
    if (isAtEnd) return 1f

    val estimatedContentSizePx = totalItemsCount.toLong() * averageItemSizePx
    val maxScrollPx = (estimatedContentSizePx - viewportSizePx).coerceAtLeast(1L)
    val currentScrollPx = firstVisibleItemIndex.toLong() * averageItemSizePx +
        firstVisibleItemScrollOffset.coerceAtLeast(0)
    return (currentScrollPx.toFloat() / maxScrollPx.toFloat()).coerceIn(0f, 1f)
}

@Composable
internal fun ScrollProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val progressColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(normalizedProgress, 0f..1f)
        }
    ) {
        val cornerRadius = CornerRadius(size.width / 2f, size.width / 2f)
        drawRoundRect(
            color = trackColor,
            cornerRadius = cornerRadius
        )
        if (normalizedProgress > 0f) {
            drawRoundRect(
                color = progressColor,
                size = Size(size.width, size.height * normalizedProgress),
                cornerRadius = cornerRadius
            )
        }
    }
}
