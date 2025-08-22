package com.hiosdra.hreader.util

import android.util.Log

object SyncPerformanceLogger {
    private const val TAG = "SyncPerformance"
    
    suspend fun <T> measureSyncTime(operationName: String, block: suspend () -> T): T {
        val startTime = System.currentTimeMillis()
        val result = block()
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        Log.i(TAG, "$operationName completed in ${duration}ms")
        return result
    }
    
    fun logBatchInfo(batchSize: Int, totalArticles: Int) {
        val batches = (totalArticles + batchSize - 1) / batchSize // Ceiling division
        Log.i(TAG, "Processing $totalArticles articles in $batches batches of $batchSize each")
    }
    
    fun logSyncMode(isIncremental: Boolean, lastSyncTime: Long? = null) {
        if (isIncremental && lastSyncTime != null) {
            val hoursAgo = (System.currentTimeMillis() - lastSyncTime) / (60 * 60 * 1000)
            Log.i(TAG, "Using incremental sync (last sync: ${hoursAgo}h ago)")
        } else {
            Log.i(TAG, "Using full sync")
        }
    }
}