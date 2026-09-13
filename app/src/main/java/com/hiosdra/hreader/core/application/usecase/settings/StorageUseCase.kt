package com.hiosdra.hreader.core.application.usecase.settings

import com.hiosdra.hreader.core.application.port.out.StorageStore
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import com.hiosdra.hreader.core.application.storage.StorageCleanupAction
import com.hiosdra.hreader.core.application.storage.StorageCleanupProgress
import com.hiosdra.hreader.core.application.storage.StorageCleanupResult
import com.hiosdra.hreader.core.application.storage.StorageSnapshot

class StorageUseCase(
    private val storage: StorageStore,
    private val sync: SyncRequester
) {
    suspend fun inspect(): StorageSnapshot = storage.inspect()

    suspend fun cleanup(
        action: StorageCleanupAction,
        onProgress: suspend (StorageCleanupProgress) -> Unit = {}
    ): StorageCleanupResult {
        return try {
            sync.cancelAllSync()
            storage.cleanup(action, onProgress)
        } finally {
            sync.schedulePeriodicSync()
        }
    }
}
