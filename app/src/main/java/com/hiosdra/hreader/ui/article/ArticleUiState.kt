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
    val entry: Entry? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ArticleViewModel(private val apiService: MinifluxApiService) : ViewModel() {
    private val _uiState = MutableStateFlow(ArticleUiState())
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    fun loadArticle(articleId: Long) {
        if (_uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val entry = apiService.getEntryById(articleId)
                _uiState.value = _uiState.value.copy(entry = entry, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error loading article: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
}
