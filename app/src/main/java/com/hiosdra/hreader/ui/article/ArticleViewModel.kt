package com.hiosdra.hreader.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.local.ArticleRepository
import com.hiosdra.hreader.data.model.Entry
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

class ArticleViewModel(private val repository: ArticleRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ArticleUiState())
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    init {
        refreshArticles()
    }

    fun refreshArticles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.refreshArticles()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error refreshing articles: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index)
    }

    fun updateReadStatus(index: Int, isRead: Boolean) {
        val entries = _uiState.value.entries.toMutableList()
        val entry = entries.getOrNull(index) ?: return
        val newStatus = if (isRead) "read" else "unread"
        entries[index] = entry.copy(status = newStatus)
        _uiState.value = _uiState.value.copy(entries = entries)
        viewModelScope.launch {
            repository.updateReadStatus(entry.id.toString(), newStatus)
        }
    }

    fun loadArticlesByIds(ids: List<Long>) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                repository.getArticlesByIds(ids).collect { articles ->
                    _uiState.value = _uiState.value.copy(entries = articles, isLoading = false, error = null)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }
}
