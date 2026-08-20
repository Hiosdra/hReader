package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface SyncRequester {
    fun start()
    fun schedulePeriodicSync()
    fun enqueuePrefetch()
    fun syncNow(
        forceFullSync: Boolean = false,
        userVisible: Boolean = false,
        operationTitle: String? = null
    ): UUID?
    fun resyncNow(): UUID?
    fun observeRequestedSync(): Flow<SyncOperationStatus>
    fun observeOfflinePreparation(): Flow<OfflinePreparationProgress>
    suspend fun cancelAllSync()
    fun enqueueBackgroundSyncChain()
    fun prepareForOffline(): UUID?
    fun prepareFullOffline(): UUID?
}
