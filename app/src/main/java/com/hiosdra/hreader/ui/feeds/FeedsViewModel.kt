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

    private fun loadFeeds() {
        if (_uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val fetchedFeeds = feedRepository.getFeeds()
                val unreadCounts = feedRepository.getUnreadCounts()
                _uiState.value = _uiState.value.copy(
                    feeds = fetchedFeeds,
                    filteredFeeds = fetchedFeeds,
                    unreadCounts = unreadCounts,
                    isLoading = false
                )
                if (_uiState.value.searchQuery.isNotEmpty()) {
                    filterFeeds()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Error loading feeds: ${e.message}", isLoading = false)
                Log.e("FeedsViewModel", "Error loading feeds", e)
            }
        }
    }
}
