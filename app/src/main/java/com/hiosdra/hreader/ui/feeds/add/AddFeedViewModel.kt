package com.hiosdra.hreader.ui.feeds.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.remote.MinifluxApiRepository
import com.hiosdra.hreader.data.remote.dto.CreateFeedRequest
import com.hiosdra.hreader.data.remote.dto.DiscoverRequest
import com.hiosdra.hreader.data.remote.dto.DiscoverResponse
import com.hiosdra.hreader.ui.feeds.FeedsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// UI state for AddFeedScreen
data class AddFeedUiState(
    val feedUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val discoveredFeeds: List<DiscoverResponse> = emptyList(),
    val showFeedPicker: Boolean = false,
    val canSubmit: Boolean = false
)

class AddFeedViewModel(
    private val apiRepository: MinifluxApiRepository,
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

    private fun isLikelyUrl(text: String): Boolean {
        if (text.isBlank()) return false
        val candidate = text.trim()
        if (candidate.length < 4) return false
        val hasDot = candidate.contains('.')
        val noSpaces = !candidate.contains(' ') && !candidate.contains('\n')
        return hasDot && noSpaces
    }

    private fun extractErrorMessage(e: Exception): String {
        if (e is HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            if (!errorBody.isNullOrEmpty()) {
                try {
                    val json = JSONObject(errorBody)
                    val msg = json.optString("error_message")
                    if (msg.isNotBlank()) return msg
                } catch (_: Exception) {}
            }
            return e.message ?: "HTTP error"
        }
        if (e is UnknownHostException || e is ConnectException) return "Cannot reach server. Check backend availability and network connection."
        if (e is SocketTimeoutException) return "Connection timed out. Try again."
        return e.message ?: "Unknown error"
    }

    fun onFeedUrlChange(newUrl: String) {
        val valid = isLikelyUrl(newUrl)
        _uiState.value = _uiState.value.copy(feedUrl = newUrl, error = null, canSubmit = valid && newUrl.isNotBlank())
    }

    fun onAddFeed(
        onFeedAdded: () -> Unit,
        onNavigateBack: () -> Unit
    ) {
        val feedUrl = _uiState.value.feedUrl.trim()
        if (!_uiState.value.canSubmit) {
            _uiState.value = _uiState.value.copy(error = "Enter a valid feed or site URL.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val alreadyExists = feedsViewModel.uiState.value.feeds.any { it.feedUrl == feedUrl || it.siteUrl == feedUrl }
            if (alreadyExists) {
                _uiState.value = _uiState.value.copy(error = "Feed already exists.", isLoading = false)
                return@launch
            }
            val normalizedUrl = normalizeUrl(feedUrl)
            try {
                apiRepository.createFeed(
                    request = CreateFeedRequest(feed_url = normalizedUrl)
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                onFeedAdded()
                onNavigateBack()
            } catch (e: Exception) {
                // If failed, try discover
                try {
                    val discovered = apiRepository.discoverFeeds(
                        request = DiscoverRequest(url = normalizedUrl)
                    )
                    if (discovered.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(discoveredFeeds = discovered, showFeedPicker = true, isLoading = false)
                    } else {
                        _uiState.value = _uiState.value.copy(error = extractErrorMessage(e), isLoading = false)
                    }
                } catch (e2: Exception) {
                    _uiState.value = _uiState.value.copy(error = extractErrorMessage(e2), isLoading = false)
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
                apiRepository.createFeed(
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
