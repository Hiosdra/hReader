package com.hiosdra.hreader.presentation.settings

import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.storage.StorageCleanupAction
import com.hiosdra.hreader.core.application.sync.SyncMode
import com.hiosdra.hreader.core.domain.model.BackendType

data class ServerSettingsActions(
    val onBackendTypeRequested: (BackendType) -> Unit,
    val onCancelBackendSwitch: () -> Unit,
    val onConfirmBackendSwitch: () -> Unit,
    val onServerUrlChange: (String) -> Unit,
    val onUsernameChange: (String) -> Unit,
    val onSecretChange: (String) -> Unit,
    val onTestConnection: () -> Unit,
    val onApplySettings: () -> Unit
)

data class SyncSettingsActions(
    val onIntervalChange: (Int) -> Unit,
    val onSyncModeChange: (SyncMode) -> Unit,
    val onUnmeteredOnlyChange: (Boolean) -> Unit,
    val onSyncWhileRoamingChange: (Boolean) -> Unit,
    val onQuietHoursEnabledChange: (Boolean) -> Unit,
    val onQuietHoursChange: (Int, Int) -> Unit
)

data class OfflineSettingsActions(
    val onPrepare: () -> Unit,
    val onFullOfflineSync: () -> Unit,
    val onTravelModePrepare: (Boolean) -> Unit,
    val onBacklogTargetChange: (Int) -> Unit,
    val onImageDownloadEnabledChange: (Boolean) -> Unit,
    val onImageCacheBudgetChange: (Int) -> Unit
)

data class StorageSettingsActions(
    val onRefresh: () -> Unit,
    val onCleanup: (StorageCleanupAction) -> Unit
)

data class AiSettingsActions(
    val onOpenRouterApiKeyChange: (String) -> Unit,
    val onModelSearchQueryChange: (String) -> Unit,
    val onFreeOnlyChange: (Boolean) -> Unit,
    val onReloadModels: () -> Unit,
    val onModelSelected: (AiModel) -> Unit
)

data class LocalDataSettingsActions(
    val onResyncFromScratch: () -> Unit,
    val onSignOut: () -> Unit
)

data class SettingsActions(
    val server: ServerSettingsActions,
    val sync: SyncSettingsActions,
    val offline: OfflineSettingsActions,
    val storage: StorageSettingsActions,
    val ai: AiSettingsActions,
    val localData: LocalDataSettingsActions
)
