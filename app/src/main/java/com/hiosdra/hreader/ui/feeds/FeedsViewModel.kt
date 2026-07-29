package com.hiosdra.hreader.ui.feeds

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.repository.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedsUiState(
    val feeds: List<Feed> = emptyList(),
    val filteredFeeds: List<Feed> = emptyList(),
    val searchQuery: String = "",
    val unreadCounts: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class FeedsViewModel(private val feedRepository: FeedRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedsUiState())
    val uiState: StateFlow<FeedsUiState> = _uiState.asStateFlow()

    init {
        loadFeeds()
    }

    fun reload() {
        loadFeeds()
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        filterFeeds()
    }

    private fun filterFeeds() {
        val query = _uiState.value.searchQuery.lowercase().trim()
        val filteredFeeds = if (query.isEmpty()) {
            _uiState.value.feeds
        } else {
            _uiState.value.feeds.filter { feed ->
                feed.title.lowercase().contains(query) ||
                feed.siteUrl?.lowercase()?.contains(query) == true
            }
        }
        _uiState.value = _uiState.value.copy(filteredFeeds = filteredFeeds)
    }

    /**
     * The cache first, the server second. Subscriptions change rarely and the local copy is
     * complete, so a failed refresh is only an error when there is nothing cached to show.
     */
    private fun loadFeeds() {
        if (_uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val cachedFeeds = runCatching { feedRepository.getCachedFeeds() }.getOrDefault(emptyList())
            val cachedCounts = runCatching { feedRepository.getCachedUnreadCounts() }.getOrDefault(emptyMap())
            if (cachedFeeds.isNotEmpty()) {
                publish(cachedFeeds, cachedCounts)
            }

            val refreshed = runCatching { feedRepository.refreshFeeds() }
            refreshed.fold(
                onSuccess = { feeds ->
                    val counts = runCatching { feedRepository.getUnreadCounts() }.getOrDefault(cachedCounts)
                    publish(feeds, counts)
                },
                onFailure = { failure ->
                    Log.w("FeedsViewModel", "Falling back to the cached subscriptions", failure)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Error loading feeds: ${failure.message}".takeIf { cachedFeeds.isEmpty() }
                    )
                }
            )
        }
    }

    private fun publish(feeds: List<Feed>, unreadCounts: Map<Long, Int>) {
        _uiState.value = _uiState.value.copy(
            feeds = feeds,
            filteredFeeds = feeds,
            unreadCounts = unreadCounts,
            isLoading = false,
            error = null
        )
        if (_uiState.value.searchQuery.isNotEmpty()) {
            filterFeeds()
        }
    }
}
