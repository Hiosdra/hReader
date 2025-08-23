package com.hiosdra.hreader.util

import android.util.Log
import com.hiosdra.hreader.data.preferences.PreferencesManager

class SyncPerformanceLogger(private val preferencesManager: PreferencesManager) {
    private val TAG = "SyncPerformance"
    
    suspend fun <T> measureSyncTime(operationName: String, block: suspend () -> T): T {
        val startTime = System.currentTimeMillis()
        val result = block()
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Create performance record
        val record = SyncPerformanceRecord(
            timestamp = startTime,
            operationName = operationName,
            durationMs = duration
        )
        
        // Save to preferences
        preferencesManager.addSyncPerformanceRecord(record)
        
        Log.i(TAG, "$operationName completed in ${duration}ms")
        return result
    }
    
    fun logBatchInfo(batchSize: Int, totalArticles: Int) {
        val batches = (totalArticles + batchSize - 1) / batchSize // Ceiling division
        Log.i(TAG, "Processing $totalArticles articles in $batches batches of $batchSize each")
        
        // Create performance record with batch info
        val record = SyncPerformanceRecord(
            timestamp = System.currentTimeMillis(),
            operationName = "Batch Processing",
            durationMs = 0, // Duration will be measured in measureSyncTime
            batchSize = batchSize,
            totalArticles = totalArticles
        )
        
        preferencesManager.addSyncPerformanceRecord(record)
    }
    
    fun logSyncMode(isIncremental: Boolean, lastSyncTime: Long? = null) {
        val hoursAgo = if (isIncremental && lastSyncTime != null) {
            (System.currentTimeMillis() - lastSyncTime) / (60 * 60 * 1000)
        } else null
        
        if (isIncremental && lastSyncTime != null) {
            Log.i(TAG, "Using incremental sync (last sync: ${hoursAgo}h ago)")
        } else {
            Log.i(TAG, "Using full sync")
        }
        
        // Create performance record with sync mode info
        val record = SyncPerformanceRecord(
            timestamp = System.currentTimeMillis(),
            operationName = if (isIncremental) "Incremental Sync" else "Full Sync",
            durationMs = 0, // Duration will be measured in measureSyncTime
            isIncremental = isIncremental,
            lastSyncHoursAgo = hoursAgo
        )
        
        preferencesManager.addSyncPerformanceRecord(record)
    }
}