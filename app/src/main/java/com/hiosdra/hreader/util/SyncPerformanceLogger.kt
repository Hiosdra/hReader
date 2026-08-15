package com.hiosdra.hreader.util

import android.util.Log
import com.hiosdra.hreader.data.preferences.PreferencesManager

class SyncPerformanceLogger(private val preferencesManager: PreferencesManager) {
    companion object {
        private const val TAG = "SyncPerformance"
    }
    
    suspend fun <T> measureSyncTime(operation: SyncPerformanceOperation, block: suspend () -> T): T {
        val startTime = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - startTime
        
        addRecord(operationName = operation.key, durationMs = duration)
        Log.i(TAG, "${operation.key} completed in ${duration}ms")
        return result
    }
    
    fun logBatchInfo(batchSize: Int, totalArticles: Int) {
        val batches = (totalArticles + batchSize - 1) / batchSize
        Log.i(TAG, "Processing $totalArticles articles in $batches batches of $batchSize each")
        
        addRecord(
            operationName = SyncPerformanceOperation.BATCH_PROCESSING.key,
            batchSize = batchSize,
            totalArticles = totalArticles
        )
    }
    
    fun logSyncMode(isIncremental: Boolean, lastSyncTime: Long? = null) {
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
        isIncremental: Boolean? = null,
        lastSyncHoursAgo: Long? = null
    ) {
        val record = SyncPerformanceRecord(
            timestamp = System.currentTimeMillis(),
            operationName = operationName,
            durationMs = durationMs,
            batchSize = batchSize,
            totalArticles = totalArticles,
            isIncremental = isIncremental,
            lastSyncHoursAgo = lastSyncHoursAgo
        )
        preferencesManager.addSyncPerformanceRecord(record)
    }
}
