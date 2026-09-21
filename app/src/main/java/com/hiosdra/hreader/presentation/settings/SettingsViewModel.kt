package com.hiosdra.hreader.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.settings.BackendConfiguration
import com.hiosdra.hreader.core.application.storage.StorageCleanupAction
import com.hiosdra.hreader.core.application.sync.SyncMode
import com.hiosdra.hreader.core.application.usecase.settings.SettingsUseCase
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.presentation.text.UiText
import com.hiosdra.hreader.core.application.usecase.settings.StorageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsUseCase,
    private val storageUseCase: StorageUseCase
) : ViewModel() {
    val actions = SettingsActions(
        server = ServerSettingsActions(
            onBackendTypeRequested = ::onBackendTypeRequested,
            onCancelBackendSwitch = ::cancelBackendSwitch,
            onConfirmBackendSwitch = ::confirmBackendSwitch,
            onServerUrlChange = ::onServerUrlChange,
            onUsernameChange = ::onUsernameChange,
            onSecretChange = ::onSecretChange,
            onTestConnection = ::testConnection,
            onApplySettings = ::applyServerSettings
        ),
        sync = SyncSettingsActions(
            onIntervalChange = ::onSyncIntervalChange,
            onSyncModeChange = ::onSyncModeChange,
            onUnmeteredOnlyChange = ::onUnmeteredOnlyChange,
            onSyncWhileRoamingChange = ::onSyncWhileRoamingChange,
            onQuietHoursEnabledChange = ::onQuietHoursEnabledChange,
            onQuietHoursChange = ::onQuietHoursChange
        ),
        offline = OfflineSettingsActions(
            onPrepare = ::prepareForOffline,
            onFullOfflineSync = ::prepareFullOffline,
            onTravelModePrepare = ::prepareTravelMode,
            onBacklogTargetChange = ::onBacklogTargetChange,
            onImageDownloadEnabledChange = ::onImageDownloadEnabledChange,
            onImageCacheBudgetChange = ::onImageCacheBudgetChange
        ),
        storage = StorageSettingsActions(
            onRefresh = ::refreshStorage,
            onCleanup = ::cleanupStorage
        ),
        ai = AiSettingsActions(
            onOpenRouterApiKeyChange = ::onOpenRouterApiKeyChange,
            onModelSearchQueryChange = ::onModelSearchQueryChange,
            onFreeOnlyChange = ::onFreeOnlyChange,
            onReloadModels = { loadAiModels(forceRefresh = true) },
            onModelSelected = ::onModelSelected
        ),
        localData = LocalDataSettingsActions(
            onResyncFromScratch = ::resyncFromScratch,
            onSignOut = ::signOut
        )
    )

    private var serverDraft = settings.getBackendConfiguration()
    private val _uiState = MutableStateFlow(currentSettings(serverDraft))
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(settings.getOpenRouterApiKey())
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _aiModels = MutableStateFlow(AiModelsUiState(selectedModelId = settings.getAiModelId()))
    val aiModels: StateFlow<AiModelsUiState> = _aiModels.asStateFlow()

    private val operations = SettingsOperationCoordinator(settings, storageUseCase, viewModelScope)
    val offline: StateFlow<OfflineUiState> = operations.offline
    val sync: StateFlow<SyncUiState> = operations.sync
    val storage: StateFlow<StorageUiState> = operations.storage

    init {
        loadAiModels()
    }

    fun prepareForOffline() = operations.prepareForOffline()

    fun prepareFullOffline() = operations.prepareFullOffline()

    fun prepareTravelMode(fullOffline: Boolean) = operations.prepareTravelMode(fullOffline)

    fun onSyncIntervalChange(minutes: Int) = operations.onSyncIntervalChange(minutes)

    fun onSyncModeChange(mode: SyncMode) = operations.onSyncModeChange(mode)

    fun onUnmeteredOnlyChange(enabled: Boolean) = operations.onUnmeteredOnlyChange(enabled)

    fun onSyncWhileRoamingChange(enabled: Boolean) = operations.onSyncWhileRoamingChange(enabled)

    fun onQuietHoursEnabledChange(enabled: Boolean) = operations.onQuietHoursEnabledChange(enabled)

    fun onQuietHoursChange(startHour: Int, endHour: Int) =
        operations.onQuietHoursChange(startHour, endHour)

    fun resyncFromScratch() = operations.resyncFromScratch { failure ->
        _uiState.value = currentSettings(serverDraft).withClearFailure(failure)
    }

    fun onBacklogTargetChange(target: Int) = operations.onBacklogTargetChange(target)

    fun onImageDownloadEnabledChange(enabled: Boolean) =
        operations.onImageDownloadEnabledChange(enabled)

    fun onImageCacheBudgetChange(megabytes: Int) =
        operations.onImageCacheBudgetChange(megabytes)

    fun refreshStorage() = operations.refreshStorage()

    fun cleanupStorage(action: StorageCleanupAction) = operations.cleanupStorage(action)

    fun onOpenRouterApiKeyChange(apiKey: String) {
        settings.setOpenRouterApiKey(apiKey)
        _openRouterApiKey.value = apiKey
    }

    fun loadAiModels(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _aiModels.value = _aiModels.value.copy(isLoading = true, error = null)
            val result = runCatchingCancellable { settings.getModels(forceRefresh) }
            _aiModels.value = result.fold(
                onSuccess = { _aiModels.value.copy(isLoading = false, models = it) },
                onFailure = {
                    _aiModels.value.copy(
                        isLoading = false,
                        error = UiText.Resource(R.string.ai_model_load_error)
                    )
                }
            )
        }
    }

    fun onModelSearchQueryChange(query: String) {
        _aiModels.value = _aiModels.value.copy(searchQuery = query)
    }

    fun onFreeOnlyChange(freeOnly: Boolean) {
        _aiModels.value = _aiModels.value.copy(freeOnly = freeOnly)
    }

    fun onModelSelected(model: AiModel) {
        settings.setAiModelId(model.id)
        _aiModels.value = _aiModels.value.copy(selectedModelId = model.id)
    }

    fun onBackendTypeRequested(backendType: BackendType) {
        if (backendType == _uiState.value.backendType) return
        if (settings.getLastSyncTimestamp() == 0L) {
            switchBackendTo(backendType)
            return
        }
        _uiState.value = _uiState.value.copy(pendingBackendType = backendType)
    }

    fun cancelBackendSwitch() {
        _uiState.value = _uiState.value.copy(pendingBackendType = null)
    }

    fun confirmBackendSwitch() {
        switchBackendTo(_uiState.value.pendingBackendType ?: return)
    }

    private fun switchBackendTo(backendType: BackendType) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSwitchingBackend = true, pendingBackendType = null)
            serverDraft = serverDraft.copy(backendType = backendType)
            val switched = runCatchingCancellable {
                settings.applyBackendConfiguration(serverDraft)
            }
            settings.schedulePeriodicSync()
            if (switched.isSuccess) {
                settings.syncNow(forceFullSync = true, userVisible = true)
            }
            serverDraft = settings.getBackendConfiguration()
            _uiState.value = currentSettings(serverDraft)
                .withClearFailure(switched.exceptionOrNull())
                .copy(isSwitchingBackend = false)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSwitchingBackend = true, signOutCompleted = false)
            val signedOut = runCatchingCancellable {
                val configuration = settings.getBackendConfiguration()
                settings.applyBackendConfiguration(
                    configuration.copy(
                        freshRssUsername = "",
                        freshRssSecret = if (configuration.backendType == BackendType.FRESHRSS) "" else configuration.freshRssSecret,
                        minifluxSecret = if (configuration.backendType == BackendType.MINIFLUX) "" else configuration.minifluxSecret
                    )
                )
            }
            settings.schedulePeriodicSync()
            serverDraft = settings.getBackendConfiguration()
            _uiState.value = currentSettings(serverDraft)
                .withClearFailure(signedOut.exceptionOrNull())
                .copy(
                    isSwitchingBackend = false,
                    signOutCompleted = signedOut.isSuccess
                )
        }
    }

    fun onServerUrlChange(serverUrl: String) {
        updateServerDraft { draft ->
            when (draft.backendType) {
                BackendType.FRESHRSS -> draft.copy(freshRssServerUrl = serverUrl)
                BackendType.MINIFLUX -> draft.copy(minifluxServerUrl = serverUrl)
            }
        }
    }

    fun onUsernameChange(username: String) {
        updateServerDraft { it.copy(freshRssUsername = username) }
    }

    fun onSecretChange(secret: String) {
        updateServerDraft { draft ->
            when (draft.backendType) {
                BackendType.FRESHRSS -> draft.copy(freshRssSecret = secret)
                BackendType.MINIFLUX -> draft.copy(minifluxSecret = secret)
            }
        }
    }

    private fun updateServerDraft(transform: (BackendConfiguration) -> BackendConfiguration) {
        serverDraft = transform(serverDraft)
        _uiState.value = currentSettings(serverDraft).cleared()
    }

    fun onSetupFinished(onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            val applied = runCatchingCancellable {
                settings.applyBackendConfiguration(serverDraft)
            }
            if (applied.isFailure) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = UiText.Resource(R.string.settings_prepare_cache_failed)
                )
                return@launch
            }
            serverDraft = settings.getBackendConfiguration()
            settings.schedulePeriodicSync()
            settings.syncNow(forceFullSync = true, userVisible = true)
            onFinished()
        }
    }

    fun applyServerSettings() {
        if (
            !_uiState.value.hasAllFields ||
            _uiState.value.isSwitchingBackend ||
            _uiState.value.isApplying ||
            _uiState.value.isTesting
        ) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isApplying = true, statusMessage = null)
            val applied = runCatchingCancellable { settings.applyBackendConfiguration(serverDraft) }
            if (applied.isSuccess) settings.schedulePeriodicSync()
            serverDraft = settings.getBackendConfiguration()
            val ownerChanged = applied.getOrDefault(false)
            _uiState.value = currentSettings(serverDraft).copy(
                isApplying = false,
                statusMessage = when {
                    applied.isFailure -> UiText.Resource(R.string.settings_cache_update_failed)
                    ownerChanged -> UiText.Resource(R.string.settings_data_cleared_new_account)
                    else -> UiText.Resource(R.string.settings_server_settings_saved)
                }
            )
            if (applied.isSuccess && ownerChanged) {
                settings.syncNow(forceFullSync = true, userVisible = true)
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, statusMessage = null)
            val result = runCatchingCancellable { settings.verifyConnection(serverDraft) }
            _uiState.value = _uiState.value.copy(
                isTesting = false,
                isConnected = result.isSuccess,
                statusMessage = if (result.isSuccess) {
                    val subscriptionCount = result.getOrThrow()
                    UiText.Plural(
                        id = R.plurals.settings_connected,
                        count = subscriptionCount,
                        args = listOf(subscriptionCount)
                    )
                } else {
                    UiText.Resource(R.string.settings_connect_failed)
                }
            )
        }
    }

    private fun currentSettings(configuration: BackendConfiguration): ServerSettingsUiState {
        val backendType = configuration.backendType
        return ServerSettingsUiState(
            backendType = backendType,
            serverUrl = configuration.serverUrlFor(backendType),
            username = configuration.freshRssUsername,
            secret = configuration.secretFor(backendType),
            isDirty = configuration != settings.getBackendConfiguration()
        )
    }
}

private fun ServerSettingsUiState.cleared(): ServerSettingsUiState =
    copy(statusMessage = null, isConnected = false)

private fun ServerSettingsUiState.withClearFailure(failure: Throwable?): ServerSettingsUiState =
    if (failure == null) this
    else copy(
        statusMessage = UiText.Resource(R.string.settings_clear_articles_failed)
    )
