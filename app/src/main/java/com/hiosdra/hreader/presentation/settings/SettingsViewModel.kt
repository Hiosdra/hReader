package com.hiosdra.hreader.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.settings.BackendConfiguration
import com.hiosdra.hreader.core.application.sync.SyncDefaults
import com.hiosdra.hreader.core.application.usecase.settings.SettingsUseCase
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.core.domain.model.OfflineReadiness
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.core.application.sync.SyncOperationError
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import com.hiosdra.hreader.core.application.sync.OfflinePreparationStage
import com.hiosdra.hreader.core.application.sync.SyncOperationId
import com.hiosdra.hreader.presentation.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    /** Falls back to the id: the list may not have loaded yet, or may never load offline. */
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
    /** Null while the worker has not reported counts yet, which reads as indeterminate. */
    val preparationProgress: Float?
        get() = if (preparationTotal > 0) preparationDone.toFloat() / preparationTotal else null
}

data class SyncUiState(
    val intervalMinutes: Int = SyncDefaults.INTERVAL_MINUTES,
    val unmeteredOnly: Boolean = false,
    val syncWhileRoaming: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = SyncDefaults.QUIET_HOURS_START,
    val quietHoursEnd: Int = SyncDefaults.QUIET_HOURS_END,
    val isResyncing: Boolean = false,
    val resyncStatus: SyncOperationStatus = SyncOperationStatus(),
    val showResyncStatus: Boolean = false
)

class SettingsViewModel(
    private val settings: SettingsUseCase
) : ViewModel() {
    private var serverDraft = settings.getBackendConfiguration()
    private val _uiState = MutableStateFlow(currentSettings(serverDraft))
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(settings.getOpenRouterApiKey())
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _aiModels = MutableStateFlow(AiModelsUiState(selectedModelId = settings.getAiModelId()))
    val aiModels: StateFlow<AiModelsUiState> = _aiModels.asStateFlow()

    private val _offline = MutableStateFlow(currentOfflineSettings())
    val offline: StateFlow<OfflineUiState> = _offline.asStateFlow()
    private var offlineAwaitingWork = false
    private var offlineWorkId: SyncOperationId? = null

    private val _sync = MutableStateFlow(currentSyncSettings())
    val sync: StateFlow<SyncUiState> = _sync.asStateFlow()
    private var resyncAwaitingWork = false
    private var resyncWorkId: SyncOperationId? = null

    init {
        loadAiModels()
        viewModelScope.launch {
            settings.observeOfflineReadiness().collect { readiness ->
                _offline.value = _offline.value.copy(readiness = readiness)
            }
        }
        viewModelScope.launch {
            settings.observeOfflinePreparation().collect { progress ->
                if (offlineAwaitingWork) {
                    val expectedWorkId = offlineWorkId ?: return@collect
                    if (expectedWorkId !in progress.status.workIds) return@collect
                }
                _offline.value = _offline.value.copy(
                    isPreparing = progress.isRunning,
                    preparationDone = progress.done,
                    preparationTotal = progress.total,
                    isFullOfflinePreparation = progress.isFullOffline,
                    preparationStage = progress.stage,
                    preparationStatus = progress.status
                )
                if (
                    offlineAwaitingWork &&
                    progress.status.state != SyncOperationState.RUNNING &&
                    progress.status.state != SyncOperationState.IDLE
                ) {
                    offlineAwaitingWork = false
                    offlineWorkId = null
                }
            }
        }
        viewModelScope.launch {
            settings.observeRequestedSync().collect { status ->
                val expectedWorkId = resyncWorkId ?: return@collect
                if (expectedWorkId !in status.workIds) return@collect
                _sync.value = _sync.value.copy(resyncStatus = status)
                if (!resyncAwaitingWork) return@collect
                when (status.state) {
                    SyncOperationState.RUNNING -> {
                        _sync.value = _sync.value.copy(isResyncing = true)
                    }
                    SyncOperationState.SUCCEEDED,
                    SyncOperationState.FAILED,
                    SyncOperationState.CANCELLED -> {
                        resyncAwaitingWork = false
                        resyncWorkId = null
                        _sync.value = _sync.value.copy(isResyncing = false)
                    }
                    SyncOperationState.IDLE -> Unit
                }
            }
        }
    }

    fun prepareForOffline() {
        startOfflinePreparation(fullOffline = false)
    }

    fun prepareFullOffline() {
        startOfflinePreparation(fullOffline = true)
    }

    private fun startOfflinePreparation(fullOffline: Boolean) {
        if (
            _offline.value.isPreparing ||
            _uiState.value.isSwitchingBackend ||
            _uiState.value.isApplying ||
            _uiState.value.isTesting
        ) return
        offlineAwaitingWork = true
        offlineWorkId = null
        _offline.value = _offline.value.copy(
            isPreparing = true,
            preparationDone = 0,
            preparationTotal = 0,
            isFullOfflinePreparation = fullOffline,
            preparationStage = OfflinePreparationStage.SYNCING,
            preparationStatus = SyncOperationStatus(SyncOperationState.RUNNING)
        )
        val workId = if (fullOffline) {
            settings.prepareFullOffline()
        } else {
            settings.prepareForOffline()
        }
        if (workId == null) {
            offlineAwaitingWork = false
            offlineWorkId = null
            _offline.value = _offline.value.copy(
                isPreparing = false,
                isFullOfflinePreparation = false,
                preparationStage = OfflinePreparationStage.IDLE,
                preparationStatus = SyncOperationStatus(
                    state = SyncOperationState.FAILED,
                    error = SyncOperationError.CONFIGURE_SERVER
                )
            )
        } else {
            offlineAwaitingWork = true
            offlineWorkId = workId
            watchOfflinePreparation(workId)
        }
    }

    private fun watchOfflinePreparation(workId: SyncOperationId) {
        viewModelScope.launch {
            val terminalProgress = settings.observeOfflinePreparation().first { progress ->
                workId in progress.status.workIds &&
                    progress.status.state != SyncOperationState.RUNNING &&
                    progress.status.state != SyncOperationState.IDLE
            }
            if (offlineWorkId != workId) return@launch
            offlineAwaitingWork = false
            offlineWorkId = null
            _offline.value = _offline.value.copy(
                isPreparing = terminalProgress.isRunning,
                preparationDone = terminalProgress.done,
                preparationTotal = terminalProgress.total,
                isFullOfflinePreparation = terminalProgress.isFullOffline,
                preparationStage = terminalProgress.stage,
                preparationStatus = terminalProgress.status
            )
        }
    }

    fun onSyncIntervalChange(minutes: Int) {
        settings.setSyncIntervalMinutes(minutes)
        _sync.value = _sync.value.copy(intervalMinutes = settings.getSyncIntervalMinutes())
        rescheduleSync()
    }

    fun onUnmeteredOnlyChange(enabled: Boolean) {
        settings.setSyncOnUnmeteredOnly(enabled)
        _sync.value = _sync.value.copy(unmeteredOnly = enabled)
        rescheduleSync()
    }

    fun onSyncWhileRoamingChange(enabled: Boolean) {
        settings.setSyncWhileRoaming(enabled)
        _sync.value = _sync.value.copy(syncWhileRoaming = enabled)
        rescheduleSync()
    }

    fun onQuietHoursEnabledChange(enabled: Boolean) {
        settings.setQuietHoursEnabled(enabled)
        _sync.value = _sync.value.copy(quietHoursEnabled = enabled)
    }

    fun onQuietHoursChange(startHour: Int, endHour: Int) {
        settings.setQuietHours(startHour, endHour)
        _sync.value = _sync.value.copy(
            quietHoursStart = settings.getQuietHoursStartHour(),
            quietHoursEnd = settings.getQuietHoursEndHour()
        )
    }

    /** Constraints and period are fixed at registration, so a changed setting has to re-register. */
    private fun rescheduleSync() {
        settings.schedulePeriodicSync()
    }

    private fun currentSyncSettings() = SyncUiState(
        intervalMinutes = settings.getSyncIntervalMinutes(),
        unmeteredOnly = settings.getSyncOnUnmeteredOnly(),
        syncWhileRoaming = settings.getSyncWhileRoaming(),
        quietHoursEnabled = settings.getQuietHoursEnabled(),
        quietHoursStart = settings.getQuietHoursStartHour(),
        quietHoursEnd = settings.getQuietHoursEndHour()
    )

    /**
     * Throws the local copy away and fetches the account again from nothing.
     *
     * The escape hatch for a cache that disagrees with the server and cannot be argued out of it.
     * It is deliberately the same sequence a backend switch runs, minus the switch: in-flight work
     * is cancelled first, because a sync that started against the old rows would write them back
     * into the cache this just emptied.
     */
    fun resyncFromScratch() {
        if (
            _sync.value.isResyncing ||
            _uiState.value.isSwitchingBackend ||
            _uiState.value.isApplying ||
            _uiState.value.isTesting
        ) return
        viewModelScope.launch {
            resyncAwaitingWork = false
            resyncWorkId = null
            _sync.value = _sync.value.copy(
                isResyncing = true,
                resyncStatus = SyncOperationStatus(SyncOperationState.RUNNING),
                showResyncStatus = true
            )
            val cleared = runCatchingCancellable { settings.cancelAndClearBackendData() }
            // Rescheduled even when clearing failed: leaving the periodic worker deregistered
            // would turn a failed wipe into an app that never syncs again.
            settings.schedulePeriodicSync()
            if (cleared.isSuccess) {
                val workId = settings.resyncNow()
                if (workId != null) {
                    resyncWorkId = workId
                    resyncAwaitingWork = true
                    watchResync(workId)
                } else {
                    resyncAwaitingWork = false
                    _sync.value = _sync.value.copy(
                        isResyncing = false,
                        resyncStatus = SyncOperationStatus(
                            state = SyncOperationState.FAILED,
                            error = SyncOperationError.CONFIGURE_SERVER
                        )
                    )
                }
            } else {
                _sync.value = _sync.value.copy(
                    isResyncing = false,
                    resyncStatus = SyncOperationStatus(
                        state = SyncOperationState.FAILED,
                        error = SyncOperationError.CACHE_UPDATE_FAILED
                    )
                )
            }
            _uiState.value = currentSettings(serverDraft).withClearFailure(cleared.exceptionOrNull())
        }
    }

    private fun watchResync(workId: SyncOperationId) {
        viewModelScope.launch {
            val terminalStatus = settings.observeRequestedSync().first { status ->
                workId in status.workIds && (
                    status.state == SyncOperationState.SUCCEEDED ||
                        status.state == SyncOperationState.FAILED ||
                        status.state == SyncOperationState.CANCELLED
                    )
            }
            if (resyncWorkId != workId) return@launch
            resyncAwaitingWork = false
            resyncWorkId = null
            _sync.value = _sync.value.copy(
                isResyncing = false,
                resyncStatus = terminalStatus
            )
        }
    }

    fun onBacklogTargetChange(target: Int) {
        settings.setOfflineBacklogTarget(target)
        _offline.value = _offline.value.copy(backlogTarget = target)
    }

    fun onImageDownloadEnabledChange(enabled: Boolean) {
        settings.setImageDownloadEnabled(enabled)
        _offline.value = _offline.value.copy(imageDownloadEnabled = enabled)
    }

    fun onImageCacheBudgetChange(megabytes: Int) {
        settings.setImageCacheBudgetMegabytes(megabytes)
        _offline.value = _offline.value.copy(imageCacheBudgetMegabytes = megabytes)
    }

    private fun currentOfflineSettings() = OfflineUiState(
        backlogTarget = settings.getOfflineBacklogTarget(),
        imageDownloadEnabled = settings.getImageDownloadEnabled(),
        imageCacheBudgetMegabytes = settings.getImageCacheBudgetMegabytes()
    )

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
        if (
            _uiState.value.isSwitchingBackend ||
            _uiState.value.isApplying ||
            _uiState.value.isTesting
        ) return
        if (backendType == serverDraft.backendType) return
        if (settings.getLastSyncTimestamp() == 0L) {
            serverDraft = serverDraft.copy(backendType = backendType)
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
        val target = serverDraft.copy(backendType = backendType)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSwitchingBackend = true,
                pendingBackendType = null,
                isApplying = false
            )
            val switched = runCatchingCancellable { settings.applyBackendConfiguration(target) }
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
        if (
            _uiState.value.isSwitchingBackend ||
            _uiState.value.isApplying ||
            _uiState.value.isTesting
        ) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSwitchingBackend = true, signOutCompleted = false)
            val signedOut = runCatchingCancellable {
                val signedOutConfiguration = settings.getBackendConfiguration().copy(
                    freshRssUsername = "",
                    freshRssSecret = "",
                    minifluxSecret = ""
                )
                settings.applyBackendConfiguration(signedOutConfiguration)
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

    fun applyServerSettings() {
        if (
            !_uiState.value.hasAllFields ||
            _uiState.value.isSwitchingBackend ||
            _uiState.value.isApplying ||
            _uiState.value.isTesting
        ) return
        val draft = serverDraft
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isApplying = true, statusMessage = null)
            val previous = settings.getBackendConfiguration()
            val applied = runCatchingCancellable { settings.applyBackendConfiguration(draft) }
            settings.schedulePeriodicSync()
            if (applied.isSuccess) {
                settings.syncNow(
                    forceFullSync = draft != previous,
                    userVisible = true
                )
            }
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
        }
    }

    fun onSetupFinished(onFinished: () -> Unit = {}) {
        if (
            _uiState.value.isSwitchingBackend ||
            _uiState.value.isApplying ||
            _uiState.value.isTesting
        ) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isApplying = true, statusMessage = null)
            val draft = serverDraft
            val applied = runCatchingCancellable { settings.applyBackendConfiguration(draft) }
            if (applied.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isApplying = false,
                    statusMessage = UiText.Resource(R.string.settings_prepare_cache_failed)
                )
                return@launch
            }
            serverDraft = settings.getBackendConfiguration()
            settings.schedulePeriodicSync()
            settings.syncNow(forceFullSync = true, userVisible = true)
            _uiState.value = currentSettings(serverDraft)
            onFinished()
        }
    }

    fun testConnection() {
        if (
            _uiState.value.isSwitchingBackend ||
            _uiState.value.isApplying ||
            _uiState.value.isTesting
        ) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, statusMessage = null)
            val previous = settings.getBackendConfiguration()
            val draft = serverDraft
            val changed = draft != previous
            val result = runCatchingCancellable {
                settings.verifyConnection(draft)
            }
            val scheduled = if (result.isSuccess && !changed) {
                runCatchingCancellable { settings.schedulePeriodicSync() }
            } else {
                Result.success(Unit)
            }
            val failure = result.exceptionOrNull() ?: scheduled.exceptionOrNull()
            _uiState.value = _uiState.value.copy(
                isTesting = false,
                isConnected = result.isSuccess && scheduled.isSuccess,
                statusMessage = if (failure == null) {
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

    private fun updateServerDraft(transform: (BackendConfiguration) -> BackendConfiguration) {
        val previous = _uiState.value
        serverDraft = transform(serverDraft)
        _uiState.value = currentSettings(serverDraft)
            .cleared()
            .copy(
                pendingBackendType = previous.pendingBackendType,
                isSwitchingBackend = previous.isSwitchingBackend,
                isApplying = previous.isApplying
            )
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
