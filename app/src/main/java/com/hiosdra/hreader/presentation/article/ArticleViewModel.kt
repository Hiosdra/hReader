package com.hiosdra.hreader.presentation.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.EmptyAiContentException
import com.hiosdra.hreader.core.application.ai.GemmaModelNotInstalledException
import com.hiosdra.hreader.core.application.ai.MissingAiApiKeyException
import com.hiosdra.hreader.core.application.content.articlePreviewHtml
import com.hiosdra.hreader.core.application.usecase.article.ArticleReaderUseCase
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource
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

private val MISSING_CONTENT_MESSAGE = UiText.Resource(R.string.article_missing_content)

/**
 * How many articles either side of the opened one the reader can swipe through.
 *
 * The pager observes its articles with one `id IN (…)` statement, and SQLite on Android binds at
 * most 999 variables — a cached backlog of several thousand would take the query down. Nobody
 * swipes dozens of articles in one sitting, and going back to the list starts a fresh window.
 */
private const val PAGER_WINDOW_RADIUS = 50
private const val CONTENT_CACHE_RADIUS = 1

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

data class ArticleUiState(
    val entries: List<Entry> = emptyList(),
    val currentIndex: Int = 0,
    val currentListPosition: Int = 0,
    val listSize: Int = 0,
    val listWindowStartIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: UiText? = null,
    /** Each article's text as it is read, with every image address already resolved. */
    val content: Map<Long, String> = emptyMap(),
    /**
     * The picture to show above each article. Absent until that article's text has arrived, and
     * null for a body that already carries the picture itself, which read the same way to the
     * screen: nothing above the text. Settling it with the text is what keeps the picture from
     * appearing and disappearing again a moment after the reader arrives.
     */
    val leadImages: Map<Long, String?> = emptyMap(),
    /** Articles the full text of which was never downloaded, so only the feed's own text is shown. */
    val partialContentIds: Set<Long> = emptySet(),
    /** Per article, where each of its images was downloaded, keyed by published address. */
    val localImagePaths: Map<Long, Map<String, String>> = emptyMap(),
    val readingPositions: Map<Long, Float> = emptyMap(),
    val readingPositionLoadedIds: Set<Long> = emptySet(),
    val offlinePages: Map<Long, OfflinePage> = emptyMap(),
    val contentError: UiText? = null,
    val isOnline: Boolean = true,
    val aiOverviews: Map<Long, String> = emptyMap(),
    val generatingOverviewIds: Set<Long> = emptySet(),
    val overviewError: UiText? = null,
    val credibilityEnabled: Boolean = false,
    val credibilityReports: Map<Long, CredibilityReport> = emptyMap(),
    val analyzingCredibilityIds: Set<Long> = emptySet(),
    val scoreError: UiText? = null
)

internal fun ArticleUiState.readerWindowIds(index: Int = currentIndex): Set<Long> {
    if (entries.isEmpty()) return emptySet()
    val focus = index.coerceIn(0, entries.lastIndex)
    val start = (focus - CONTENT_CACHE_RADIUS).coerceAtLeast(0)
    val endExclusive = (focus + CONTENT_CACHE_RADIUS + 1).coerceAtMost(entries.size)
    return entries.subList(start, endExclusive).mapTo(mutableSetOf()) { it.id }
}

internal fun ArticleUiState.trimReaderState(index: Int = currentIndex): ArticleUiState {
    val retainedIds = readerWindowIds(index)
    return copy(
        content = content.filterKeys { it in retainedIds },
        leadImages = leadImages.filterKeys { it in retainedIds },
        localImagePaths = localImagePaths.filterKeys { it in retainedIds },
        readingPositions = readingPositions.filterKeys { it in retainedIds },
        readingPositionLoadedIds = readingPositionLoadedIds.filterTo(mutableSetOf()) { it in retainedIds },
        offlinePages = offlinePages.filterKeys { it in retainedIds },
        aiOverviews = aiOverviews.filterKeys { it in retainedIds },
        credibilityReports = credibilityReports.filterKeys { it in retainedIds },
        partialContentIds = partialContentIds.filterTo(mutableSetOf()) { it in retainedIds }
    )
}

class ArticleViewModel(
    private val reader: ArticleReaderUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ArticleUiState(
            credibilityEnabled = reader.credibilityEnabled(),
            isOnline = reader.isOnline.value
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

    init {
        viewModelScope.launch {
            reader.isOnline.collect { online ->
                val wasOnline = _uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
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
                        aiOverviews = emptyMap(),
                        credibilityReports = emptyMap(),
                        generatingOverviewIds = emptySet(),
                        analyzingCredibilityIds = emptySet(),
                        overviewError = null,
                        scoreError = null
                    )
                }
                reloadAiCaches(nearbyIds, modelId, generation)
            }
        }
    }

    private fun retryPartialContent() {
        val state = _uiState.value
        state.partialContentIds.forEach { entryId ->
            requestedContentIds.remove(entryId)
            state.entries.find { it.id == entryId }?.let { entry ->
                loadArticleText(entry.id, entry.url)
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        _uiState.update { state ->
            val arrivedAt = state.entries.getOrNull(index)?.id
            val listPosition = if (state.listSize > 0) {
                (state.listWindowStartIndex + index + 1).coerceIn(1, state.listSize)
            } else {
                0
            }
            state.copy(
                currentIndex = index,
                currentListPosition = listPosition,
                contentError = if (arrivedAt in state.partialContentIds) {
                    PARTIAL_CONTENT_MESSAGE
                } else {
                    state.contentError
                }
            ).trimReaderState(index)
        }
        loadAround(index)
    }

    /**
     * The article on screen and the one either side of it. Arriving at an article whose text has to
     * be fetched first shows what the feed carried and then replaces it, which the reader sees as
     * the article rebuilding itself; asking for the neighbours while they are still off screen is
     * what gives the swipe something ready to show.
     */
    private fun loadAround(index: Int) {
        val entries = _uiState.value.entries
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
        _uiState.update { it.trimReaderState(index) }
        loadReadingPositions(nearbyIds)
        nearby.forEach { entry ->
            loadOfflinePage(entry.id, entry.url)
            loadArticleText(entry.id, entry.url)
        }
        loadCachedCredibility(nearbyIds.toList())
    }

    private fun loadReadingPositions(articleIds: Set<Long>) {
        val state = _uiState.value
        val missing = articleIds.filter { entryId ->
            entryId !in state.readingPositionLoadedIds && requestedReadingPositionIds.add(entryId)
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
                    readingPositions = state.readingPositions.filterKeys { it in retainedIds } +
                        positions.filterKeys { it in retainedIds },
                    readingPositionLoadedIds = state.readingPositionLoadedIds +
                        missing.filter { it in retainedIds }
                )
            }
        }
    }

    private fun loadOfflinePage(entryId: Long, url: String) {
        val loadedPage = _uiState.value.offlinePages[entryId]
        if (loadedPage?.originalUrl == url || requestedOfflinePageUrls[entryId] == url) return
        requestedOfflinePageUrls[entryId] = url
        if (loadedPage != null) {
            _uiState.update { it.copy(offlinePages = it.offlinePages - entryId) }
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
                    offlinePages = if (offlinePage == null) {
                        state.offlinePages - entryId
                    } else {
                        state.offlinePages + (entryId to offlinePage)
                    }
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
    private fun loadArticleText(entryId: Long, url: String) {
        if (_uiState.value.content.containsKey(entryId)) return
        if (!requestedContentIds.add(entryId)) return
        viewModelScope.launch {
            try {
                val text = reader.getArticleContent(entryId, url, _uiState.value.isOnline)
                store(entryId, text.html, text.leadImageUrl, text.source == ArticleContentSource.FULL)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                markPartial(entryId)
            }
        }
    }

    private fun markPartial(entryId: Long) {
        _uiState.update { state ->
            if (entryId !in state.readerWindowIds()) state else state.withPartialContent(entryId)
        }
    }

    /**
     * Raised for the article being read and only remembered for the rest: the neighbours are loaded
     * before the reader gets to them, and a message about an article they are not looking at yet
     * reads as a fault with the one they are. Arriving at that article raises it.
     */
    private fun ArticleUiState.withPartialContent(entryId: Long) = copy(
        partialContentIds = partialContentIds + entryId,
        contentError = if (entries.getOrNull(currentIndex)?.id == entryId) {
            PARTIAL_CONTENT_MESSAGE
        } else {
            contentError
        }
    )

    private suspend fun store(entryId: Long, html: String, leadImage: String?, isFullText: Boolean) {
        val modelId = activeAiModelId
        val generation = aiModelGeneration
        val localPaths = reader.getLocalImagePaths(entryId)
        _uiState.update {
            val stored = it.trimReaderState()
            if (entryId !in stored.readerWindowIds()) return@update stored
            val withContent = stored.copy(
                content = stored.content + (entryId to html),
                leadImages = stored.leadImages + (entryId to leadImage),
                localImagePaths = stored.localImagePaths + (entryId to localPaths)
            )
            if (isFullText) {
                withContent.copy(
                    partialContentIds = withContent.partialContentIds - entryId,
                    contentError = if (withContent.entries.getOrNull(withContent.currentIndex)?.id == entryId &&
                        withContent.contentError == PARTIAL_CONTENT_MESSAGE
                    ) null else stored.contentError
                )
            } else {
                withContent.withPartialContent(entryId)
            }
        }
        val cachedOverview = reader.getCachedOverview(entryId, html, modelId)
        if (cachedOverview != null) {
            _uiState.update { state ->
                if (generation != aiModelGeneration || entryId !in state.readerWindowIds()) {
                    state
                } else {
                    state.copy(aiOverviews = state.aiOverviews + (entryId to cachedOverview))
                }
            }
        }
        requestedContentIds.retainAll(_uiState.value.readerWindowIds())
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
        starredOnly: Boolean,
        includeRead: Boolean,
        sessionStartMillis: Long
    ) {
        if (listResolved || listResolutionJob?.isActive == true) return
        _uiState.update {
            it.copy(isLoading = true, credibilityEnabled = reader.credibilityEnabled())
        }
        listResolutionJob = viewModelScope.launch {
            try {
                val window = reader.getArticleListWindow(
                    ArticleListQuery(
                        feedId = feedId,
                        starredOnly = starredOnly,
                        includeRead = includeRead,
                        sessionStart = Instant.ofEpochMilli(sessionStartMillis)
                    ),
                    articleId = startArticleId,
                    radius = PAGER_WINDOW_RADIUS
                )
                val ids = window.ids.ifEmpty { listOf(startArticleId) }
                val startIndex = window.currentIndex.coerceIn(0, ids.lastIndex.coerceAtLeast(0))
                val currentListPosition = (window.windowStartIndex + startIndex + 1)
                    .coerceIn(1, window.totalCount.coerceAtLeast(1))
                _uiState.update {
                    it.copy(
                        currentIndex = startIndex,
                        currentListPosition = currentListPosition,
                        listSize = window.totalCount.coerceAtLeast(ids.size),
                        listWindowStartIndex = window.windowStartIndex
                    )
                }
                listResolved = true
                observeArticles(ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listResolved = false
                _uiState.update { it.copy(isLoading = false, error = UiText.Resource(R.string.article_load_error)) }
            }
        }
    }

    private suspend fun observeArticles(ids: List<Long>) {
        reader.observeArticles(ids).collect { articles ->
            // Room returns them ordered by date; the pager has to walk them in the order the list
            // handed over, which is the same order but resolved once rather than re-derived.
            _uiState.update { state ->
                state.copy(
                    entries = mergeReaderEntries(ids, articles, state.entries),
                    isLoading = false,
                    error = null
                )
            }
            loadAround(_uiState.value.currentIndex)
        }
    }

    fun setStarred(entryId: Long, starred: Boolean) {
        _uiState.update { state ->
            state.copy(entries = state.entries.map { if (it.id == entryId) it.copy(starred = starred) else it })
        }
        viewModelScope.launch {
            runCatchingCancellable { reader.updateStarred(entryId, starred) }
        }
    }

    fun getContentForEntry(entryId: Long): String? =
        _uiState.value.content[entryId]
            ?: _uiState.value.entries.find { it.id == entryId }?.let(::readerFallbackContent)

    fun getLeadImageForEntry(entryId: Long): String? = _uiState.value.leadImages[entryId]

    fun getReadingProgressForEntry(entryId: Long): Float? =
        _uiState.value.readingPositions[entryId]

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
                state.copy(readingPositions = state.readingPositions + (entryId to normalized))
            }
        }
        enqueueReadingPositionWrite(entryId) {
            reader.saveReadingProgress(entryId, normalized)
        }
    }

    fun clearReadingProgress(entryId: Long) {
        _uiState.update { state ->
            state.copy(readingPositions = state.readingPositions - entryId)
        }
        enqueueReadingPositionWrite(entryId) {
            reader.clearReadingProgress(entryId)
        }
    }

    private fun enqueueReadingPositionWrite(entryId: Long, operation: suspend () -> Unit) {
        readingPositionWriteJobs.remove(entryId)?.cancel()
        readingPositionWriteJobs[entryId] = viewModelScope.launch { operation() }
    }

    fun getOfflinePageForEntry(entryId: Long): OfflinePage? = _uiState.value.offlinePages[entryId]

    fun clearContentError() {
        _uiState.update { it.copy(contentError = null) }
    }

    fun generateAiOverview(entryId: Long) {
        val entry = _uiState.value.entries.find { it.id == entryId } ?: return
        if (_uiState.value.generatingOverviewIds.contains(entryId)) return
        val modelId = activeAiModelId
        val generation = aiModelGeneration

        _uiState.update {
            it.copy(generatingOverviewIds = it.generatingOverviewIds + entryId, overviewError = null)
        }

        viewModelScope.launch {
            val content = getContentForEntry(entryId).orEmpty()
            if (content.isBlank()) {
                _uiState.update { state ->
                    if (generation != aiModelGeneration) state else state.copy(
                        generatingOverviewIds = state.generatingOverviewIds - entryId,
                        overviewError = MISSING_CONTENT_MESSAGE
                    )
                }
                return@launch
            }
            val result = reader.generateOverview(entryId, entry.title, content, modelId)

            _uiState.update { state ->
                if (generation != aiModelGeneration) {
                    state
                } else {
                    state.copy(
                        generatingOverviewIds = state.generatingOverviewIds - entryId,
                        aiOverviews = result.fold(
                            onSuccess = { overview ->
                                if (entryId in state.readerWindowIds()) {
                                    state.aiOverviews + (entryId to overview)
                                } else {
                                    state.aiOverviews
                                }
                            },
                            onFailure = { state.aiOverviews }
                        ),
                        overviewError = result.exceptionOrNull()
                            ?.let { aiErrorText(it, R.string.article_summary_error) }
                            ?: state.overviewError
                    )
                }
            }
        }
    }

    fun analyzeCredibility(entryId: Long, forceRefresh: Boolean = false) {
        val entry = _uiState.value.entries.find { it.id == entryId } ?: return
        if (!_uiState.value.credibilityEnabled) return
        if (_uiState.value.analyzingCredibilityIds.contains(entryId)) return
        if (!forceRefresh && _uiState.value.credibilityReports.containsKey(entryId)) return
        val modelId = activeAiModelId
        val generation = aiModelGeneration

        val content = getContentForEntry(entryId).orEmpty()
        if (content.isBlank()) {
            _uiState.update { it.copy(scoreError = MISSING_CONTENT_MESSAGE) }
            return
        }

        _uiState.update {
            it.copy(analyzingCredibilityIds = it.analyzingCredibilityIds + entryId, scoreError = null)
        }

        viewModelScope.launch {
            val result = reader.analyzeCredibility(
                entryId = entryId,
                source = CredibilitySource(
                    title = entry.title,
                    content = content,
                    author = entry.author,
                    feedTitle = entry.feed.title,
                    url = entry.url,
                    publishedAt = entry.publishedAt
                ),
                forceRefresh = forceRefresh,
                modelId = modelId
            )

            _uiState.update { state ->
                if (generation != aiModelGeneration) {
                    state
                } else {
                    state.copy(
                        analyzingCredibilityIds = state.analyzingCredibilityIds - entryId,
                        credibilityReports = result.fold(
                            onSuccess = { report ->
                                if (entryId in state.readerWindowIds()) {
                                    state.credibilityReports + (entryId to report)
                                } else {
                                    state.credibilityReports
                                }
                            },
                            onFailure = { state.credibilityReports }
                        ),
                        scoreError = result.exceptionOrNull()
                            ?.let { aiErrorText(it, R.string.article_credibility_error) }
                            ?: state.scoreError
                    )
                }
            }
        }
    }

    private fun aiErrorText(error: Throwable, fallbackResId: Int): UiText = when (error) {
        is MissingAiApiKeyException -> UiText.Resource(R.string.article_ai_api_key_missing)
        is GemmaModelNotInstalledException -> UiText.Resource(R.string.article_ai_model_missing)
        is EmptyAiContentException -> MISSING_CONTENT_MESSAGE
        else -> UiText.Resource(fallbackResId)
    }

    private fun loadCachedCredibility(entryIds: List<Long>) {
        if (!_uiState.value.credibilityEnabled) return
        val modelId = activeAiModelId
        val generation = aiModelGeneration
        // Looked up once per article. The pager re-emits its whole window every time a read state
        // changes, and articles with no stored report would be queried again on each of them.
        val missing = entryIds
            .filterNot { _uiState.value.credibilityReports.containsKey(it) }
            .filter { checkedCredibilityIds.add(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val cached = runCatchingCancellable {
                reader.getCachedCredibility(missing, modelId)
            }.getOrElse { emptyMap() }
            if (generation != aiModelGeneration) return@launch
            if (cached.isEmpty()) return@launch
            _uiState.update { state ->
                val retainedIds = state.readerWindowIds()
                state.copy(
                    credibilityReports = state.credibilityReports.filterKeys { it in retainedIds } +
                        cached.filterKeys { it in retainedIds }
                )
            }
        }
    }

    private fun reloadAiCaches(entryIds: Set<Long>, modelId: String, generation: Int) {
        val overviewsToLoad = entryIds.mapNotNull { entryId ->
            _uiState.value.content[entryId]?.let { body -> entryId to body }
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
                        aiOverviews = cachedOverviews.filterKeys { it in state.readerWindowIds() }
                    )
                }
            }
        }
        loadCachedCredibility(entryIds.toList())
    }

    fun clearOverviewError() {
        _uiState.update { it.copy(overviewError = null) }
    }

    fun clearScoreError() {
        _uiState.update { it.copy(scoreError = null) }
    }
}
