package com.hiosdra.hreader.presentation.settings

import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.storage.StorageCleanupAction
import com.hiosdra.hreader.core.application.storage.StorageCleanupProgress
import com.hiosdra.hreader.core.application.storage.StorageCleanupResult
import com.hiosdra.hreader.core.application.storage.StorageSnapshot
import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.OfflinePreparationStage
import com.hiosdra.hreader.core.application.sync.SyncDefaults
import com.hiosdra.hreader.core.application.sync.SyncMode
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.core.domain.model.OfflineReadiness
import com.hiosdra.hreader.presentation.text.UiText

data class ServerSettingsUiState(
    val backendType: BackendType = BackendType.FRESHRSS,
    val serverUrl: String = "",
    val username: String = "",
    val secret: String = "",
    val isTesting: Boolean = false,
    val statusMessage: UiText? = null,
    val isConnected: Boolean = false,
    val pendingBackendType: BackendType? = null,
    val isSwitchingBackend: Boolean = false,
    val isApplying: Boolean = false,
    val isDirty: Boolean = false,
    val signOutCompleted: Boolean = false
) {
    val hasAllFields: Boolean
        get() = serverUrl.isNotBlank() &&
            secret.isNotBlank() &&
            (!backendType.requiresUsername || username.isNotBlank())
}

data class AiModelsUiState(
    val selectedModelId: String = AiModel.DEFAULT_ID,
    val models: List<AiModel> = emptyList(),
    val searchQuery: String = "",
    val freeOnly: Boolean = true,
    val isLoading: Boolean = false,
    val error: UiText? = null
) {
    val visibleModels: List<AiModel>
        get() = models.filter { (!freeOnly || it.isFree) && it.matches(searchQuery) }

    val selectedModelIsMissing: Boolean
        get() = models.isNotEmpty() && models.none { it.id == selectedModelId }

    val selectedModelName: String
        get() = models.find { it.id == selectedModelId }?.displayName ?: selectedModelId
}

data class OfflineUiState(
    val readiness: OfflineReadiness = OfflineReadiness(),
    val backlogTarget: Int = 0,
    val imageDownloadEnabled: Boolean = true,
    val imageCacheBudgetMegabytes: Int = 0,
    val isPreparing: Boolean = false,
    val preparationDone: Int = 0,
    val preparationTotal: Int = 0,
    val isFullOfflinePreparation: Boolean = false,
    val preparationStage: OfflinePreparationStage = OfflinePreparationStage.IDLE,
    val preparationStatus: SyncOperationStatus = SyncOperationStatus()
) {
    val preparationProgress: Float?
        get() = preparationTotal.takeIf { it > 0 }?.let { preparationDone.toFloat() / it }
}

data class SyncUiState(
    val intervalMinutes: Int = SyncDefaults.INTERVAL_MINUTES,
    val syncMode: SyncMode = SyncMode.SAFE,
    val unmeteredOnly: Boolean = false,
    val syncWhileRoaming: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = SyncDefaults.QUIET_HOURS_START,
    val quietHoursEnd: Int = SyncDefaults.QUIET_HOURS_END,
    val isResyncing: Boolean = false,
    val resyncStatus: SyncOperationStatus = SyncOperationStatus(),
    val showResyncStatus: Boolean = false
)

data class StorageUiState(
    val snapshot: StorageSnapshot? = null,
    val isLoading: Boolean = false,
    val cleanupAction: StorageCleanupAction? = null,
    val cleanupProgress: StorageCleanupProgress? = null,
    val cleanupResult: StorageCleanupResult? = null,
    val error: UiText? = null
)

internal fun OfflineUiState.withPreparation(progress: OfflinePreparationProgress) = copy(
    isPreparing = progress.isRunning,
    preparationDone = progress.done,
    preparationTotal = progress.total,
    isFullOfflinePreparation = progress.isFullOffline,
    preparationStage = progress.stage,
    preparationStatus = progress.status
)
