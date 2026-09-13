package com.hiosdra.hreader.adapter.observability

import android.util.Log
import com.hiosdra.hreader.core.application.observability.NetworkMetricsCollector
import com.hiosdra.hreader.core.application.observability.NetworkMetricsSnapshot
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.observability.SyncPerformanceRecord
import com.hiosdra.hreader.core.application.observability.ArticleSyncStats
import com.hiosdra.hreader.core.application.port.out.PerformancePreferences
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences

class SyncPerformanceLogger(
    private val preferencesManager: PerformancePreferences,
    private val syncPreferences: SyncPreferences,
    private val networkMetrics: NetworkMetricsCollector
) : SyncPerformanceTracker {
    companion object {
        private const val TAG = "SyncPerformance"
    }
    
    override suspend fun <T> measureSyncTime(operation: SyncPerformanceOperation, block: suspend () -> T): T {
        val startTime = System.nanoTime()
        val metricsWindow = networkMetrics.startWindow()
        return try {
            block()
        } finally {
            val duration = (System.nanoTime() - startTime) / 1_000_000L
            val metrics = networkMetrics.finishWindow(metricsWindow)
            val syncMode = syncPreferences.getSyncMode()
            val networkRecord = metrics.takeIf { it.requestCount > 0 }

            addRecord(
                operationName = operation.key,
                durationMs = duration,
                syncMode = syncMode.name,
                networkMetrics = networkRecord
            )
            Log.i(TAG, "${operation.key} mode=${syncMode.name} completed in ${duration}ms")
            if (networkRecord != null) {
                Log.i(
                    TAG,
                    "${operation.key} network requests=${networkRecord.requestCount}, " +
                        "errors=${networkRecord.errorCount}, bytes=${networkRecord.totalBytes}, " +
                        "throughputBytesPerSecond=${networkRecord.throughputBytesPerSecond}, " +
                        "averageResponseMs=${networkRecord.averageResponseMs}, " +
                        "maxConcurrentRequests=${networkRecord.maxConcurrentRequests}"
                )
            }
        }
    }
    
    override fun logBatchInfo(batchSize: Int, totalArticles: Int) {
        val batches = (totalArticles + batchSize - 1) / batchSize
        Log.i(TAG, "Processing $totalArticles articles in $batches batches of $batchSize each")
        
        addRecord(
            operationName = SyncPerformanceOperation.BATCH_PROCESSING.key,
            batchSize = batchSize,
            totalArticles = totalArticles
        )
    }

    override fun logArticleSyncStats(stats: ArticleSyncStats) {
        Log.i(
            TAG,
            "Reconciled ${stats.fetched} articles: " +
                "${stats.unchanged} unchanged, ${stats.inserted} inserted, ${stats.updated} updated"
        )
        addRecord(
            operationName = SyncPerformanceOperation.ARTICLE_RECONCILIATION.key,
            totalArticles = stats.fetched,
            unchangedArticles = stats.unchanged,
            insertedArticles = stats.inserted,
            updatedArticles = stats.updated
        )
    }
    
    override fun logSyncMode(isIncremental: Boolean, lastSyncTime: Long?) {
        val hoursAgo = if (isIncremental && lastSyncTime != null) {
            (System.currentTimeMillis() - lastSyncTime) / (60 * 60 * 1000)
        } else null
        
        val operation = if (isIncremental) {
            SyncPerformanceOperation.INCREMENTAL_SYNC
        } else {
            SyncPerformanceOperation.FULL_SYNC
        }
        val syncType = if (isIncremental) "incremental" else "full"
        val message = if (isIncremental && lastSyncTime != null) {
            "Using $syncType sync (last sync: ${hoursAgo}h ago)"
        } else {
            "Using $syncType sync"
        }
        Log.i(TAG, message)
        
        addRecord(
            operationName = operation.key,
            isIncremental = isIncremental,
            lastSyncHoursAgo = hoursAgo
        )
    }

    private fun addRecord(
        operationName: String,
        durationMs: Long = 0,
        batchSize: Int? = null,
        totalArticles: Int? = null,
        unchangedArticles: Int? = null,
        insertedArticles: Int? = null,
        updatedArticles: Int? = null,
        isIncremental: Boolean? = null,
        lastSyncHoursAgo: Long? = null,
        syncMode: String? = null,
        networkMetrics: NetworkMetricsSnapshot? = null
    ) {
        val record = SyncPerformanceRecord(
            timestamp = System.currentTimeMillis(),
            operationName = operationName,
            durationMs = durationMs,
            batchSize = batchSize,
            totalArticles = totalArticles,
            unchangedArticles = unchangedArticles,
            insertedArticles = insertedArticles,
            updatedArticles = updatedArticles,
            isIncremental = isIncremental,
            lastSyncHoursAgo = lastSyncHoursAgo,
            syncMode = syncMode,
            requestCount = networkMetrics?.requestCount,
            errorCount = networkMetrics?.errorCount,
            totalBytes = networkMetrics?.totalBytes,
            throughputBytesPerSecond = networkMetrics?.throughputBytesPerSecond,
            averageResponseMs = networkMetrics?.averageResponseMs,
            maxConcurrentRequests = networkMetrics?.maxConcurrentRequests
        )
        preferencesManager.addSyncPerformanceRecord(record)
    }
}
