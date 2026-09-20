package com.hiosdra.hreader.presentation.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.ArticleAiPhase
import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import com.hiosdra.hreader.core.application.ai.AiProviderException
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.EmptyAiContentException
import com.hiosdra.hreader.core.application.ai.GemmaModelNotInstalledException
import com.hiosdra.hreader.core.application.ai.MissingAiApiKeyException
import com.hiosdra.hreader.core.application.content.articlePreviewHtml
import com.hiosdra.hreader.core.application.content.hasReadableArticleText
import com.hiosdra.hreader.core.application.usecase.article.ArticleReaderUseCase
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.core.domain.model.ArticleContentDelivery
import com.hiosdra.hreader.core.domain.model.ArticleContentKind
import com.hiosdra.hreader.core.domain.model.ArticleContentProvenance
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.CredibilitySource
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.OfflinePage
import com.hiosdra.hreader.core.domain.model.ArticleText
import com.hiosdra.hreader.core.domain.model.toProvenance
import com.hiosdra.hreader.presentation.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

private val MISSING_CONTENT_MESSAGE = UiText.Resource(R.string.article_missing_content)
private val CONTENT_UNAVAILABLE_MESSAGE = UiText.Resource(R.string.article_content_unavailable)

/**
 * How many articles either side of the opened one the reader can swipe through.
 *
 * The pager observes its articles with one `id IN (…)` statement, and SQLite on Android binds at
 * most 999 variables — a cached backlog of several thousand would take the query down. Nobody
 * swipes dozens of articles in one sitting, and going back to the list starts a fresh window.
 */
private const val PAGER_WINDOW_RADIUS = 50
private val PARTIAL_CONTENT_MESSAGE = UiText.Resource(R.string.article_partial_content)

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

private fun Entry.toCredibilitySource(content: String): CredibilitySource = CredibilitySource(
    title = title,
    content = content,
    author = author,
    feedTitle = feed.title,
    url = url,
    publishedAt = publishedAt
)

class ArticleViewModel(
    private val reader: ArticleReaderUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ArticleUiState(
            content = ArticleContentState(isOnline = reader.isOnline.value),
            ai = ArticleAiState(
                credibilityEnabled = reader.credibilityEnabled(),
                aiProvider = AiModel.providerFor(reader.getAiModelId())
            )
        )
    )
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    /** The list is resolved once; a configuration change must not rebuild it under the pager. */
    private var listResolved = false
    private var listResolutionJob: Job? = null

    /**
     * Articles whose text has already been asked for. The pager observes its articles, so every
     * read tick re-emits them; without this the article on screen was fetched again on each one.
     */
    private val requestedContentIds = mutableSetOf<Long>()

    private val requestedOfflinePageUrls = mutableMapOf<Long, String>()

    /** Articles whose stored credibility report has already been looked up, for the same reason. */
    private val checkedCredibilityIds = mutableSetOf<Long>()
    private var activeAiModelId = reader.getAiModelId()
    private var aiModelGeneration = 0

    private val requestedReadingPositionIds = mutableSetOf<Long>()
    private val readingPositionWriteJobs = mutableMapOf<Long, Job>()
    private val imagePathJobs = mutableMapOf<Long, Job>()

    init {
        viewModelScope.launch {
            reader.isOnline.collect { online ->
                val wasOnline = _uiState.value.content.isOnline
                _uiState.update { it.copy(content = it.content.copy(isOnline = online)) }
                if (online && !wasOnline) retryPartialContent()
            }
        }
        viewModelScope.launch {
            reader.observeAiModelId().collect { modelId ->
                if (modelId == activeAiModelId) return@collect
                activeAiModelId = modelId
                aiModelGeneration++
                checkedCredibilityIds.clear()
                val generation = aiModelGeneration
                val nearbyIds = _uiState.value.readerWindowIds()
                _uiState.update {
                    it.copy(
                        ai = it.ai.copy(
                            aiOverviews = emptyMap(),
                            aiProvider = AiModel.providerFor(modelId),
                            credibilityReports = emptyMap(),
                            generatingOverviewIds = emptySet(),
                            aiOverviewProgress = emptyMap(),
                            analyzingCredibilityIds = emptySet(),
                            overviewError = null,
                            scoreError = null
                        )
                    )
                }
                reloadAiCaches(nearbyIds, modelId, generation)
            }
        }
    }

    private fun retryPartialContent() {
        val state = _uiState.value
        val unavailableIds = state.content.contentLoadStates
            .filterValues { it == ArticleContentLoadState.UNAVAILABLE }
            .keys
        (state.content.partialContentIds + unavailableIds).forEach { entryId ->
            retryContent(entryId)
        }
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
        loadAround(index)
    }

    private fun loadAround(index: Int) {
        val entries = _uiState.value.navigation.entries
        val nearby = listOfNotNull(
            entries.getOrNull(index),
            entries.getOrNull(index - 1),
            entries.getOrNull(index + 1)
        )
        val nearbyIds = nearby.map { it.id }.toSet()
        requestedOfflinePageUrls.keys.retainAll(nearbyIds)
        requestedContentIds.retainAll(nearbyIds)
        checkedCredibilityIds.retainAll(nearbyIds)
        requestedReadingPositionIds.retainAll(nearbyIds)
        imagePathJobs.keys
            .filterNot { it in nearbyIds }
            .toList()
            .forEach { imagePathJobs.remove(it)?.cancel() }
        _uiState.update { it.trimReaderState(index) }
        loadReadingPositions(nearbyIds)
        observeLocalImagePaths(nearbyIds)
        nearby.forEach { entry ->
            loadOfflinePage(entry.id, entry.url)
            loadArticleText(entry.id, entry.url)
        }
        loadCachedCredibility(nearbyIds.toList())
    }

    private fun observeLocalImagePaths(articleIds: Set<Long>) {
        articleIds.forEach { entryId ->
            if (imagePathJobs[entryId]?.isActive == true) return@forEach
            imagePathJobs[entryId] = viewModelScope.launch {
                reader.observeLocalImagePaths(entryId).collect { paths ->
                    _uiState.update { state ->
                        if (entryId !in state.readerWindowIds()) {
                            state
                        } else {
                            state.copy(
                                content = state.content.copy(
                                    localImagePaths = state.content.localImagePaths + (entryId to paths)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun loadReadingPositions(articleIds: Set<Long>) {
        val state = _uiState.value
        val missing = articleIds.filter { entryId ->
            entryId !in state.content.readingProgress.loadedIds && requestedReadingPositionIds.add(entryId)
        }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val positions = try {
                reader.getReadingProgresses(missing)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                requestedReadingPositionIds.removeAll(missing.toSet())
                emptyMap()
            }
            _uiState.update { state ->
                val retainedIds = state.readerWindowIds()
                state.copy(
                    content = state.content.copy(
                        readingProgress = state.content.readingProgress.copy(
                            positions = state.content.readingProgress.positions.filterKeys { it in retainedIds } +
                                positions.filterKeys { it in retainedIds },
                            loadedIds = state.content.readingProgress.loadedIds +
                                missing.filter { it in retainedIds }
                        )
                    )
                )
            }
        }
    }

    private fun loadOfflinePage(entryId: Long, url: String) {
        val loadedPage = _uiState.value.content.offlinePages[entryId]
        if (loadedPage?.originalUrl == url || requestedOfflinePageUrls[entryId] == url) return
        requestedOfflinePageUrls[entryId] = url
        if (loadedPage != null) {
            _uiState.update {
                it.copy(content = it.content.copy(offlinePages = it.content.offlinePages - entryId))
            }
        }
        viewModelScope.launch {
            val offlinePage = try {
                reader.getOfflinePage(entryId, url)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _uiState.update { state ->
                if (requestedOfflinePageUrls[entryId] != url) return@update state
                requestedOfflinePageUrls.remove(entryId)
                state.copy(
                    content = state.content.copy(
                        offlinePages = if (offlinePage == null) {
                            state.content.offlinePages - entryId
                        } else {
                            state.content.offlinePages + (entryId to offlinePage)
                        }
                    )
                )
            }
        }
    }

    /**
     * A failure here is the normal offline case, not an oddity: the fetch needs the backend, and
     * what the feed itself carried is all that is left. Silence used to leave the reader staring
     * at an empty screen wondering whether it was still loading.
     *
     * Asked for once while the connection state is unchanged. A fallback is retried after the
     * connection returns so it can be upgraded to the full text.
     */
    private fun loadArticleText(entryId: Long, url: String, force: Boolean = false) {
        if (!force && _uiState.value.content.content.containsKey(entryId)) return
        if (!requestedContentIds.add(entryId)) return
        _uiState.update { state ->
            if (entryId !in state.readerWindowIds()) {
                state
            } else {
                state.copy(
                    content = state.content.copy(
                        contentLoadStates = state.content.contentLoadStates +
                            (entryId to ArticleContentLoadState.LOADING)
                    )
                )
            }
        }
        viewModelScope.launch {
            try {
                val text = reader.getArticleContent(entryId, url, _uiState.value.content.isOnline)
                store(entryId, text)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                val state = _uiState.value
                val entry = state.navigation.entries.find { it.id == entryId }
                when {
                    hasReadableArticleText(state.content.content[entryId]) -> restoreStoredContent(entryId)
                    entry != null && readerFallbackContent(entry) != null -> markPartial(entryId)
                    else -> markUnavailable(entryId)
                }
            }
        }
    }

    private fun restoreStoredContent(entryId: Long) {
        _uiState.update { state ->
            if (entryId !in state.readerWindowIds()) {
                state
            } else {
                val previousProvenance = state.content.contentProvenance[entryId]
                val isFallback = previousProvenance?.kind == ArticleContentKind.FEED_FALLBACK
                val restoredState = if (isFallback) {
                    ArticleContentLoadState.FALLBACK
                } else {
                    ArticleContentLoadState.FULL
                }
                state.copy(
                    content = state.content.copy(
                        contentLoadStates = state.content.contentLoadStates + (entryId to restoredState),
                        contentProvenance = if (previousProvenance == null) {
                            state.content.contentProvenance + (
                                entryId to ArticleContentProvenance(
                                    kind = if (isFallback) {
                                        ArticleContentKind.FEED_FALLBACK
                                    } else {
                                        ArticleContentKind.FULL_ARTICLE
                                    },
                                    sourceUrl = state.navigation.entries.firstOrNull { it.id == entryId }?.url,
                                    delivery = ArticleContentDelivery.LOCAL_STORAGE,
                                    isComplete = !isFallback
                                )
                            )
                        } else {
                            state.content.contentProvenance
                        },
                        partialContentIds = if (isFallback) {
                            state.content.partialContentIds + entryId
                        } else {
                            state.content.partialContentIds - entryId
                        },
                        contentError = if (state.navigation.entries.getOrNull(state.navigation.currentIndex)?.id == entryId) {
                            if (isFallback) PARTIAL_CONTENT_MESSAGE else null
                        } else {
                            state.content.contentError
                        }
                    )
                )
            }
        }
    }

    private fun markPartial(entryId: Long) {
        _uiState.update { state ->
            if (entryId !in state.readerWindowIds()) {
                state
            } else {
                val partialState = state.withPartialContent(entryId)
                partialState.copy(
                    content = partialState.content.copy(
                        contentProvenance = state.content.contentProvenance + (
                            entryId to ArticleContentProvenance(
                                kind = ArticleContentKind.FEED_FALLBACK,
                                sourceUrl = state.navigation.entries.firstOrNull { it.id == entryId }?.url,
                                delivery = ArticleContentDelivery.LOCAL_STORAGE,
                                isComplete = false
                            )
                        )
                    )
                )
            }
        }
    }

    private fun markUnavailable(entryId: Long) {
        _uiState.update { state ->
            if (entryId !in state.readerWindowIds()) {
                state
            } else {
                state.copy(
                    content = state.content.copy(
                        contentLoadStates = state.content.contentLoadStates + (
                            entryId to ArticleContentLoadState.UNAVAILABLE
                        ),
                        partialContentIds = state.content.partialContentIds - entryId,
                        contentProvenance = state.content.contentProvenance + (
                            entryId to ArticleContentProvenance(
                                kind = ArticleContentKind.UNAVAILABLE,
                                sourceUrl = state.navigation.entries.firstOrNull { it.id == entryId }?.url,
                                isComplete = false
                            )
                        ),
                        contentError = if (state.navigation.entries.getOrNull(state.navigation.currentIndex)?.id == entryId) {
                            CONTENT_UNAVAILABLE_MESSAGE
                        } else {
                            state.content.contentError
                        }
                    )
                )
            }
        }
    }

    private fun ArticleUiState.withPartialContent(entryId: Long) = copy(
        content = content.copy(
            contentLoadStates = content.contentLoadStates + (entryId to ArticleContentLoadState.FALLBACK),
            partialContentIds = content.partialContentIds + entryId,
            contentError = if (navigation.entries.getOrNull(navigation.currentIndex)?.id == entryId) {
                PARTIAL_CONTENT_MESSAGE
            } else {
                content.contentError
            }
        )
    )

    private suspend fun store(entryId: Long, text: ArticleText) {
        val modelId = activeAiModelId
        val generation = aiModelGeneration
        val provenance = text.toProvenance()
        val localPaths = reader.getLocalImagePaths(entryId)
        _uiState.update {
            val stored = it.trimReaderState()
            if (entryId !in stored.readerWindowIds()) return@update stored
            val withContent = stored.content.copy(
                content = stored.content.content + (entryId to text.html),
                contentLoadStates = stored.content.contentLoadStates + (
                    entryId to if (text.source == ArticleContentSource.FULL) {
                        ArticleContentLoadState.FULL
                    } else {
                        ArticleContentLoadState.FALLBACK
                    }
                ),
                contentProvenance = stored.content.contentProvenance + (entryId to provenance),
                leadImages = stored.content.leadImages + (entryId to text.leadImageUrl),
                localImagePaths = stored.content.localImagePaths + (entryId to localPaths)
            )
            val withContentState = stored.copy(content = withContent)
            if (text.source == ArticleContentSource.FULL) {
                withContentState.copy(
                    content = withContent.copy(
                        partialContentIds = withContent.partialContentIds - entryId,
                        contentError = if (
                            withContentState.navigation.entries
                                .getOrNull(withContentState.navigation.currentIndex)?.id == entryId &&
                            withContent.contentError in setOf(
                                PARTIAL_CONTENT_MESSAGE,
                                CONTENT_UNAVAILABLE_MESSAGE
                            )
                        ) {
                            null
                        } else {
                            stored.content.contentError
                        }
                    )
                )
            } else {
                withContentState.withPartialContent(entryId)
            }
        }
        val cachedOverview = reader.getCachedOverview(entryId, text.html, modelId)
        if (cachedOverview != null) {
            _uiState.update { state ->
                if (generation != aiModelGeneration || entryId !in state.readerWindowIds()) {
                    state
                } else {
                    state.copy(ai = state.ai.copy(aiOverviews = state.ai.aiOverviews + (entryId to cachedOverview)))
                }
            }
        }
        requestedContentIds.retainAll(_uiState.value.readerWindowIds())
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

    /**
     * Resolves the list the reader was looking at from the same query that built it, rather than
     * having every article id handed over through the navigation route.
     *
     * The set of ids is taken once and then held: the pager observes those articles for changes,
     * but the list itself must not shrink as they are marked read under the reader's finger.
     */
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
            // Room returns them ordered by date; the pager has to walk them in the order the list
            // handed over, which is the same order but resolved once rather than re-derived.
            _uiState.update { state ->
                state.copy(
                    navigation = state.navigation.copy(
                        entries = mergeReaderEntries(ids, articles, state.navigation.entries),
                        isLoading = false,
                        error = null
                    )
                )
            }
            loadAround(_uiState.value.navigation.currentIndex)
        }
    }

    fun getContentForEntry(entryId: Long): String? =
        _uiState.value.content.content[entryId]
            ?: _uiState.value.navigation.entries.find { it.id == entryId }?.let(::readerFallbackContent)

    fun getContentStateForEntry(entryId: Long): ArticleContentLoadState =
        _uiState.value.content.contentLoadState(entryId)

    fun getContentProvenanceForEntry(entryId: Long): ArticleContentProvenance {
        return _uiState.value.getContentProvenance(entryId)
    }

    fun getLeadImageForEntry(entryId: Long): String? = _uiState.value.content.leadImages[entryId]

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

    fun getOfflinePageForEntry(entryId: Long): OfflinePage? = _uiState.value.content.offlinePages[entryId]

    fun retryContent(entryId: Long) {
        val entry = _uiState.value.navigation.entries.find { it.id == entryId } ?: return
        requestedContentIds.remove(entryId)
        _uiState.update { state ->
            val currentEntryId = state.navigation.entries.getOrNull(state.navigation.currentIndex)?.id
            val hasUsableStoredContent = hasReadableArticleText(state.content.content[entryId])
            state.copy(
                content = state.content.copy(
                    content = if (hasUsableStoredContent) {
                        state.content.content
                    } else {
                        state.content.content - entryId
                    },
                    leadImages = if (hasUsableStoredContent) {
                    state.content.leadImages
                } else {
                    state.content.leadImages - entryId
                },
                    contentLoadStates = state.content.contentLoadStates +
                        (entryId to ArticleContentLoadState.LOADING),
                    contentProvenance = if (hasUsableStoredContent) {
                        state.content.contentProvenance
                    } else {
                        state.content.contentProvenance - entryId
                    },
                    partialContentIds = state.content.partialContentIds - entryId,
                    contentError = if (currentEntryId == entryId) null else state.content.contentError
                )
            )
        }
        loadArticleText(entry.id, entry.url, force = true)
    }

    fun clearContentError() {
        _uiState.update { it.copy(content = it.content.copy(contentError = null)) }
    }

    fun generateAiOverview(entryId: Long) {
        val entry = _uiState.value.navigation.entries.find { it.id == entryId } ?: return
        if (_uiState.value.ai.generatingOverviewIds.contains(entryId)) return
        val modelId = activeAiModelId
        val generation = aiModelGeneration

        _uiState.update {
            it.copy(
                ai = it.ai.copy(
                    generatingOverviewIds = it.ai.generatingOverviewIds + entryId,
                    aiOverviewProgress = it.ai.aiOverviewProgress +
                        (entryId to ArticleAiProgress(ArticleAiPhase.PREPARING)),
                    overviewError = null
                )
            )
        }

        viewModelScope.launch {
            val content = getContentForEntry(entryId).orEmpty()
            if (content.isBlank()) {
                _uiState.update { state ->
                    if (generation != aiModelGeneration) state else state.copy(
                        ai = state.ai.copy(
                            generatingOverviewIds = state.ai.generatingOverviewIds - entryId,
                            aiOverviewProgress = state.ai.aiOverviewProgress - entryId,
                            overviewError = MISSING_CONTENT_MESSAGE
                        )
                    )
                }
                return@launch
            }
            val result = reader.generateOverview(
                entryId = entryId,
                title = entry.title,
                body = content,
                modelId = modelId
            ) { progress ->
                _uiState.update { state ->
                    if (generation != aiModelGeneration || entryId !in state.readerWindowIds()) {
                        state
                    } else {
                        state.copy(
                            ai = state.ai.copy(
                                aiOverviewProgress = state.ai.aiOverviewProgress + (entryId to progress)
                            )
                        )
                    }
                }
            }

            _uiState.update { state ->
                if (generation != aiModelGeneration) {
                    state
                } else {
                    state.copy(
                        ai = state.ai.copy(
                            generatingOverviewIds = state.ai.generatingOverviewIds - entryId,
                            aiOverviewProgress = state.ai.aiOverviewProgress - entryId,
                            aiOverviews = result.fold(
                                onSuccess = { overview ->
                                    if (entryId in state.readerWindowIds()) {
                                        state.ai.aiOverviews + (entryId to overview)
                                    } else {
                                        state.ai.aiOverviews
                                    }
                                },
                                onFailure = { state.ai.aiOverviews }
                            ),
                            overviewError = result.exceptionOrNull()
                                ?.let { aiErrorText(it, R.string.article_summary_error) }
                                ?: state.ai.overviewError
                        )
                    )
                }
            }
        }
    }

    fun analyzeCredibility(entryId: Long, forceRefresh: Boolean = false) {
        val entry = _uiState.value.navigation.entries.find { it.id == entryId } ?: return
        if (!_uiState.value.ai.credibilityEnabled) return
        if (_uiState.value.ai.analyzingCredibilityIds.contains(entryId)) return
        if (!forceRefresh && _uiState.value.ai.credibilityReports.containsKey(entryId)) return
        val modelId = activeAiModelId
        val generation = aiModelGeneration

        val content = getContentForEntry(entryId).orEmpty()
        if (content.isBlank()) {
            _uiState.update { it.copy(ai = it.ai.copy(scoreError = MISSING_CONTENT_MESSAGE)) }
            return
        }

        _uiState.update {
            it.copy(
                ai = it.ai.copy(
                    analyzingCredibilityIds = it.ai.analyzingCredibilityIds + entryId,
                    scoreError = null
                )
            )
        }

        viewModelScope.launch {
            val result = reader.analyzeCredibility(
                entryId = entryId,
                source = entry.toCredibilitySource(content),
                forceRefresh = forceRefresh,
                modelId = modelId
            )

            _uiState.update { state ->
                if (generation != aiModelGeneration) {
                    state
                } else {
                    state.copy(
                        ai = state.ai.copy(
                            analyzingCredibilityIds = state.ai.analyzingCredibilityIds - entryId,
                            credibilityReports = result.fold(
                                onSuccess = { report ->
                                    if (entryId in state.readerWindowIds()) {
                                        state.ai.credibilityReports + (entryId to report)
                                    } else {
                                        state.ai.credibilityReports
                                    }
                                },
                                onFailure = { state.ai.credibilityReports }
                            ),
                            scoreError = result.exceptionOrNull()
                                ?.let { aiErrorText(it, R.string.article_credibility_error) }
                                ?: state.ai.scoreError
                        )
                    )
                }
            }
        }
    }

    private fun aiErrorText(error: Throwable, fallbackResId: Int): UiText = when (error) {
        is MissingAiApiKeyException -> UiText.Resource(R.string.article_ai_api_key_missing)
        is GemmaModelNotInstalledException -> UiText.Resource(R.string.article_ai_model_missing)
        is EmptyAiContentException -> MISSING_CONTENT_MESSAGE
        is UnknownHostException,
        is ConnectException,
        is SocketTimeoutException,
        is IOException -> UiText.Resource(R.string.article_ai_network_error)
        is AiProviderException -> when (error.statusCode) {
            401, 403 -> UiText.Resource(R.string.article_ai_api_key_invalid)
            404, 422 -> UiText.Resource(R.string.article_ai_model_unavailable)
            else -> UiText.Resource(R.string.article_ai_provider_error)
        }
        else -> UiText.Resource(fallbackResId)
    }

    private fun loadCachedCredibility(entryIds: List<Long>) {
        if (!_uiState.value.ai.credibilityEnabled) return
        val modelId = activeAiModelId
        val generation = aiModelGeneration
        // Looked up once per article. The pager re-emits its whole window every time a read state
        // changes, and articles with no stored report would be queried again on each of them.
        val missing = entryIds
            .filterNot { _uiState.value.ai.credibilityReports.containsKey(it) }
            .filter { checkedCredibilityIds.add(it) }
        if (missing.isEmpty()) return
        val sources = missing.mapNotNull { entryId ->
            val entry = _uiState.value.navigation.entries.find { it.id == entryId }
                ?: return@mapNotNull null
            val content = getContentForEntry(entryId)?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            entryId to entry.toCredibilitySource(content)
        }.toMap()
        if (sources.isEmpty()) return
        viewModelScope.launch {
            val cached = runCatchingCancellable {
                reader.getCachedCredibility(sources, modelId)
            }.getOrElse { emptyMap() }
            if (generation != aiModelGeneration) return@launch
            if (cached.isEmpty()) return@launch
            _uiState.update { state ->
                val retainedIds = state.readerWindowIds()
                state.copy(
                    ai = state.ai.copy(
                        credibilityReports = state.ai.credibilityReports.filterKeys { it in retainedIds } +
                            cached.filterKeys { it in retainedIds }
                    )
                )
            }
        }
    }

    private fun reloadAiCaches(entryIds: Set<Long>, modelId: String, generation: Int) {
        val overviewsToLoad = entryIds.mapNotNull { entryId ->
            _uiState.value.content.content[entryId]?.let { body -> entryId to body }
        }
        if (overviewsToLoad.isNotEmpty()) {
            viewModelScope.launch {
                val cachedOverviews = overviewsToLoad.mapNotNull { (entryId, body) ->
                    runCatchingCancellable {
                        reader.getCachedOverview(entryId, body, modelId)
                    }.getOrNull()?.let { entryId to it }
                }.toMap()
                if (generation != aiModelGeneration) return@launch
                _uiState.update { state ->
                    state.copy(
                        ai = state.ai.copy(
                            aiOverviews = cachedOverviews.filterKeys { it in state.readerWindowIds() }
                        )
                    )
                }
            }
        }
        loadCachedCredibility(entryIds.toList())
    }

    fun clearOverviewError() {
        _uiState.update { it.copy(ai = it.ai.copy(overviewError = null)) }
    }

    fun clearScoreError() {
        _uiState.update { it.copy(ai = it.ai.copy(scoreError = null)) }
    }
}
