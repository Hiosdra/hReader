package com.hiosdra.hreader.presentation.travel

import com.hiosdra.hreader.core.domain.model.OfflineReadiness
import kotlin.math.ceil

private const val ESTIMATED_CONTENT_BYTES = 64L * 1024
private const val ESTIMATED_IMAGE_BYTES = 160L * 1024
private const val ESTIMATED_PAGE_BYTES = 512L * 1024
private const val BYTES_PER_KILOBYTE = 1024L
private const val BYTES_PER_MEGABYTE = 1024L * 1024

internal data class TravelModeEstimate(
    val networkBytes: Long,
    val storageBytes: Long
)

internal fun estimateTravelMode(
    readiness: OfflineReadiness,
    includeImages: Boolean,
    includeFullPages: Boolean
): TravelModeEstimate {
    val missingContent = if (includeFullPages) {
        readiness.missingFullContentCount
    } else {
        readiness.missingContentCount
    }
    val missingImages = if (includeImages) readiness.missingImageCount else 0
    val missingPages = if (includeFullPages) readiness.missingFullPageCount else 0
    val contentBytes = missingContent * ESTIMATED_CONTENT_BYTES
    val imageBytes = missingImages * ESTIMATED_IMAGE_BYTES
    val pageBytes = missingPages * ESTIMATED_PAGE_BYTES
    val totalBytes = contentBytes + imageBytes + pageBytes
    return TravelModeEstimate(networkBytes = totalBytes, storageBytes = totalBytes)
}

internal fun formatTravelModeSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < BYTES_PER_MEGABYTE -> "${ceil(bytes / BYTES_PER_KILOBYTE.toDouble()).toLong()} KB"
    else -> "${ceil(bytes / BYTES_PER_MEGABYTE.toDouble()).toLong()} MB"
}
