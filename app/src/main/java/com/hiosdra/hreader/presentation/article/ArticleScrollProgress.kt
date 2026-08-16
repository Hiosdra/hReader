package com.hiosdra.hreader.presentation.article

import kotlin.math.roundToInt

internal const val READING_POSITION_COMPLETE_THRESHOLD = 0.98f

internal fun articleScrollProgress(value: Int, maxValue: Int): Float =
    if (maxValue <= 0) 0f else (value.toFloat() / maxValue).coerceIn(0f, 1f)

internal fun articleScrollOffset(progress: Float, maxValue: Int): Int =
    (progress.coerceIn(0f, 1f) * maxValue.coerceAtLeast(0)).roundToInt()
