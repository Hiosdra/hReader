package com.hiosdra.hreader.ui.feeds.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.model.DiscoveredFeed
import com.hiosdra.hreader.data.repository.FeedRepository
import com.hiosdra.hreader.ui.feeds.FeedsViewModel
import com.hiosdra.hreader.util.NetworkMonitor
import com.hiosdra.hreader.ui.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class AddFeedUiState(
    val feedUrl: String = "",
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val discoveredFeeds: List<DiscoveredFeed> = emptyList(),
    val showFeedPicker: Boolean = false,
    val canSubmit: Boolean = false
)

class AddFeedViewModel(
    private val feedRepository: FeedRepository,
    private val feedsViewModel: FeedsViewModel,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddFeedUiState())
    val uiState: StateFlow<AddFeedUiState> = _uiState.asStateFlow()

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }

    private fun isLikelyUrl(text: String): Boolean {
        if (text.isBlank()) return false
        val candidate = text.trim()
        if (candidate.length < 4) return false
        val hasDot = candidate.contains('.')
        val noSpaces = !candidate.contains(' ') && !candidate.contains('\n')
        return hasDot && noSpaces
    }

    private fun extractErrorMessage(e: Exception): UiText {
        if (e is HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            if (!errorBody.isNullOrEmpty()) {
                try {
                    val json = JSONObject(errorBody)
                    val msg = json.optString("error_message")
                    if (msg.isNotBlank()) return UiText.Plain(msg)
                } catch (_: Exception) {}
            }
            return UiText.Resource(R.string.feeds_add_error)
        }
        if (e is UnknownHostException || e is ConnectException) {
            return UiText.Resource(R.string.feeds_server_unreachable)
        }
        if (e is SocketTimeoutException) return UiText.Resource(R.string.feeds_connection_timeout)
        return UiText.Resource(R.string.feeds_add_error)
    }

    fun onFeedUrlChange(newUrl: String) {
        val valid = isLikelyUrl(newUrl)
        _uiState.value = _uiState.value.copy(feedUrl = newUrl, error = null, canSubmit = valid && newUrl.isNotBlank())
    }

    fun onAddFeed(onFeedAdded: () -> Unit, onNavigateBack: () -> Unit) {
        if (_uiState.value.isLoading) return
        if (!networkMonitor.isOnline.value) {
            _uiState.value = _uiState.value.copy(error = UiText.Resource(R.string.feeds_need_connection_subscribe))
            return
        }
        val feedUrl = _uiState.value.feedUrl.trim()
        if (!_uiState.value.canSubmit) {
            _uiState.value = _uiState.value.copy(error = UiText.Resource(R.string.feeds_invalid_url))
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val alreadyExists = feedsViewModel.uiState.value.feeds.any { it.feedUrl == feedUrl || it.siteUrl == feedUrl }
            if (alreadyExists) {
                _uiState.value = _uiState.value.copy(
                    error = UiText.Resource(R.string.feeds_already_subscribed),
                    isLoading = false
                )
                return@launch
            }
            val normalizedUrl = normalizeUrl(feedUrl)
            try {
                feedRepository.createFeed(normalizedUrl)
                _uiState.value = _uiState.value.copy(isLoading = false)
                onFeedAdded()
                onNavigateBack()
            } catch (e: Exception) {
                try {
                    val discovered = feedRepository.discoverFeeds(normalizedUrl)
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

    fun onSelectDiscoveredFeed(discovered: DiscoveredFeed, onFeedAdded: () -> Unit, onNavigateBack: () -> Unit) {
        if (_uiState.value.isLoading) return
        if (!networkMonitor.isOnline.value) {
            _uiState.value = _uiState.value.copy(error = UiText.Resource(R.string.feeds_need_connection_subscribe))
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val normalizedDiscoveredUrl = normalizeUrl(discovered.url)
                feedRepository.createFeed(normalizedDiscoveredUrl)
                _uiState.value = _uiState.value.copy(isLoading = false)
                onFeedAdded()
                onNavigateBack()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = extractErrorMessage(e), isLoading = false)
            }
        }
    }
}
