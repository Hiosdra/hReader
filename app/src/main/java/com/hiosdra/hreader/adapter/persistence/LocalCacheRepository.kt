package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.CacheStore
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class LocalCacheRepository(
    private val dataCleaner: CacheDataCleaner,
    private val preferencesManager: SyncPreferences,
    private val backendIdentity: BackendIdentity
) : CacheStore {
    private val ownerMutex = Mutex()

    override suspend fun ensureCacheOwner(): Boolean = ownerMutex.withLock {
        val ownerKey = backendIdentity.cacheOwnerKey()
        val storedOwner = preferencesManager.getCacheOwnerKey()
        if (storedOwner.isBlank()) {
            preferencesManager.setCacheOwnerKey(ownerKey)
            return@withLock false
        }
        if (storedOwner == ownerKey) return@withLock false
        clearBackendDataLocked()
        preferencesManager.setCacheOwnerKey(ownerKey)
        true
    }

    override suspend fun ensureCacheOwnerWhenConfigured(): Boolean =
        if (backendIdentity.isComplete()) ensureCacheOwner() else false

    override suspend fun clearBackendData() = ownerMutex.withLock {
        clearBackendDataLocked()
    }

    private suspend fun clearBackendDataLocked() {
        dataCleaner.clearAll()
        preferencesManager.setCacheOwnerKey("")
        // Both timestamps, or the next sync against a different backend would still consider the
        // cache recently reconciled against a full server state that was never this backend's.
        preferencesManager.setLastSyncTimestamp(0L)
        preferencesManager.setLastFullSyncTimestamp(0L)
    }
}
