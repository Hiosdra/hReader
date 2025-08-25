package com.hiosdra.hreader.util

import com.hiosdra.hreader.data.preferences.PreferencesManager

class SyncPerformanceLogger(private val preferencesManager: PreferencesManager) {
    suspend fun <T> measureSyncTime(operationName: String, block: suspend () -> T): T {
        val startTime = System.currentTimeMillis()
        val result = block()
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        val record = SyncPerformanceRecord(
            timestamp = startTime,
            operationName = operationName,
            durationMs = duration
        )
        
        preferencesManager.addSyncPerformanceRecord(record)
        return result
    }
}