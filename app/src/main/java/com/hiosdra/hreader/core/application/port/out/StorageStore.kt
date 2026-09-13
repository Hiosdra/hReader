package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.storage.StorageCleanupAction
import com.hiosdra.hreader.core.application.storage.StorageCleanupProgress
import com.hiosdra.hreader.core.application.storage.StorageCleanupResult
import com.hiosdra.hreader.core.application.storage.StorageSnapshot

interface StorageStore {
    suspend fun inspect(): StorageSnapshot

    suspend fun cleanup(
        action: StorageCleanupAction,
        onProgress: suspend (StorageCleanupProgress) -> Unit = {}
    ): StorageCleanupResult
}
