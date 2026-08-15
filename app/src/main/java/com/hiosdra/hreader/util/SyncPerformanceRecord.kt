package com.hiosdra.hreader.util

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SyncPerformanceRecord(
    val timestamp: Long,
    val operationName: String,
    val durationMs: Long,
    val batchSize: Int? = null,
    val totalArticles: Int? = null,
    val isIncremental: Boolean? = null,
    val lastSyncHoursAgo: Long? = null
) {
}
