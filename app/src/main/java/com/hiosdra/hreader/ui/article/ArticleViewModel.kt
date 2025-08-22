package com.hiosdra.hreader.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.ai.ArticleAiService
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArticleUiState(
    val entries: List<Entry> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val originalContent: Map<Long, String> = emptyMap(),
    val aiOverviews: Map<Long, String> = emptyMap(),
    val isGeneratingOverview: Boolean = false,
    val overviewError: String? = null
)

class ArticleViewModel(
    private val articleRepository: ArticleRepository,
    private val articleContentRepository: ArticleContentRepository,
    private val articleAiService: ArticleAiService,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArticleUiState())
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    fun refreshArticles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                articleRepository.refreshArticles()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error refreshing articles: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index)
        val entry = _uiState.value.entries.getOrNull(index)
        if (entry != null && !_uiState.value.originalContent.containsKey(entry.id)) {
            loadOriginalContent(entry.id, entry.url)
        }
    }

    private fun loadOriginalContent(entryId: Long, url: String) {
        viewModelScope.launch {
            try {
                val content = articleContentRepository.getArticleContent(entryId, url)
                val updatedContent = _uiState.value.originalContent.toMutableMap().apply {
                    put(entryId, content)
                }
                _uiState.value = _uiState.value.copy(originalContent = updatedContent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateReadStatus(index: Int, isRead: Boolean) {
        val entries = _uiState.value.entries.toMutableList()
        val entry = entries.getOrNull(index) ?: return
        val newStatus = if (isRead) "read" else "unread"
        entries[index] = entry.copy(status = newStatus)
        _uiState.value = _uiState.value.copy(entries = entries)
        viewModelScope.launch {
            articleRepository.updateReadStatus(entry.id.toString(), newStatus)
        }
    }

    fun loadArticlesByIds(ids: List<Long>) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                articleRepository.getArticlesByIds(ids).collect { articles ->
                    _uiState.value = _uiState.value.copy(entries = articles, isLoading = false, error = null)
                    val currentArticle = articles.getOrNull(_uiState.value.currentIndex)
                    if (currentArticle != null) {
                        loadOriginalContent(currentArticle.id, currentArticle.url)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun getContentForEntry(entryId: Long): String? {
        val originalContent = _uiState.value.originalContent[entryId]
        if (originalContent != null) {
            return originalContent
        }
        return _uiState.value.entries.find { it.id == entryId }?.content
    }
    
    fun generateAiOverview(entryId: Long) {
        val entry = _uiState.value.entries.find { it.id == entryId } ?: return
        
        // Check if overview already exists
        if (_uiState.value.aiOverviews.containsKey(entryId)) {
            return
        }
        
        _uiState.value = _uiState.value.copy(
            isGeneratingOverview = true,
            overviewError = null
        )
        
        viewModelScope.launch {
            try {
                val content = getContentForEntry(entryId) ?: entry.content ?: ""
                val model = preferencesManager.getAiModel()
                
                val result = articleAiService.generateArticleOverview(
                    title = entry.title,
                    content = content,
                    model = model
                )
                
                result.fold(
                    onSuccess = { overview ->
                        val updatedOverviews = _uiState.value.aiOverviews.toMutableMap()
                        updatedOverviews[entryId] = overview
                        _uiState.value = _uiState.value.copy(
                            aiOverviews = updatedOverviews,
                            isGeneratingOverview = false,
                            overviewError = null
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isGeneratingOverview = false,
                            overviewError = error.message ?: "Failed to generate overview"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingOverview = false,
                    overviewError = e.message ?: "Failed to generate overview"
                )
            }
        }
    }
    
    fun clearOverviewError() {
        _uiState.value = _uiState.value.copy(overviewError = null)
    }
}
