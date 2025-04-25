package com.hiosdra.hreader.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.remote.MinifluxApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ArticleListViewModel(
    private val apiService: MinifluxApiService
) : ViewModel(), KoinComponent {
    private val _uiState = MutableStateFlow(ArticleListUiState())
    val uiState: StateFlow<ArticleListUiState> = _uiState.asStateFlow()

    fun loadArticlesForFeed(feedId: Long) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val response = apiService.getFeedEntries(
                    feedId = feedId,
                    status = "unread",
                    limit = 1000,
                    order = "published_at",
                    direction = "desc"
                )
                _uiState.value = _uiState.value.copy(entries = response.entries, isLoading = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }
}

data class ArticleListUiState(
    val entries: List<Entry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
