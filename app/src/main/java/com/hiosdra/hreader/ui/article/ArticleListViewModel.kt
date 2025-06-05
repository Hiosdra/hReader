package com.hiosdra.hreader.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.model.Entry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class ArticleListViewModel(
    private val articleRepository: ArticleRepository
) : ViewModel(), KoinComponent {
    private val _uiState = MutableStateFlow(ArticleListUiState())
    val uiState: StateFlow<ArticleListUiState> = _uiState.asStateFlow()

    fun loadArticlesForFeed(feedId: Long) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                articleRepository.getAllArticlesForFeed(feedId).collect { filtered ->
                    _uiState.value =
                        _uiState.value.copy(entries = filtered, isLoading = false, error = null)
                }
            } catch (e: Exception) {
                _uiState.value =
                    _uiState.value.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun updateEntryReadStatus(entryId: Long, checked: Boolean) {
        val entries = _uiState.value.entries.map {
            if (it.id == entryId) it.copy(status = if (checked) "read" else "unread") else it
        }
        _uiState.value = _uiState.value.copy(entries = entries)
        viewModelScope.launch {
            try {
                articleRepository.updateReadStatus(
                    entryId.toString(),
                    if (checked) "read" else "unread"
                )
            } catch (e: Exception) {
                // Optionally handle error (rollback local change, show error, etc)
            }
        }
    }

    fun markAllAsRead() {
        val entries = _uiState.value.entries.map { it.copy(status = "read") }
        _uiState.value = _uiState.value.copy(entries = entries)
        viewModelScope.launch {
            try {
                val articleIds = _uiState.value.entries.map { it.id.toString() }
                articleRepository.updateReadStatus(articleIds, "read")
            } catch (e: Exception) {
                // Optionally handle error (rollback local change, show error, etc)
            }
        }
    }

    fun refreshArticles(feedId: Long) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                articleRepository.refreshArticles()
                loadArticlesForFeed(feedId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Network refresh failed: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}

data class ArticleListUiState(
    val entries: List<Entry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
