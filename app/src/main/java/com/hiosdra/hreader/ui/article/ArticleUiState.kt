package com.hiosdra.hreader.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.remote.MinifluxApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArticleUiState(
    val entries: List<Entry> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ArticleViewModel(private val apiService: MinifluxApiService) : ViewModel() {
    private val _uiState = MutableStateFlow(ArticleUiState())
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    fun loadArticles(articleIds: List<Long>, initialIndex: Int = 0) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val idsString = articleIds.joinToString(",")
                val entries = apiService.getEntriesByIds(idsString).entries
                // Sort entries to match the order of articleIds
                val sortedEntries = articleIds.mapNotNull { id -> entries.find { it.id == id } }
                _uiState.value = _uiState.value.copy(entries = sortedEntries, currentIndex = initialIndex, isLoading = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error loading articles: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index)
    }
}
