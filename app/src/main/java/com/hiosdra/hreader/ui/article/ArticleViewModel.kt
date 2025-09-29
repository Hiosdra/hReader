package com.hiosdra.hreader.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.ai.AiModel
import com.hiosdra.hreader.data.ai.ArticleAiService
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.model.ArticleStatus
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
    val overviewError: String? = null,
    val credibilityScores: Map<Long, Float> = emptyMap(),
    val isGeneratingScore: Boolean = false,
    val scoreError: String? = null
)

class ArticleViewModel(
    private val articleRepository: ArticleRepository,
    private val articleContentRepository: ArticleContentRepository,
    private val articleAiService: ArticleAiService,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArticleUiState())
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

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
        val newStatus = if (isRead) ArticleStatus.READ else ArticleStatus.UNREAD
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

    fun getContentForEntry(entryId: Long): String? =
        _uiState.value.originalContent[entryId]
            ?: _uiState.value.entries.find { it.id == entryId }?.content

    fun generateAiOverview(entryId: Long) {
        val entry = _uiState.value.entries.find { it.id == entryId } ?: return
        if (_uiState.value.aiOverviews.containsKey(entryId)) return

        generateAiContent(
            entryId = entryId,
            entry = entry,
            setGenerating = { generating -> _uiState.value = _uiState.value.copy(isGeneratingOverview = generating, overviewError = null) },
            setError = { error -> _uiState.value = _uiState.value.copy(isGeneratingOverview = false, overviewError = error) },
            onSuccess = { content -> _uiState.value = _uiState.value.copy(aiOverviews = _uiState.value.aiOverviews + (entryId to content), isGeneratingOverview = false, overviewError = null) },
            generateFunction = { title, content, model -> articleAiService.generateArticleOverview(title, content, model) },
            errorMessage = "Failed to generate overview"
        )
    }

    fun generateCredibilityScore(entryId: Long) {
        val entry = _uiState.value.entries.find { it.id == entryId } ?: return
        if (_uiState.value.credibilityScores.containsKey(entryId)) return
        if (!preferencesManager.getCredibilityScoreEnabled()) return

        generateAiContent(
            entryId = entryId,
            entry = entry,
            setGenerating = { generating -> _uiState.value = _uiState.value.copy(isGeneratingScore = generating, scoreError = null) },
            setError = { error -> _uiState.value = _uiState.value.copy(isGeneratingScore = false, scoreError = error) },
            onSuccess = { score -> _uiState.value = _uiState.value.copy(credibilityScores = _uiState.value.credibilityScores + (entryId to score), isGeneratingScore = false, scoreError = null) },
            generateFunction = { title, content, model -> articleAiService.generateCredibilityScore(title, content, model) },
            errorMessage = "Failed to generate credibility score"
        )
    }

    fun clearOverviewError() {
        _uiState.value = _uiState.value.copy(overviewError = null)
    }

    fun clearScoreError() {
        _uiState.value = _uiState.value.copy(scoreError = null)
    }

    private fun <T> generateAiContent(
        entryId: Long,
        entry: Entry,
        setGenerating: (Boolean) -> Unit,
        setError: (String) -> Unit,
        onSuccess: (T) -> Unit,
        generateFunction: suspend (String, String, AiModel) -> Result<T>,
        errorMessage: String
    ) {
        setGenerating(true)

        viewModelScope.launch {
            try {
                val content = getContentForEntry(entryId) ?: entry.content ?: ""
                val model = preferencesManager.getAiModel()

                val result = generateFunction(entry.title, content, model)

                result.fold(
                    onSuccess = onSuccess,
                    onFailure = { error -> setError(error.message ?: errorMessage) }
                )
            } catch (e: Exception) {
                setError(e.message ?: errorMessage)
            }
        }
    }
}
