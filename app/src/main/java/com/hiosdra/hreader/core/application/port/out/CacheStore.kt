package com.hiosdra.hreader.core.application.port.out

interface CacheStore {
    suspend fun ensureCacheOwner(): Boolean
    suspend fun ensureCacheOwnerWhenConfigured(): Boolean
    suspend fun clearBackendData()
}
