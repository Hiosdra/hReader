package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal const val READING_POSITION_COMPLETE_THRESHOLD = 0.98f

internal fun articleScrollProgress(value: Int, maxValue: Int): Float =
    if (maxValue <= 0) 0f else (value.toFloat() / maxValue).coerceIn(0f, 1f)

internal fun articleScrollOffset(progress: Float, maxValue: Int): Int =
    (progress.coerceIn(0f, 1f) * maxValue.coerceAtLeast(0)).roundToInt()

internal data class VerticalScrollbarMetrics(
    val thumbFraction: Float,
    val positionFraction: Float
)

internal fun verticalScrollbarMetrics(
    viewportSizePx: Int,
    contentSizePx: Int,
    scrollOffsetPx: Int,
    isAtEnd: Boolean = false
): VerticalScrollbarMetrics? {
    if (viewportSizePx <= 0 || contentSizePx <= viewportSizePx) return null

    val maxScrollPx = contentSizePx - viewportSizePx
    val currentScrollPx = if (isAtEnd) maxScrollPx else scrollOffsetPx
    return VerticalScrollbarMetrics(
        thumbFraction = (viewportSizePx.toFloat() / contentSizePx).coerceIn(0f, 1f),
        positionFraction = (currentScrollPx.toFloat() / maxScrollPx).coerceIn(0f, 1f)
    )
}

internal fun listScrollbarMetrics(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    totalItemsCount: Int,
    averageItemSizePx: Int,
    viewportSizePx: Int,
    isAtEnd: Boolean
): VerticalScrollbarMetrics? {
    if (totalItemsCount <= 0 || averageItemSizePx <= 0 || viewportSizePx <= 0) return null

    val estimatedContentSizePx = (totalItemsCount.toLong() * averageItemSizePx)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    val currentScrollPx = (firstVisibleItemIndex.coerceAtLeast(0).toLong() * averageItemSizePx +
        firstVisibleItemScrollOffset.coerceAtLeast(0))
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    return verticalScrollbarMetrics(
        viewportSizePx = viewportSizePx,
        contentSizePx = estimatedContentSizePx,
        scrollOffsetPx = currentScrollPx,
        isAtEnd = isAtEnd
    )
}

@Composable
internal fun VerticalScrollbar(
    metrics: VerticalScrollbarMetrics?,
    modifier: Modifier = Modifier
) {
    if (metrics == null) return

    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(12.dp)
            .padding(vertical = 4.dp)
    ) {
        val trackWidth = 2.dp.toPx()
        val thumbWidth = 4.dp.toPx()
        val thumbHeight = minOf(
            size.height,
            maxOf(32.dp.toPx(), size.height * metrics.thumbFraction.coerceIn(0f, 1f))
        )
        val thumbTop = (size.height - thumbHeight).coerceAtLeast(0f) *
            metrics.positionFraction.coerceIn(0f, 1f)
        val centerX = size.width - thumbWidth / 2f

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(centerX - trackWidth / 2f, 0f),
            size = Size(trackWidth, size.height),
            cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(centerX - thumbWidth / 2f, thumbTop),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
        )
    }
}
