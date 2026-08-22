package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.CacheStore
import com.hiosdra.hreader.core.application.port.out.PreferenceWriteBarrier
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class CacheOwnershipCoordinator(
    private val dataCleaner: CacheDataCleaner,
    private val preferences: SyncPreferences,
    private val backendIdentity: BackendIdentity,
    private val preferenceWrites: PreferenceWriteBarrier
) : CacheStore {
    private val ownerMutex = Mutex()

    override suspend fun ensureCacheOwner(): Boolean = ownerMutex.withLock {
        preferenceWrites.awaitReady()
        if (preferences.isCacheCleanupPending()) {
            clearBackendDataLocked()
        }

        val ownerKey = backendIdentity.cacheOwnerKey()
        val storedOwner = preferences.getCacheOwnerKey()
        if (storedOwner.isBlank()) {
            preferences.setCacheOwnerKey(ownerKey)
            preferenceWrites.awaitWrites()
            dataCleaner.repair()
            return@withLock false
        }
        if (storedOwner == ownerKey) {
            dataCleaner.repair()
            return@withLock false
        }

        clearBackendDataLocked()
        preferences.setCacheOwnerKey(ownerKey)
        preferenceWrites.awaitWrites()
        true
    }

    override suspend fun ensureCacheOwnerWhenConfigured(): Boolean =
        if (backendIdentity.isComplete()) ensureCacheOwner() else false

    override suspend fun clearBackendData() = ownerMutex.withLock {
        clearBackendDataLocked()
    }

    private suspend fun clearBackendDataLocked() {
        preferences.setCacheCleanupPending(true)
        preferenceWrites.awaitWrites()
        dataCleaner.clearAll()
        preferences.setCacheOwnerKey("")
        preferences.setLastSyncTimestamp(0L)
        preferences.setLastFullSyncTimestamp(0L)
        preferenceWrites.awaitWrites()
        preferences.setCacheCleanupPending(false)
        preferenceWrites.awaitWrites()
    }
}
