package com.hiosdra.hreader.core.application.sync

import com.hiosdra.hreader.core.domain.model.Enclosure
import java.util.UUID

@JvmInline
value class SyncOperationId(val value: UUID)

sealed interface SyncIntent {
    data object Periodic : SyncIntent
    data object Background : SyncIntent
    data object Resync : SyncIntent
    data object PrepareOffline : SyncIntent
    data object PrepareFullOffline : SyncIntent
    data class User(
        val forceFullSync: Boolean = false,
        val userVisible: Boolean = false,
        val operationTitle: String? = null
    ) : SyncIntent
}

data class SyncPlan(
    val forceFullSync: Boolean,
    val expedited: Boolean,
    val ignoreQuietHours: Boolean,
    val userVisible: Boolean,
    val drainRemaining: Boolean = false,
    val offlinePreparation: Boolean = false,
    val fullOfflinePreparation: Boolean = false,
    val includeFullPages: Boolean = false
)

class SyncCoordinator {
    fun plan(intent: SyncIntent): SyncPlan = when (intent) {
        SyncIntent.Periodic,
        SyncIntent.Background -> SyncPlan(
            forceFullSync = false,
            expedited = false,
            ignoreQuietHours = false,
            userVisible = false
        )
        SyncIntent.Resync -> SyncPlan(
            forceFullSync = true,
            expedited = true,
            ignoreQuietHours = true,
            userVisible = true
        )
        SyncIntent.PrepareOffline -> SyncPlan(
            forceFullSync = true,
            expedited = true,
            ignoreQuietHours = true,
            userVisible = true,
            drainRemaining = true,
            offlinePreparation = true
        )
        SyncIntent.PrepareFullOffline -> SyncPlan(
            forceFullSync = true,
            expedited = true,
            ignoreQuietHours = true,
            userVisible = true,
            drainRemaining = true,
            offlinePreparation = true,
            fullOfflinePreparation = true,
            includeFullPages = true
        )
        is SyncIntent.User -> SyncPlan(
            forceFullSync = intent.forceFullSync,
            expedited = intent.userVisible,
            ignoreQuietHours = intent.userVisible,
            userVisible = intent.userVisible
        )
    }
}

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
    val workIds: Set<SyncOperationId> = emptySet()
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
