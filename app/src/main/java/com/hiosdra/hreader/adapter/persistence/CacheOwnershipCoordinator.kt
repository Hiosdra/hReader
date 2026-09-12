package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.CacheStore
import com.hiosdra.hreader.core.application.port.out.PreferenceWriteBarrier
import com.hiosdra.hreader.core.application.port.out.NoopSyncSessionGate
import com.hiosdra.hreader.core.application.port.out.SyncHealthStore
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.port.out.SyncSessionGate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class CacheOwnershipCoordinator(
    private val dataCleaner: CacheDataCleaner,
    private val preferences: SyncPreferences,
    private val syncHealth: SyncHealthStore,
    private val backendIdentity: BackendIdentity,
    private val preferenceWrites: PreferenceWriteBarrier,
    private val sessionGate: SyncSessionGate = NoopSyncSessionGate
) : CacheStore {
    private val ownerMutex = Mutex()

    override suspend fun ensureCacheOwner(): Boolean {
        preferenceWrites.awaitReady()
        return sessionGate.withSessionChange {
            ownerMutex.withLock { ensureCacheOwnerLocked() }
        }
    }

    private suspend fun ensureCacheOwnerLocked(): Boolean {
        val ownerKey = backendIdentity.cacheOwnerKey()
        if (preferences.isCacheCleanupPending()) {
            clearBackendDataLocked()
            preferences.setCacheOwnerKey(ownerKey)
            preferenceWrites.awaitWrites()
            return true
        }

        val storedOwner = preferences.getCacheOwnerKey()
        if (storedOwner.isBlank()) {
            clearBackendDataLocked()
            preferences.setCacheOwnerKey(ownerKey)
            preferenceWrites.awaitWrites()
            return true
        }
        if (storedOwner == ownerKey) {
            dataCleaner.repair()
            return false
        }

        clearBackendDataLocked()
        preferences.setCacheOwnerKey(ownerKey)
        preferenceWrites.awaitWrites()
        return true
    }

    override suspend fun ensureCacheOwnerWhenConfigured(): Boolean {
        preferenceWrites.awaitReady()
        return if (backendIdentity.isComplete()) ensureCacheOwner() else false
    }

    override suspend fun clearBackendData() = sessionGate.withSessionChange {
        ownerMutex.withLock { clearBackendDataLocked() }
    }

    private suspend fun clearBackendDataLocked() {
        preferences.setCacheCleanupPending(true)
        preferenceWrites.awaitWrites()
        dataCleaner.clearAll()
        preferences.setCacheOwnerKey("")
        preferences.setLastSyncTimestamp(0L)
        preferences.setLastFullSyncTimestamp(0L)
        preferences.clearSyncCheckpoint()
        syncHealth.clear()
        preferenceWrites.awaitWrites()
        preferences.setCacheCleanupPending(false)
        preferenceWrites.awaitWrites()
    }
}
