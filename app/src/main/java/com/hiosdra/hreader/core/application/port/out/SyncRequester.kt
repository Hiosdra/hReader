package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import com.hiosdra.hreader.core.application.sync.SyncIntent
import com.hiosdra.hreader.core.application.sync.SyncOperationId
import kotlinx.coroutines.flow.Flow

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
    fun observeOfflinePreparation(): Flow<OfflinePreparationProgress>
    suspend fun cancelAllSync()
    fun enqueueBackgroundSyncChain()
    fun prepareForOffline(): SyncOperationId? = request(SyncIntent.PrepareOffline)
    fun prepareFullOffline(): SyncOperationId? = request(SyncIntent.PrepareFullOffline)
}
