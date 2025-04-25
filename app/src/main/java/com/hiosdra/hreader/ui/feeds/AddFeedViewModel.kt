package com.hiosdra.hreader.ui.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.model.CreateFeedRequest
import com.hiosdra.hreader.data.model.DiscoverRequest
import com.hiosdra.hreader.data.model.DiscoverResponse
import com.hiosdra.hreader.data.remote.MinifluxApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

// UI state for AddFeedScreen
data class AddFeedUiState(
    val feedUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val discoveredFeeds: List<DiscoverResponse> = emptyList(),
    val showFeedPicker: Boolean = false
)

class AddFeedViewModel(
    private val apiService: MinifluxApiService,
    private val feedsViewModel: FeedsViewModel
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddFeedUiState())
    val uiState: StateFlow<AddFeedUiState> = _uiState.asStateFlow()

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun extractErrorMessage(e: Exception): String {
        if (e is HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            if (!errorBody.isNullOrEmpty()) {
                try {
                    val json = JSONObject(errorBody)
                    return json.optString("error_message", e.message ?: "Unknown error")
                } catch (_: Exception) {}
            }
        }
        return e.message ?: "Unknown error"
    }

    fun onFeedUrlChange(newUrl: String) {
        _uiState.value = _uiState.value.copy(feedUrl = newUrl, error = null)
    }

    fun onAddFeed(
        onFeedAdded: () -> Unit,
        onNavigateBack: () -> Unit
    ) {
        val feedUrl = _uiState.value.feedUrl
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val alreadyExists = feedsViewModel.uiState.value.feeds.any { it.feedUrl == feedUrl || it.siteUrl == feedUrl }
            if (alreadyExists) {
                _uiState.value = _uiState.value.copy(error = "Feed already exists.", isLoading = false)
                return@launch
            }
            val normalizedUrl = normalizeUrl(feedUrl)
            try {
                apiService.createFeed(
                    request = CreateFeedRequest(feed_url = normalizedUrl)
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                onFeedAdded()
                onNavigateBack()
            } catch (e: Exception) {
                // If failed, try discover
                try {
                    val discovered = apiService.discoverFeeds(
                        request = DiscoverRequest(url = normalizedUrl)
                    )
                    if (discovered.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(discoveredFeeds = discovered, showFeedPicker = true, isLoading = false)
                    } else {
                        _uiState.value = _uiState.value.copy(error = "No feeds discovered at this URL.", isLoading = false)
                    }
                } catch (e2: Exception) {
                    _uiState.value = _uiState.value.copy(error = "Failed to discover feeds: ${extractErrorMessage(e2)}", isLoading = false)
                }
            }
        }
    }

    fun onSelectDiscoveredFeed(
        discovered: DiscoverResponse,
        onFeedAdded: () -> Unit,
        onNavigateBack: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val normalizedDiscoveredUrl = normalizeUrl(discovered.url)
                apiService.createFeed(
                    request = CreateFeedRequest(feed_url = normalizedDiscoveredUrl)
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                onFeedAdded()
                onNavigateBack()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = extractErrorMessage(e), isLoading = false)
            }
        }
    }
}
