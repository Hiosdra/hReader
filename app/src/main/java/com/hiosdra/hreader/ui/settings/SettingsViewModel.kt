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
import com.hiosdra.hreader.worker.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ServerSettingsUiState(
    val backendType: BackendType = BackendType.FRESHRSS,
    val serverUrl: String = "",
    val username: String = "",
    val secret: String = "",
    val isTesting: Boolean = false,
    val statusMessage: String? = null,
    val isConnected: Boolean = false,
    val pendingBackendType: BackendType? = null,
    val isSwitchingBackend: Boolean = false
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
}

data class OfflineUiState(
    val readiness: OfflineReadiness = OfflineReadiness(),
    val isPreparing: Boolean = false
)

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val feedRepository: FeedRepository,
    private val aiModelRepository: AiModelRepository,
    private val localCacheRepository: LocalCacheRepository,
    private val offlineReadinessRepository: OfflineReadinessRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {
    private val _uiState = MutableStateFlow(currentSettings())
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(preferencesManager.getOpenRouterApiKey())
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _aiModels = MutableStateFlow(AiModelsUiState(selectedModelId = preferencesManager.getAiModelId()))
    val aiModels: StateFlow<AiModelsUiState> = _aiModels.asStateFlow()

    private val _offline = MutableStateFlow(OfflineUiState())
    val offline: StateFlow<OfflineUiState> = _offline.asStateFlow()

    init {
        loadAiModels()
        viewModelScope.launch {
            offlineReadinessRepository.observe().collect { readiness ->
                _offline.value = _offline.value.copy(readiness = readiness)
            }
        }
        viewModelScope.launch {
            syncScheduler.isPreparingForOffline().collect { preparing ->
                _offline.value = _offline.value.copy(isPreparing = preparing)
            }
        }
    }

    fun prepareForOffline() {
        _offline.value = _offline.value.copy(isPreparing = true)
        syncScheduler.prepareForOffline()
    }


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
            val cleared = runCatching { localCacheRepository.clearBackendData() }
            if (cleared.isSuccess) {
                preferencesManager.setBackendType(backendType)
            }
            _uiState.value = currentSettings().withClearFailure(cleared.exceptionOrNull())
        }
    }

    fun signOut() {
        val backendType = _uiState.value.backendType
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSwitchingBackend = true)
            val cleared = runCatching { localCacheRepository.clearBackendData() }
            preferencesManager.setBackendSecret(backendType, "")
            if (backendType.requiresUsername) {
                preferencesManager.setFreshRssUsername("")
            }
            _uiState.value = currentSettings().withClearFailure(cleared.exceptionOrNull())
        }
    }

    fun onServerUrlChange(serverUrl: String) {
        preferencesManager.setServerUrl(_uiState.value.backendType, serverUrl)
        _uiState.value = _uiState.value.copy(serverUrl = serverUrl).cleared()
    }

    fun onUsernameChange(username: String) {
        preferencesManager.setFreshRssUsername(username)
        _uiState.value = _uiState.value.copy(username = username).cleared()
    }

    fun onSecretChange(secret: String) {
        preferencesManager.setBackendSecret(_uiState.value.backendType, secret)
        _uiState.value = _uiState.value.copy(secret = secret).cleared()
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, statusMessage = null)
            val result = runCatching { feedRepository.verifyConnection() }
            _uiState.value = _uiState.value.copy(
                isTesting = false,
                isConnected = result.isSuccess,
                statusMessage = result.fold(
                    onSuccess = { "Connected. Found $it subscriptions." },
                    onFailure = { it.message ?: "Could not connect to the server." }
                )
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
