package com.hiosdra.hreader.presentation.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.content.articlePreviewHtml
import com.hiosdra.hreader.core.application.usecase.article.ArticleReaderUseCase
import com.hiosdra.hreader.core.domain.model.ArticleContentProvenance
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.OfflinePage
import com.hiosdra.hreader.presentation.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

private const val PAGER_WINDOW_RADIUS = 50

internal fun mergeReaderEntries(
    ids: List<Long>,
    latestEntries: List<Entry>,
    previousEntries: List<Entry>
): List<Entry> {
    val latestById = latestEntries.associateBy { it.id }
    val previousById = previousEntries.associateBy { it.id }
    return ids.mapNotNull { id -> latestById[id] ?: previousById[id] }
}

internal fun readerFallbackContent(entry: Entry): String? =
    entry.content?.takeIf { it.isNotBlank() } ?: articlePreviewHtml(entry.preview)

class ArticleViewModel(
    private val reader: ArticleReaderUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ArticleUiState(
            content = ArticleContentState(isOnline = reader.isOnline.value),
            ai = initialArticleAiState(reader)
        )
    )
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()
    private val aiCoordinator = ArticleAiCoordinator(reader, _uiState, viewModelScope)
    private val contentCoordinator = ArticleContentCoordinator(
        reader = reader,
        uiState = _uiState,
        scope = viewModelScope,
        loadCachedOverview = aiCoordinator::loadCachedOverview,
        loadCachedCredibility = aiCoordinator::loadCachedCredibility
    )

    private var listResolved = false
    private var listResolutionJob: Job? = null
    private val readingPositionWriteJobs = mutableMapOf<Long, Job>()

    init {
        viewModelScope.launch {
            reader.isOnline.collect { online ->
                val wasOnline = _uiState.value.content.isOnline
                _uiState.update { it.copy(content = it.content.copy(isOnline = online)) }
                if (online && !wasOnline) contentCoordinator.retryPartialContent()
            }
        }
        aiCoordinator.start()
    }

    fun setCurrentIndex(index: Int) {
        _uiState.update { state ->
            val arrivedAt = state.navigation.entries.getOrNull(index)?.id
            val contentError = when {
                arrivedAt in state.content.partialContentIds -> PARTIAL_CONTENT_MESSAGE
                state.content.contentLoadStates[arrivedAt] == ArticleContentLoadState.UNAVAILABLE -> {
                    CONTENT_UNAVAILABLE_MESSAGE
                }
                else -> null
            }
            state.copy(
                navigation = state.navigation.selectIndex(index),
                content = state.content.copy(contentError = contentError)
            ).trimReaderState(index)
        }
        contentCoordinator.loadAround(index)
    }

    fun updateReadStatus(index: Int, isRead: Boolean) {
        val entry = _uiState.value.navigation.entries.getOrNull(index) ?: return
        val newStatus = if (isRead) ArticleStatus.READ else ArticleStatus.UNREAD
        _uiState.update { state ->
            state.copy(
                navigation = state.navigation.copy(
                    entries = state.navigation.entries.map {
                        if (it.id == entry.id) it.copy(status = newStatus) else it
                    }
                )
            )
        }
        viewModelScope.launch {
            reader.updateReadStatus(entry.id, newStatus)
        }
    }

    fun openList(
        feedId: Long?,
        startArticleId: Long,
        includeRead: Boolean,
        sessionStartMillis: Long
    ) {
        if (listResolved || listResolutionJob?.isActive == true) return
        _uiState.update {
            it.copy(
                navigation = it.navigation.copy(isLoading = true),
                ai = it.ai.copy(credibilityEnabled = reader.credibilityEnabled())
            )
        }
        listResolutionJob = viewModelScope.launch {
            try {
                val window = reader.getArticleListWindow(
                    ArticleListQuery(
                        feedId = feedId,
                        includeRead = includeRead,
                        sessionStart = Instant.ofEpochMilli(sessionStartMillis)
                    ),
                    articleId = startArticleId,
                    radius = PAGER_WINDOW_RADIUS
                )
                val ids = window.ids.ifEmpty { listOf(startArticleId) }
                val startIndex = window.currentIndex.coerceIn(0, ids.lastIndex.coerceAtLeast(0))
                _uiState.update {
                    it.copy(
                        navigation = it.navigation.resolveList(
                            currentIndex = startIndex,
                            windowStartIndex = window.windowStartIndex,
                            totalCount = window.totalCount.coerceAtLeast(ids.size)
                        )
                    )
                }
                listResolved = true
                observeArticles(ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listResolved = false
                _uiState.update {
                    it.copy(
                        navigation = it.navigation.copy(
                            isLoading = false,
                            error = UiText.Resource(R.string.article_load_error)
                        )
                    )
                }
            }
        }
    }

    private suspend fun observeArticles(ids: List<Long>) {
        reader.observeArticles(ids).collect { articles ->
            _uiState.update { state ->
                state.copy(
                    navigation = state.navigation.copy(
                        entries = mergeReaderEntries(ids, articles, state.navigation.entries),
                        isLoading = false,
                        error = null
                    )
                )
            }
            contentCoordinator.loadAround(_uiState.value.navigation.currentIndex)
        }
    }

    fun getContentForEntry(entryId: Long): String? = contentCoordinator.getContentForEntry(entryId)

    fun getContentStateForEntry(entryId: Long): ArticleContentLoadState =
        contentCoordinator.getContentStateForEntry(entryId)

    fun getContentProvenanceForEntry(entryId: Long): ArticleContentProvenance =
        contentCoordinator.getContentProvenanceForEntry(entryId)

    fun getLeadImageForEntry(entryId: Long): String? = contentCoordinator.getLeadImageForEntry(entryId)

    fun getReadingProgressForEntry(entryId: Long): Float? =
        _uiState.value.content.readingProgress.positions[entryId]

    fun saveReadingProgress(entryId: Long, progress: Float) {
        val normalized = progress.coerceIn(0f, 1f)
        if (normalized == 0f) {
            clearReadingProgress(entryId)
            return
        }
        _uiState.update { state ->
            if (entryId !in state.readerWindowIds()) {
                state
            } else {
                state.copy(
                    content = state.content.copy(
                        readingProgress = state.content.readingProgress.withPosition(entryId, normalized)
                    )
                )
            }
        }
        enqueueReadingPositionWrite(entryId) {
            reader.saveReadingProgress(entryId, normalized)
        }
    }

    fun clearReadingProgress(entryId: Long) {
        _uiState.update { state ->
            state.copy(
                content = state.content.copy(
                    readingProgress = state.content.readingProgress.withoutPosition(entryId)
                )
            )
        }
        enqueueReadingPositionWrite(entryId) {
            reader.clearReadingProgress(entryId)
        }
    }

    private fun enqueueReadingPositionWrite(entryId: Long, operation: suspend () -> Unit) {
        readingPositionWriteJobs.remove(entryId)?.cancel()
        readingPositionWriteJobs[entryId] = viewModelScope.launch { operation() }
    }

    fun getOfflinePageForEntry(entryId: Long): OfflinePage? =
        contentCoordinator.getOfflinePageForEntry(entryId)

    fun retryContent(entryId: Long) = contentCoordinator.retryContent(entryId)

    fun clearContentError() {
        _uiState.update { it.copy(content = it.content.copy(contentError = null)) }
    }

    fun generateAiOverview(entryId: Long) = aiCoordinator.generateOverview(entryId)

    fun analyzeCredibility(entryId: Long, forceRefresh: Boolean = false) =
        aiCoordinator.analyzeCredibility(entryId, forceRefresh)

    fun clearOverviewError() = aiCoordinator.clearOverviewError()

    fun clearScoreError() = aiCoordinator.clearScoreError()
}
