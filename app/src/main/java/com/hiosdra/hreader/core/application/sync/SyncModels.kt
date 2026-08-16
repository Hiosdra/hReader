package com.hiosdra.hreader.core.application.sync

import com.hiosdra.hreader.core.domain.model.Enclosure
import java.util.UUID

data class PrefetchTarget(
    val id: Long,
    val url: String,
    val enclosures: List<Enclosure>
)

enum class SyncOperationState {
    IDLE,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

enum class OfflinePreparationStage {
    IDLE,
    SYNCING,
    DOWNLOADING_CONTENT,
    ARCHIVING_PAGES
}

enum class SyncOperationError {
    CONFIGURE_SERVER,
    CACHE_UPDATE_FAILED
}

data class SyncOperationStatus(
    val state: SyncOperationState = SyncOperationState.IDLE,
    val errorMessage: String? = null,
    val error: SyncOperationError? = null,
    val workIds: Set<UUID> = emptySet()
) {
    val isRunning: Boolean
        get() = state == SyncOperationState.RUNNING
}

data class OfflinePreparationProgress(
    val isRunning: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val status: SyncOperationStatus = SyncOperationStatus(),
    val isFullOffline: Boolean = false,
    val stage: OfflinePreparationStage = OfflinePreparationStage.IDLE
)
