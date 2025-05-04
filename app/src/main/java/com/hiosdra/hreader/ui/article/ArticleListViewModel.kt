package com.hiosdra.hreader.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.local.ArticleRepository
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
                    _uiState.value = _uiState.value.copy(entries = filtered, isLoading = false, error = null)
                }
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
