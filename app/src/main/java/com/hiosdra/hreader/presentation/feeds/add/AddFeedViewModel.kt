package com.hiosdra.hreader.presentation.feeds.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.exception.FeedOperationException
import com.hiosdra.hreader.core.application.exception.FeedOperationFailureReason
import com.hiosdra.hreader.core.application.usecase.feeds.FeedUseCase
import com.hiosdra.hreader.core.domain.model.DiscoveredFeed
import com.hiosdra.hreader.presentation.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddFeedUiState(
    val feedUrl: String = "",
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val discoveredFeeds: List<DiscoveredFeed> = emptyList(),
    val showFeedPicker: Boolean = false,
    val canSubmit: Boolean = false
)

class AddFeedViewModel(
    private val feeds: FeedUseCase
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

    private fun extractErrorMessage(exception: Exception): UiText {
        val feedException = exception as? FeedOperationException
            ?: return UiText.Resource(R.string.feeds_add_error)
        feedException.serverMessage?.takeIf { it.isNotBlank() }?.let { return UiText.Plain(it) }
        return UiText.Resource(
            when (feedException.reason) {
                FeedOperationFailureReason.UNREACHABLE -> R.string.feeds_server_unreachable
                FeedOperationFailureReason.TIMEOUT -> R.string.feeds_connection_timeout
                FeedOperationFailureReason.SERVER,
                FeedOperationFailureReason.UNKNOWN -> R.string.feeds_add_error
            }
        )
    }

    fun onFeedUrlChange(newUrl: String) {
        val valid = isLikelyUrl(newUrl)
        _uiState.value = _uiState.value.copy(feedUrl = newUrl, error = null, canSubmit = valid && newUrl.isNotBlank())
    }

    fun onAddFeed(onFeedAdded: () -> Unit, onNavigateBack: () -> Unit) {
        if (_uiState.value.isLoading) return
        if (!feeds.isOnline.value) {
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
            val alreadyExists = feeds.getCachedFeeds().any { it.feedUrl == feedUrl || it.siteUrl == feedUrl }
            if (alreadyExists) {
                _uiState.value = _uiState.value.copy(
                    error = UiText.Resource(R.string.feeds_already_subscribed),
                    isLoading = false
                )
                return@launch
            }
            val normalizedUrl = normalizeUrl(feedUrl)
            try {
                feeds.createFeed(normalizedUrl)
                _uiState.value = _uiState.value.copy(isLoading = false)
                onFeedAdded()
                onNavigateBack()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                try {
                    val discovered = feeds.discoverFeeds(normalizedUrl)
                    if (discovered.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(discoveredFeeds = discovered, showFeedPicker = true, isLoading = false)
                    } else {
                        _uiState.value = _uiState.value.copy(error = extractErrorMessage(e), isLoading = false)
                    }
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    _uiState.value = _uiState.value.copy(error = extractErrorMessage(e2), isLoading = false)
                }
            }
        }
    }

    fun onSelectDiscoveredFeed(discovered: DiscoveredFeed, onFeedAdded: () -> Unit, onNavigateBack: () -> Unit) {
        if (_uiState.value.isLoading) return
        if (!feeds.isOnline.value) {
            _uiState.value = _uiState.value.copy(error = UiText.Resource(R.string.feeds_need_connection_subscribe))
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val normalizedDiscoveredUrl = normalizeUrl(discovered.url)
                feeds.createFeed(normalizedDiscoveredUrl)
                _uiState.value = _uiState.value.copy(isLoading = false)
                onFeedAdded()
                onNavigateBack()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = extractErrorMessage(e), isLoading = false)
            }
        }
    }
}
