package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.observability.ArticleSyncStats

interface SyncPerformanceTracker {
    suspend fun <T> measureSyncTime(operation: SyncPerformanceOperation, block: suspend () -> T): T
    fun logBatchInfo(batchSize: Int, totalArticles: Int)
    fun logArticleSyncStats(stats: ArticleSyncStats)
    fun logSyncMode(isIncremental: Boolean, lastSyncTime: Long? = null)
}
