package com.hiosdra.hreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.ai.AiModel
import com.hiosdra.hreader.data.ai.AiModelRepository
import com.hiosdra.hreader.data.local.repository.OfflineReadinessRepository
import com.hiosdra.hreader.data.model.BackendType
import com.hiosdra.hreader.data.model.OfflineReadiness
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.repository.FeedRepository
import com.hiosdra.hreader.data.repository.LocalCacheRepository
import com.hiosdra.hreader.worker.SyncOperationState
import com.hiosdra.hreader.worker.SyncOperationStatus
import com.hiosdra.hreader.worker.SyncScheduler
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
    val statusMessage: String? = null,
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
    val error: String? = null
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
    val preparationStatus: SyncOperationStatus = SyncOperationStatus()
) {
    /** Null while the worker has not reported counts yet, which reads as indeterminate. */
    val preparationProgress: Float?
        get() = if (preparationTotal > 0) preparationDone.toFloat() / preparationTotal else null
}

data class SyncUiState(
    val intervalMinutes: Int = PreferencesManager.DEFAULT_SYNC_INTERVAL_MINUTES,
    val unmeteredOnly: Boolean = false,
    val syncWhileRoaming: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = PreferencesManager.DEFAULT_QUIET_HOURS_START,
    val quietHoursEnd: Int = PreferencesManager.DEFAULT_QUIET_HOURS_END,
    val isResyncing: Boolean = false,
    val resyncStatus: SyncOperationStatus = SyncOperationStatus(),
    val showResyncStatus: Boolean = false
)

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val feedRepository: FeedRepository,
    private val aiModelRepository: AiModelRepository,
    private val localCacheRepository: LocalCacheRepository,
    private val offlineReadinessRepository: OfflineReadinessRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {
    private var cacheOwnerCheckJob: Job? = null
    private val _uiState = MutableStateFlow(currentSettings())
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(preferencesManager.getOpenRouterApiKey())
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _aiModels = MutableStateFlow(AiModelsUiState(selectedModelId = preferencesManager.getAiModelId()))
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
            offlineReadinessRepository.observe().collect { readiness ->
                _offline.value = _offline.value.copy(readiness = readiness)
            }
        }
        viewModelScope.launch {
            syncScheduler.observeOfflinePreparation().collect { progress ->
                if (offlineAwaitingWork) {
                    val expectedWorkId = offlineWorkId ?: return@collect
                    if (expectedWorkId !in progress.status.workIds) return@collect
                }
                _offline.value = _offline.value.copy(
                    isPreparing = progress.isRunning,
                    preparationDone = progress.done,
                    preparationTotal = progress.total,
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
            syncScheduler.observeRequestedSync().collect { status ->
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
        offlineAwaitingWork = true
        offlineWorkId = null
        _offline.value = _offline.value.copy(
            isPreparing = true,
            preparationDone = 0,
            preparationTotal = 0,
            preparationStatus = SyncOperationStatus(SyncOperationState.RUNNING)
        )
        val workId = syncScheduler.prepareForOffline()
        if (workId == null) {
            offlineAwaitingWork = false
            offlineWorkId = null
            _offline.value = _offline.value.copy(
                isPreparing = false,
                preparationStatus = SyncOperationStatus(
                    state = SyncOperationState.FAILED,
                    errorMessage = "Configure a feed server first."
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
            val terminalProgress = syncScheduler.observeOfflinePreparation().first { progress ->
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
                preparationStatus = terminalProgress.status
            )
        }
    }

    fun onSyncIntervalChange(minutes: Int) {
        preferencesManager.setSyncIntervalMinutes(minutes)
        _sync.value = _sync.value.copy(intervalMinutes = preferencesManager.getSyncIntervalMinutes())
        rescheduleSync()
    }

    fun onUnmeteredOnlyChange(enabled: Boolean) {
        preferencesManager.setSyncOnUnmeteredOnly(enabled)
        _sync.value = _sync.value.copy(unmeteredOnly = enabled)
        rescheduleSync()
    }

    fun onSyncWhileRoamingChange(enabled: Boolean) {
        preferencesManager.setSyncWhileRoaming(enabled)
        _sync.value = _sync.value.copy(syncWhileRoaming = enabled)
        rescheduleSync()
    }

    fun onQuietHoursEnabledChange(enabled: Boolean) {
        preferencesManager.setQuietHoursEnabled(enabled)
        _sync.value = _sync.value.copy(quietHoursEnabled = enabled)
    }

    fun onQuietHoursChange(startHour: Int, endHour: Int) {
        preferencesManager.setQuietHours(startHour, endHour)
        _sync.value = _sync.value.copy(
            quietHoursStart = preferencesManager.getQuietHoursStartHour(),
            quietHoursEnd = preferencesManager.getQuietHoursEndHour()
        )
    }

    /** Constraints and period are fixed at registration, so a changed setting has to re-register. */
    private fun rescheduleSync() {
        syncScheduler.schedulePeriodicSync()
    }

    private fun currentSyncSettings() = SyncUiState(
        intervalMinutes = preferencesManager.getSyncIntervalMinutes(),
        unmeteredOnly = preferencesManager.getSyncOnUnmeteredOnly(),
        syncWhileRoaming = preferencesManager.getSyncWhileRoaming(),
        quietHoursEnabled = preferencesManager.getQuietHoursEnabled(),
        quietHoursStart = preferencesManager.getQuietHoursStartHour(),
        quietHoursEnd = preferencesManager.getQuietHoursEndHour()
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
            syncScheduler.cancelAllSync()
            val cleared = runCatching { localCacheRepository.clearBackendData() }
            // Rescheduled even when clearing failed: leaving the periodic worker deregistered
            // would turn a failed wipe into an app that never syncs again.
            syncScheduler.schedulePeriodicSync()
            if (cleared.isSuccess) {
                val workId = syncScheduler.resyncNow()
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
                            errorMessage = "Configure a feed server first."
                        )
                    )
                }
            } else {
                _sync.value = _sync.value.copy(
                    isResyncing = false,
                    resyncStatus = SyncOperationStatus(
                        state = SyncOperationState.FAILED,
                        errorMessage = cleared.exceptionOrNull()?.message
                    )
                )
            }
            _uiState.value = currentSettings().withClearFailure(cleared.exceptionOrNull())
        }
    }

    private fun watchResync(workId: UUID) {
        viewModelScope.launch {
            val terminalStatus = syncScheduler.observeRequestedSync().first { status ->
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
        preferencesManager.setOfflineBacklogTarget(target)
        _offline.value = _offline.value.copy(backlogTarget = target)
    }

    fun onImageDownloadEnabledChange(enabled: Boolean) {
        preferencesManager.setImageDownloadEnabled(enabled)
        _offline.value = _offline.value.copy(imageDownloadEnabled = enabled)
    }

    fun onImageCacheBudgetChange(megabytes: Int) {
        preferencesManager.setImageCacheBudgetMegabytes(megabytes)
        _offline.value = _offline.value.copy(imageCacheBudgetMegabytes = megabytes)
    }

    private fun currentOfflineSettings() = OfflineUiState(
        backlogTarget = preferencesManager.getOfflineBacklogTarget(),
        imageDownloadEnabled = preferencesManager.getImageDownloadEnabled(),
        imageCacheBudgetMegabytes = preferencesManager.getImageCacheBudgetMegabytes()
    )

    fun onOpenRouterApiKeyChange(apiKey: String) {
        preferencesManager.setOpenRouterApiKey(apiKey)
        _openRouterApiKey.value = apiKey
    }

    fun loadAiModels(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _aiModels.value = _aiModels.value.copy(isLoading = true, error = null)
            val result = runCatching { aiModelRepository.getModels(forceRefresh) }
            _aiModels.value = result.fold(
                onSuccess = { _aiModels.value.copy(isLoading = false, models = it) },
                onFailure = {
                    _aiModels.value.copy(
                        isLoading = false,
                        error = it.message ?: "Could not load the model list from OpenRouter."
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
        preferencesManager.setAiModelId(model.id)
        _aiModels.value = _aiModels.value.copy(selectedModelId = model.id)
    }

    fun onBackendTypeRequested(backendType: BackendType) {
        if (backendType == _uiState.value.backendType) return
        if (preferencesManager.getLastSyncTimestamp() == 0L) {
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
            syncScheduler.cancelAllSync()
            val cleared = runCatching { localCacheRepository.clearBackendData() }
            if (cleared.isSuccess) {
                preferencesManager.setBackendType(backendType)
            }
            // The switch wipes everything downloaded, so the new backend is fetched from scratch
            // rather than leaving the reader with an empty list until the next scheduled run.
            syncScheduler.schedulePeriodicSync()
            if (cleared.isSuccess) {
                syncScheduler.syncNow(forceFullSync = true, userVisible = true)
            }
            _uiState.value = currentSettings().withClearFailure(cleared.exceptionOrNull())
        }
    }

    fun signOut() {
        val backendType = _uiState.value.backendType
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSwitchingBackend = true, signOutCompleted = false)
            syncScheduler.cancelAllSync()
            val cleared = runCatching { localCacheRepository.clearBackendData() }
            preferencesManager.setBackendSecret(backendType, "")
            if (backendType.requiresUsername) {
                preferencesManager.setFreshRssUsername("")
            }
            // Deregisters the periodic worker: without credentials every run wakes the radio only
            // to fail on a missing token, hourly, for as long as the app stays installed.
            syncScheduler.schedulePeriodicSync()
            _uiState.value = currentSettings()
                .withClearFailure(cleared.exceptionOrNull())
                .copy(signOutCompleted = cleared.isSuccess)
        }
    }

    fun onServerUrlChange(serverUrl: String) {
        preferencesManager.setServerUrl(_uiState.value.backendType, serverUrl)
        _uiState.value = _uiState.value.copy(serverUrl = serverUrl).cleared()
        scheduleCacheOwnerCheck()
    }

    fun onUsernameChange(username: String) {
        preferencesManager.setFreshRssUsername(username)
        _uiState.value = _uiState.value.copy(username = username).cleared()
        scheduleCacheOwnerCheck()
    }

    fun onSecretChange(secret: String) {
        preferencesManager.setBackendSecret(_uiState.value.backendType, secret)
        _uiState.value = _uiState.value.copy(secret = secret).cleared()
        scheduleCacheOwnerCheck()
    }

    private fun scheduleCacheOwnerCheck() {
        cacheOwnerCheckJob?.cancel()
        cacheOwnerCheckJob = viewModelScope.launch {
            delay(500)
            val result = runCatching { localCacheRepository.ensureCacheOwnerWhenConfigured() }
            if (result.getOrDefault(false)) {
                syncScheduler.cancelAllSync()
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Downloaded data was cleared for the new account."
                )
                syncScheduler.schedulePeriodicSync()
            } else if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = result.exceptionOrNull()?.message
                        ?: "Could not update the local cache."
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
            val ownerCheck = runCatching { localCacheRepository.ensureCacheOwner() }
            if (ownerCheck.isFailure) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = ownerCheck.exceptionOrNull()?.message
                        ?: "Could not prepare the local cache."
                )
                return@launch
            }
            syncScheduler.schedulePeriodicSync()
            syncScheduler.syncNow(forceFullSync = true, userVisible = true)
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, statusMessage = null)
            val result = runCatching { feedRepository.verifyConnection() }
            val ownerCheck = if (result.isSuccess) {
                runCatching { localCacheRepository.ensureCacheOwner() }
            } else {
                Result.success(false)
            }
            if (result.isSuccess && ownerCheck.isSuccess) {
                syncScheduler.schedulePeriodicSync()
            }
            val failure = result.exceptionOrNull() ?: ownerCheck.exceptionOrNull()
            _uiState.value = _uiState.value.copy(
                isTesting = false,
                isConnected = result.isSuccess && ownerCheck.isSuccess,
                statusMessage = if (failure == null) {
                    "Connected. Found ${result.getOrThrow()} subscriptions."
                } else {
                    failure.message ?: "Could not connect to the server."
                }
            )
        }
    }

    private fun currentSettings(): ServerSettingsUiState {
        val backendType = preferencesManager.getBackendType()
        return ServerSettingsUiState(
            backendType = backendType,
            serverUrl = preferencesManager.getServerUrl(backendType),
            username = preferencesManager.getFreshRssUsername(),
            secret = preferencesManager.getBackendSecret(backendType)
        )
    }
}

private fun ServerSettingsUiState.cleared(): ServerSettingsUiState =
    copy(statusMessage = null, isConnected = false)

private fun ServerSettingsUiState.withClearFailure(failure: Throwable?): ServerSettingsUiState =
    if (failure == null) this
    else copy(statusMessage = failure.message ?: "Could not clear the downloaded articles.")
