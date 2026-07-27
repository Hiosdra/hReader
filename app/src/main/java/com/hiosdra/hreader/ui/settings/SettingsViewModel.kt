package com.hiosdra.hreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.model.BackendType
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.repository.FeedRepository
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
    val isConnected: Boolean = false
) {
    val hasAllFields: Boolean
        get() = serverUrl.isNotBlank() &&
            secret.isNotBlank() &&
            (!backendType.requiresUsername || username.isNotBlank())
}

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val feedRepository: FeedRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(currentSettings())
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(preferencesManager.getOpenRouterApiKey())
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    fun onOpenRouterApiKeyChange(apiKey: String) {
        preferencesManager.setOpenRouterApiKey(apiKey)
        _openRouterApiKey.value = apiKey
    }

    fun onBackendTypeChange(backendType: BackendType) {
        if (backendType == _uiState.value.backendType) return
        preferencesManager.setBackendType(backendType)
        _uiState.value = currentSettings()
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
