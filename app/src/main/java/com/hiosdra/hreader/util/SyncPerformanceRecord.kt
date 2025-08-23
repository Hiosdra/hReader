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
    fun getFormattedTimestamp(): String {
        val date = java.text.SimpleDateFormat("MMM dd, HH:mm:ss", java.util.Locale.getDefault())
        return date.format(java.util.Date(timestamp))
    }
    
    fun getFormattedDuration(): String {
        return when {
            durationMs < 1000 -> "${durationMs}ms"
            durationMs < 60000 -> String.format("%.1fs", durationMs / 1000.0)
            else -> String.format("%.1fm", durationMs / 60000.0)
        }
    }
}