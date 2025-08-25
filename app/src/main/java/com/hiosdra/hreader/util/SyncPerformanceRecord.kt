package com.hiosdra.hreader.util

import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Locale

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
    fun getFormattedTimestamp(): String =
        SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
            .format(java.util.Date(timestamp))

    fun getFormattedDuration(): String = when {
        durationMs < 1000 -> "${durationMs}ms"
        durationMs < 60000 -> String.format("%.1fs", durationMs / 1000.0)
        else -> String.format("%.1fm", durationMs / 60000.0)
    }
}
