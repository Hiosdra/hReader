package com.hiosdra.hreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.repository.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ServerSettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val apiPassword: String = "",
    val isTesting: Boolean = false,
    val statusMessage: String? = null,
    val isConnected: Boolean = false
) {
    val hasAllFields: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && apiPassword.isNotBlank()
}

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val feedRepository: FeedRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ServerSettingsUiState(
            serverUrl = preferencesManager.getFreshRssServerUrl(),
            username = preferencesManager.getFreshRssUsername(),
            apiPassword = preferencesManager.getFreshRssApiPassword()
        )
    )
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    fun onServerUrlChange(serverUrl: String) {
        preferencesManager.setFreshRssServerUrl(serverUrl)
        _uiState.value = _uiState.value.copy(serverUrl = serverUrl).cleared()
    }

    fun onUsernameChange(username: String) {
        preferencesManager.setFreshRssUsername(username)
        _uiState.value = _uiState.value.copy(username = username).cleared()
    }

    fun onApiPasswordChange(apiPassword: String) {
        preferencesManager.setFreshRssApiPassword(apiPassword)
        _uiState.value = _uiState.value.copy(apiPassword = apiPassword).cleared()
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
                    onFailure = { it.message ?: "Could not connect to FreshRSS." }
                )
            )
        }
    }
}

private fun ServerSettingsUiState.cleared(): ServerSettingsUiState =
    copy(statusMessage = null, isConnected = false)
