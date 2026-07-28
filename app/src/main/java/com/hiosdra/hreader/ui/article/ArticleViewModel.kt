package com.hiosdra.hreader.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.ai.ArticleAiService
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.local.repository.CredibilityRepository
import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.CredibilityReport
import com.hiosdra.hreader.data.model.CredibilitySource
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MISSING_CONTENT_MESSAGE =
    "The article text is still downloading. Try again in a moment."

data class ArticleUiState(
    val entries: List<Entry> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val originalContent: Map<Long, String> = emptyMap(),
    val aiOverviews: Map<Long, String> = emptyMap(),
    val generatingOverviewIds: Set<Long> = emptySet(),
    val overviewError: String? = null,
    val credibilityEnabled: Boolean = false,
    val credibilityReports: Map<Long, CredibilityReport> = emptyMap(),
    val analyzingCredibilityIds: Set<Long> = emptySet(),
    val scoreError: String? = null
)

class ArticleViewModel(
    private val articleRepository: ArticleRepository,
    private val articleContentRepository: ArticleContentRepository,
    private val articleAiService: ArticleAiService,
    private val credibilityRepository: CredibilityRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ArticleUiState(credibilityEnabled = preferencesManager.getCredibilityScoreEnabled())
    )
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    fun setCurrentIndex(index: Int) {
        _uiState.update { it.copy(currentIndex = index) }
        val entry = _uiState.value.entries.getOrNull(index) ?: return
        if (!_uiState.value.originalContent.containsKey(entry.id)) {
            loadOriginalContent(entry.id, entry.url)
        }
    }

    private fun loadOriginalContent(entryId: Long, url: String) {
        viewModelScope.launch {
            try {
                val content = articleContentRepository.getArticleContent(entryId, url)
                _uiState.update { it.copy(originalContent = it.originalContent + (entryId to content)) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateReadStatus(index: Int, isRead: Boolean) {
        val entry = _uiState.value.entries.getOrNull(index) ?: return
        val newStatus = if (isRead) ArticleStatus.READ else ArticleStatus.UNREAD
        _uiState.update { state ->
            state.copy(
                entries = state.entries.map { if (it.id == entry.id) it.copy(status = newStatus) else it }
            )
        }
        viewModelScope.launch {
            articleRepository.updateReadStatus(entry.id.toString(), newStatus)
        }
    }

    fun loadArticlesByIds(ids: List<Long>) {
        _uiState.update {
            it.copy(
                isLoading = true,
                credibilityEnabled = preferencesManager.getCredibilityScoreEnabled()
            )
        }
        viewModelScope.launch {
            try {
                articleRepository.getArticlesByIds(ids).collect { articles ->
                    _uiState.update { it.copy(entries = articles, isLoading = false, error = null) }
                    val currentArticle = articles.getOrNull(_uiState.value.currentIndex)
                    if (currentArticle != null) {
                        loadOriginalContent(currentArticle.id, currentArticle.url)
                    }
                    loadCachedCredibility(articles.map { it.id })
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun getContentForEntry(entryId: Long): String? =
        _uiState.value.originalContent[entryId]
            ?: _uiState.value.entries.find { it.id == entryId }?.content

    fun generateAiOverview(entryId: Long) {
        val entry = _uiState.value.entries.find { it.id == entryId } ?: return
        if (_uiState.value.aiOverviews.containsKey(entryId)) return
        if (_uiState.value.generatingOverviewIds.contains(entryId)) return

        _uiState.update {
            it.copy(generatingOverviewIds = it.generatingOverviewIds + entryId, overviewError = null)
        }

        viewModelScope.launch {
            val content = getContentForEntry(entryId).orEmpty()
            val result = if (content.isBlank()) {
                Result.failure(Exception(MISSING_CONTENT_MESSAGE))
            } else {
                articleAiService.generateArticleOverview(
                    title = entry.title,
                    content = content,
                    modelId = preferencesManager.getAiModelId()
                )
            }

            _uiState.update { state ->
                state.copy(
                    generatingOverviewIds = state.generatingOverviewIds - entryId,
                    aiOverviews = result.fold(
                        onSuccess = { state.aiOverviews + (entryId to it) },
                        onFailure = { state.aiOverviews }
                    ),
                    overviewError = result.exceptionOrNull()
                        ?.let { it.message ?: "Failed to generate overview" }
                        ?: state.overviewError
                )
            }
        }
    }

    fun analyzeCredibility(entryId: Long, forceRefresh: Boolean = false) {
        val entry = _uiState.value.entries.find { it.id == entryId } ?: return
        if (!_uiState.value.credibilityEnabled) return
        if (_uiState.value.analyzingCredibilityIds.contains(entryId)) return
        if (!forceRefresh && _uiState.value.credibilityReports.containsKey(entryId)) return

        val content = getContentForEntry(entryId).orEmpty()
        if (content.isBlank()) {
            _uiState.update { it.copy(scoreError = MISSING_CONTENT_MESSAGE) }
            return
        }

        _uiState.update {
            it.copy(analyzingCredibilityIds = it.analyzingCredibilityIds + entryId, scoreError = null)
        }

        viewModelScope.launch {
            val result = credibilityRepository.analyze(
                entryId = entryId,
                source = CredibilitySource(
                    title = entry.title,
                    content = content,
                    author = entry.author,
                    feedTitle = entry.feed.title,
                    url = entry.url,
                    publishedAt = entry.publishedAt
                ),
                modelId = preferencesManager.getAiModelId(),
                forceRefresh = forceRefresh
            )

            _uiState.update { state ->
                state.copy(
                    analyzingCredibilityIds = state.analyzingCredibilityIds - entryId,
                    credibilityReports = result.fold(
                        onSuccess = { state.credibilityReports + (entryId to it) },
                        onFailure = { state.credibilityReports }
                    ),
                    scoreError = result.exceptionOrNull()
                        ?.let { it.message ?: "Failed to analyze credibility" }
                        ?: state.scoreError
                )
            }
        }
    }

    private fun loadCachedCredibility(entryIds: List<Long>) {
        if (!_uiState.value.credibilityEnabled) return
        val missing = entryIds.filterNot { _uiState.value.credibilityReports.containsKey(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val cached = runCatching { credibilityRepository.getCached(missing) }.getOrElse { emptyMap() }
            if (cached.isEmpty()) return@launch
            _uiState.update { it.copy(credibilityReports = cached + it.credibilityReports) }
        }
    }

    fun clearOverviewError() {
        _uiState.update { it.copy(overviewError = null) }
    }

    fun clearScoreError() {
        _uiState.update { it.copy(scoreError = null) }
    }
}
