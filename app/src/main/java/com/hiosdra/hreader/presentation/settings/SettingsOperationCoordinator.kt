package com.hiosdra.hreader.presentation.settings

import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.storage.StorageCleanupAction
import com.hiosdra.hreader.core.application.sync.OfflinePreparationStage
import com.hiosdra.hreader.core.application.sync.SyncMode
import com.hiosdra.hreader.core.application.sync.SyncOperationError
import com.hiosdra.hreader.core.application.sync.SyncOperationId
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import com.hiosdra.hreader.core.application.usecase.settings.SettingsUseCase
import com.hiosdra.hreader.core.application.usecase.settings.StorageUseCase
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.presentation.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SettingsOperationCoordinator(
    private val settings: SettingsUseCase,
    private val storageUseCase: StorageUseCase,
    private val scope: CoroutineScope
) {
    private val _offline = MutableStateFlow(currentOfflineSettings())
    val offline: StateFlow<OfflineUiState> = _offline.asStateFlow()
    private var offlineAwaitingOperation = false
    private var offlineOperationId: SyncOperationId? = null

    private val _sync = MutableStateFlow(currentSyncSettings())
    val sync: StateFlow<SyncUiState> = _sync.asStateFlow()
    private var resyncAwaitingOperation = false
    private var resyncOperationId: SyncOperationId? = null

    private val _storage = MutableStateFlow(StorageUiState())
    val storage: StateFlow<StorageUiState> = _storage.asStateFlow()

    init {
        scope.launch {
            settings.observeOfflineReadiness().collect { readiness ->
                _offline.update { it.copy(readiness = readiness) }
            }
        }
        scope.launch {
            settings.observeOfflinePreparation().collect { progress ->
                if (offlineAwaitingOperation) {
                    val expectedOperationId = offlineOperationId ?: return@collect
                    if (expectedOperationId !in progress.status.operationIds) return@collect
                }
                _offline.update { it.withPreparation(progress) }
                if (offlineAwaitingOperation && progress.status.state.isTerminal) {
                    offlineAwaitingOperation = false
                    offlineOperationId = null
                }
            }
        }
        scope.launch {
            settings.observeRequestedSync().collect { status ->
                val expectedOperationId = resyncOperationId ?: return@collect
                if (expectedOperationId !in status.operationIds) return@collect
                _sync.update { it.copy(resyncStatus = status) }
                if (!resyncAwaitingOperation) return@collect
                when (status.state) {
                    SyncOperationState.RUNNING -> _sync.update { it.copy(isResyncing = true) }
                    SyncOperationState.SUCCEEDED,
                    SyncOperationState.FAILED,
                    SyncOperationState.CANCELLED -> {
                        resyncAwaitingOperation = false
                        resyncOperationId = null
                        _sync.update { it.copy(isResyncing = false) }
                    }
                    SyncOperationState.IDLE -> Unit
                }
            }
        }
        refreshStorage()
    }

    fun prepareForOffline() {
        startOfflinePreparation(fullOffline = false)
    }

    fun prepareFullOffline() {
        startOfflinePreparation(fullOffline = true)
    }

    fun prepareTravelMode(fullOffline: Boolean) {
        startOfflinePreparation(fullOffline = fullOffline, travelMode = true)
    }

    private fun startOfflinePreparation(fullOffline: Boolean, travelMode: Boolean = false) {
        offlineAwaitingOperation = true
        offlineOperationId = null
        _offline.update {
            it.copy(
                isPreparing = true,
                preparationDone = 0,
                preparationTotal = 0,
                isFullOfflinePreparation = fullOffline,
                preparationStage = OfflinePreparationStage.SYNCING,
                preparationStatus = SyncOperationStatus(SyncOperationState.RUNNING)
            )
        }
        val operationId = when {
            travelMode -> settings.prepareTravelMode(fullOffline)
            fullOffline -> settings.prepareFullOffline()
            else -> settings.prepareForOffline()
        }
        if (operationId == null) {
            offlineAwaitingOperation = false
            offlineOperationId = null
            _offline.update {
                it.copy(
                    isPreparing = false,
                    isFullOfflinePreparation = false,
                    preparationStage = OfflinePreparationStage.IDLE,
                    preparationStatus = SyncOperationStatus(
                        state = SyncOperationState.FAILED,
                        error = SyncOperationError.CONFIGURE_SERVER
                    )
                )
            }
        } else {
            offlineOperationId = operationId
            watchOfflinePreparation(operationId)
        }
    }

    private fun watchOfflinePreparation(operationId: SyncOperationId) {
        scope.launch {
            val terminalProgress = settings.observeOfflinePreparation().first { progress ->
                operationId in progress.status.operationIds &&
                    progress.status.state != SyncOperationState.RUNNING &&
                    progress.status.state != SyncOperationState.IDLE
            }
            if (offlineOperationId != operationId) return@launch
            offlineAwaitingOperation = false
            offlineOperationId = null
            _offline.update { it.withPreparation(terminalProgress) }
        }
    }

    fun onSyncIntervalChange(minutes: Int) {
        updateSync(
            persist = { settings.setSyncIntervalMinutes(minutes) },
            update = { it.copy(intervalMinutes = settings.getSyncIntervalMinutes()) },
            reschedule = true
        )
    }

    fun onSyncModeChange(mode: SyncMode) {
        updateSync(
            persist = { settings.setSyncMode(mode) },
            update = { it.copy(syncMode = settings.getSyncMode()) }
        )
    }

    fun onUnmeteredOnlyChange(enabled: Boolean) {
        updateSync(
            persist = { settings.setSyncOnUnmeteredOnly(enabled) },
            update = { it.copy(unmeteredOnly = enabled) },
            reschedule = true
        )
    }

    fun onSyncWhileRoamingChange(enabled: Boolean) {
        updateSync(
            persist = { settings.setSyncWhileRoaming(enabled) },
            update = { it.copy(syncWhileRoaming = enabled) },
            reschedule = true
        )
    }

    fun onQuietHoursEnabledChange(enabled: Boolean) {
        updateSync(
            persist = { settings.setQuietHoursEnabled(enabled) },
            update = { it.copy(quietHoursEnabled = enabled) }
        )
    }

    fun onQuietHoursChange(startHour: Int, endHour: Int) {
        updateSync(
            persist = { settings.setQuietHours(startHour, endHour) },
            update = {
                it.copy(
                    quietHoursStart = settings.getQuietHoursStartHour(),
                    quietHoursEnd = settings.getQuietHoursEndHour()
                )
            }
        )
    }

    private fun updateSync(
        persist: () -> Unit,
        update: (SyncUiState) -> SyncUiState,
        reschedule: Boolean = false
    ) {
        persist()
        _sync.update(update)
        if (reschedule) settings.schedulePeriodicSync()
    }

    fun resyncFromScratch(onServerStateUpdated: (Throwable?) -> Unit) {
        scope.launch {
            resyncAwaitingOperation = false
            resyncOperationId = null
            _sync.update {
                it.copy(
                    isResyncing = true,
                    resyncStatus = SyncOperationStatus(SyncOperationState.RUNNING),
                    showResyncStatus = true
                )
            }
            val cleared = runCatchingCancellable { settings.cancelAndClearBackendData() }
            settings.schedulePeriodicSync()
            if (cleared.isSuccess) {
                val operationId = settings.resyncNow()
                if (operationId != null) {
                    resyncOperationId = operationId
                    resyncAwaitingOperation = true
                    watchResync(operationId)
                } else {
                    resyncAwaitingOperation = false
                    _sync.update {
                        it.copy(
                            isResyncing = false,
                            resyncStatus = SyncOperationStatus(
                                state = SyncOperationState.FAILED,
                                error = SyncOperationError.CONFIGURE_SERVER
                            )
                        )
                    }
                }
            } else {
                _sync.update {
                    it.copy(
                        isResyncing = false,
                        resyncStatus = SyncOperationStatus(
                            state = SyncOperationState.FAILED,
                            error = SyncOperationError.CACHE_UPDATE_FAILED
                        )
                    )
                }
            }
            onServerStateUpdated(cleared.exceptionOrNull())
        }
    }

    private fun watchResync(operationId: SyncOperationId) {
        scope.launch {
            val terminalStatus = settings.observeRequestedSync().first { status ->
                operationId in status.operationIds && status.state.isTerminal
            }
            if (resyncOperationId != operationId) return@launch
            resyncAwaitingOperation = false
            resyncOperationId = null
            _sync.update {
                it.copy(
                    isResyncing = false,
                    resyncStatus = terminalStatus
                )
            }
        }
    }

    fun onBacklogTargetChange(target: Int) {
        settings.setOfflineBacklogTarget(target)
        _offline.update { it.copy(backlogTarget = target) }
    }

    fun onImageDownloadEnabledChange(enabled: Boolean) {
        settings.setImageDownloadEnabled(enabled)
        _offline.update { it.copy(imageDownloadEnabled = enabled) }
    }

    fun onImageCacheBudgetChange(megabytes: Int) {
        settings.setImageCacheBudgetMegabytes(megabytes)
        _offline.update { it.copy(imageCacheBudgetMegabytes = megabytes) }
    }

    fun refreshStorage() {
        if (_storage.value.cleanupAction != null || _storage.value.isLoading) return
        scope.launch {
            _storage.update { it.copy(isLoading = true, error = null) }
            val result = runCatchingCancellable { storageUseCase.inspect() }
            _storage.value = result.fold(
                onSuccess = { snapshot -> StorageUiState(snapshot = snapshot) },
                onFailure = {
                    _storage.value.copy(
                        isLoading = false,
                        error = UiText.Resource(R.string.storage_refresh_failed)
                    )
                }
            )
        }
    }

    fun cleanupStorage(action: StorageCleanupAction) {
        if (_storage.value.cleanupAction != null || _storage.value.isLoading) return
        scope.launch {
            _storage.update {
                it.copy(
                    cleanupAction = action,
                    cleanupProgress = null,
                    cleanupResult = null,
                    error = null
                )
            }
            val result = runCatchingCancellable {
                storageUseCase.cleanup(action) { progress ->
                    _storage.update { it.copy(cleanupProgress = progress) }
                }
            }
            val refreshedStorage = runCatchingCancellable { storageUseCase.inspect() }
            _storage.update {
                it.copy(
                    snapshot = refreshedStorage.getOrNull(),
                    isLoading = false,
                    cleanupAction = null,
                    cleanupProgress = null,
                    cleanupResult = result.getOrNull(),
                    error = when {
                        result.isFailure -> UiText.Resource(R.string.storage_cleanup_failed)
                        refreshedStorage.isFailure -> UiText.Resource(R.string.storage_refresh_failed)
                        else -> null
                    }
                )
            }
        }
    }

    private fun currentOfflineSettings() = OfflineUiState(
        backlogTarget = settings.getOfflineBacklogTarget(),
        imageDownloadEnabled = settings.getImageDownloadEnabled(),
        imageCacheBudgetMegabytes = settings.getImageCacheBudgetMegabytes()
    )

    private fun currentSyncSettings() = SyncUiState(
        intervalMinutes = settings.getSyncIntervalMinutes(),
        syncMode = settings.getSyncMode(),
        unmeteredOnly = settings.getSyncOnUnmeteredOnly(),
        syncWhileRoaming = settings.getSyncWhileRoaming(),
        quietHoursEnabled = settings.getQuietHoursEnabled(),
        quietHoursStart = settings.getQuietHoursStartHour(),
        quietHoursEnd = settings.getQuietHoursEndHour()
    )
}

private val SyncOperationState.isTerminal: Boolean
    get() = this == SyncOperationState.SUCCEEDED ||
        this == SyncOperationState.FAILED ||
        this == SyncOperationState.CANCELLED
