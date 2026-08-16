package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.observability.SyncPerformanceRecord

interface PerformancePreferences {
    fun getSyncPerformanceRecords(): List<SyncPerformanceRecord>
    fun addSyncPerformanceRecord(record: SyncPerformanceRecord)
    fun clearSyncPerformanceRecords()
}
