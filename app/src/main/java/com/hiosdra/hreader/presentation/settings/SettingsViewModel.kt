package com.hiosdra.hreader.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.sync.SyncDefaults
import com.hiosdra.hreader.core.application.usecase.settings.SettingsUseCase
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.core.domain.model.OfflineReadiness
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.core.application.sync.SyncOperationError
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import com.hiosdra.hreader.core.application.sync.OfflinePreparationStage
import com.hiosdra.hreader.presentation.text.UiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

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
    private var cacheOwnerCheckJob: Job? = null
    private val _uiState = MutableStateFlow(currentSettings())
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(settings.getOpenRouterApiKey())
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _aiModels = MutableStateFlow(AiModelsUiState(selectedModelId = settings.getAiModelId()))
    val aiModels: StateFlow<AiModelsUiState> = _aiModels.asStateFlow()

    private val _offline = MutableStateFlow(currentOfflineSettings())
    val offline: StateFlow<OfflineUiState> = _offline.asStateFlow()
    private var offlineAwaitingWork = false
    private var offlineWorkId: UUID? = null

    private val _sync = MutableStateFlow(currentSyncSettings())
    val sync: StateFlow<SyncUiState> = _sync.asStateFlow()
    private var resyncAwaitingWork = false
    private var resyncWorkId: UUID? = null

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

    private fun watchOfflinePreparation(workId: UUID) {
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
        viewModelScope.launch {
            resyncAwaitingWork = false
            resyncWorkId = null
            _sync.value = _sync.value.copy(
                isResyncing = true,
                resyncStatus = SyncOperationStatus(SyncOperationState.RUNNING),
                showResyncStatus = true
            )
            settings.cancelAllSync()
            val cleared = runCatchingCancellable { settings.clearBackendData() }
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
            _uiState.value = currentSettings().withClearFailure(cleared.exceptionOrNull())
        }
    }

    private fun watchResync(workId: UUID) {
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
            // In flight work belongs to the backend being left, and would write its articles back
            // into the cache that was just emptied.
            settings.cancelAllSync()
            val cleared = runCatchingCancellable { settings.clearBackendData() }
            if (cleared.isSuccess) {
                settings.setBackendType(backendType)
            }
            // The switch wipes everything downloaded, so the new backend is fetched from scratch
            // rather than leaving the reader with an empty list until the next scheduled run.
            settings.schedulePeriodicSync()
            if (cleared.isSuccess) {
                settings.syncNow(forceFullSync = true, userVisible = true)
            }
            _uiState.value = currentSettings().withClearFailure(cleared.exceptionOrNull())
        }
    }

    fun signOut() {
        val backendType = _uiState.value.backendType
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSwitchingBackend = true, signOutCompleted = false)
            settings.cancelAllSync()
            val cleared = runCatchingCancellable { settings.clearBackendData() }
            settings.setBackendSecret(backendType, "")
            if (backendType.requiresUsername) {
                settings.setFreshRssUsername("")
            }
            // Deregisters the periodic worker: without credentials every run wakes the radio only
            // to fail on a missing token, hourly, for as long as the app stays installed.
            settings.schedulePeriodicSync()
            _uiState.value = currentSettings()
                .withClearFailure(cleared.exceptionOrNull())
                .copy(signOutCompleted = cleared.isSuccess)
        }
    }

    fun onServerUrlChange(serverUrl: String) {
        settings.setServerUrl(_uiState.value.backendType, serverUrl)
        _uiState.value = _uiState.value.copy(serverUrl = serverUrl).cleared()
        scheduleCacheOwnerCheck()
    }

    fun onUsernameChange(username: String) {
        settings.setFreshRssUsername(username)
        _uiState.value = _uiState.value.copy(username = username).cleared()
        scheduleCacheOwnerCheck()
    }

    fun onSecretChange(secret: String) {
        settings.setBackendSecret(_uiState.value.backendType, secret)
        _uiState.value = _uiState.value.copy(secret = secret).cleared()
        scheduleCacheOwnerCheck()
    }

    private fun scheduleCacheOwnerCheck() {
        cacheOwnerCheckJob?.cancel()
        cacheOwnerCheckJob = viewModelScope.launch {
            delay(500)
            val result = runCatchingCancellable { settings.ensureCacheOwnerWhenConfigured() }
            if (result.getOrDefault(false)) {
                settings.cancelAllSync()
                _uiState.value = _uiState.value.copy(
                    statusMessage = UiText.Resource(R.string.settings_data_cleared_new_account)
                )
                settings.schedulePeriodicSync()
            } else if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = UiText.Resource(R.string.settings_cache_update_failed)
                )
            }
        }
    }

    /**
     * Credentials are stored as they are typed, but the periodic worker is only registered where
     * there is an account to sync — so finishing setup, or signing back in, has to say so. The
     * first sync is started here too rather than leaving a new install empty for an hour.
     */
    fun onSetupFinished() {
        viewModelScope.launch {
            val ownerCheck = runCatchingCancellable { settings.ensureCacheOwner() }
            if (ownerCheck.isFailure) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = UiText.Resource(R.string.settings_prepare_cache_failed)
                )
                return@launch
            }
            settings.schedulePeriodicSync()
            settings.syncNow(forceFullSync = true, userVisible = true)
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, statusMessage = null)
            val result = runCatchingCancellable { settings.verifyConnection() }
            val ownerCheck = if (result.isSuccess) {
                runCatchingCancellable { settings.ensureCacheOwner() }
            } else {
                Result.success(false)
            }
            if (result.isSuccess && ownerCheck.isSuccess) {
                settings.schedulePeriodicSync()
            }
            val failure = result.exceptionOrNull() ?: ownerCheck.exceptionOrNull()
            _uiState.value = _uiState.value.copy(
                isTesting = false,
                isConnected = result.isSuccess && ownerCheck.isSuccess,
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

    private fun currentSettings(): ServerSettingsUiState {
        val backendType = settings.getBackendType()
        return ServerSettingsUiState(
            backendType = backendType,
            serverUrl = settings.getServerUrl(backendType),
            username = settings.getFreshRssUsername(),
            secret = settings.getBackendSecret(backendType)
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
