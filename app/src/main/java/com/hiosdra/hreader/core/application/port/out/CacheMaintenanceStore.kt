package com.hiosdra.hreader.core.application.port.out

interface CacheMaintenanceStore {
    suspend fun maintain()
}
