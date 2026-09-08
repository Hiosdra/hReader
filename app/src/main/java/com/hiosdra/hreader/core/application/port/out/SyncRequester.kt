package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import com.hiosdra.hreader.core.application.sync.SyncIntent
import com.hiosdra.hreader.core.application.sync.SyncOperationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

interface SyncRequester {
    fun start()
    fun schedulePeriodicSync()
    fun enqueuePrefetch()
    fun request(intent: SyncIntent): SyncOperationId?
    fun syncNow(
        forceFullSync: Boolean = false,
        userVisible: Boolean = false,
        operationTitle: String? = null
    ): SyncOperationId? = request(SyncIntent.User(forceFullSync, userVisible, operationTitle))
    fun resyncNow(): SyncOperationId? = request(SyncIntent.Resync)
    fun observeRequestedSync(): Flow<SyncOperationStatus>
    fun observeSyncActivity(): Flow<Boolean> = observeRequestedSync()
        .map { it.isRunning }
        .distinctUntilChanged()
    fun observeNextScheduledSync(): Flow<Long?> = flowOf(null)
    fun observeOfflinePreparation(): Flow<OfflinePreparationProgress>
    suspend fun cancelAllSync()
    fun enqueueBackgroundSyncChain()
    fun prepareForOffline(): SyncOperationId? = request(SyncIntent.PrepareOffline)
    fun prepareFullOffline(): SyncOperationId? = request(SyncIntent.PrepareFullOffline)
}
