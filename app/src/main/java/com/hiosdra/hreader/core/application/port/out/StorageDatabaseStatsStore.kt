package com.hiosdra.hreader.core.application.port.out

data class StorageDatabaseStats(
    val articleCount: Int,
    val feedCount: Int,
    val storedContentCount: Int,
    val readingPositionCount: Int,
    val imageCount: Int,
    val pageCount: Int
)

interface StorageDatabaseStatsStore {
    suspend fun getStats(): StorageDatabaseStats
}
