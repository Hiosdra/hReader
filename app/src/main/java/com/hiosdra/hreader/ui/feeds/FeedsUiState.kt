package com.hiosdra.hreader.ui.feeds

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.BuildConfig
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.MinifluxApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedsUiState(
    val feeds: List<Feed> = emptyList(),
    val unreadCounts: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class FeedsViewModel(private val apiService: MinifluxApiService) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedsUiState())
    val uiState: StateFlow<FeedsUiState> = _uiState.asStateFlow()

    init {
        loadFeeds()
    }

    private fun loadFeeds() {
        if (_uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val fetchedFeeds = apiService.getFeeds()
                val counters = apiService.getFeedCounters()
                val unreadCounts = counters.unreads.mapKeys { it.key.toLong() }
                _uiState.value = _uiState.value.copy(
                    feeds = fetchedFeeds,
                    unreadCounts = unreadCounts,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Error loading feeds: ${e.message}", isLoading = false)
                Log.e("FeedsViewModel", "Error loading feeds", e)
            }
        }
    }
}
