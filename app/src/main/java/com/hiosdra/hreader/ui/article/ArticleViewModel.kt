package com.hiosdra.hreader.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.ai.ArticleAiService
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleAiOverviewRepository
import com.hiosdra.hreader.data.local.repository.ArticlePageRepository
import com.hiosdra.hreader.data.local.repository.ArticleReadingPositionRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.local.repository.CredibilityRepository
import com.hiosdra.hreader.data.model.ArticleListQuery
import com.hiosdra.hreader.data.model.ArticleContentSource
import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.CredibilityReport
import com.hiosdra.hreader.data.model.CredibilitySource
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.OfflinePage
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.util.ImageLoader
import com.hiosdra.hreader.util.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

private const val MISSING_CONTENT_MESSAGE =
    "The article text is still downloading. Try again in a moment."

/**
 * How many articles either side of the opened one the reader can swipe through.
 *
 * The pager observes its articles with one `id IN (…)` statement, and SQLite on Android binds at
 * most 999 variables — a cached backlog of several thousand would take the query down. Nobody
 * swipes dozens of articles in one sitting, and going back to the list starts a fresh window.
 */
private const val PAGER_WINDOW_RADIUS = 50
private const val CONTENT_CACHE_RADIUS = 1

private const val PARTIAL_CONTENT_MESSAGE =
    "The full text of this article was never downloaded, so this is only what the feed itself carried."

internal fun mergeReaderEntries(
    ids: List<Long>,
    latestEntries: List<Entry>,
    previousEntries: List<Entry>
): List<Entry> {
    val latestById = latestEntries.associateBy { it.id }
    val previousById = previousEntries.associateBy { it.id }
    return ids.mapNotNull { id -> latestById[id] ?: previousById[id] }
}

data class ArticleUiState(
    val entries: List<Entry> = emptyList(),
    val currentIndex: Int = 0,
    val currentListPosition: Int = 0,
    val listSize: Int = 0,
    val listWindowStartIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
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
    val contentError: String? = null,
    val isOnline: Boolean = true,
    val aiOverviews: Map<Long, String> = emptyMap(),
    val generatingOverviewIds: Set<Long> = emptySet(),
    val overviewError: String? = null,
    val credibilityEnabled: Boolean = false,
    val credibilityReports: Map<Long, CredibilityReport> = emptyMap(),
    val analyzingCredibilityIds: Set<Long> = emptySet(),
    val scoreError: String? = null
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
    private val articleRepository: ArticleRepository,
    private val articleReadingPositionRepository: ArticleReadingPositionRepository,
    private val articleContentRepository: ArticleContentRepository,
    private val articlePageRepository: ArticlePageRepository,
    private val articleAiService: ArticleAiService,
    private val articleAiOverviewRepository: ArticleAiOverviewRepository,
    private val credibilityRepository: CredibilityRepository,
    private val preferencesManager: PreferencesManager,
    private val imageLoader: ImageLoader,
    networkMonitor: NetworkMonitor
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ArticleUiState(
            credibilityEnabled = preferencesManager.getCredibilityScoreEnabled(),
            isOnline = networkMonitor.isOnline.value
        )
    )
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    /** The list is resolved once; a configuration change must not rebuild it under the pager. */
    private var listResolved = false

    /**
     * Articles whose text has already been asked for. The pager observes its articles, so every
     * read tick re-emits them; without this the article on screen was fetched again on each one.
     */
    private val requestedContentIds = mutableSetOf<Long>()

    private val requestedOfflinePageUrls = mutableMapOf<Long, String>()

    /** Articles whose stored credibility report has already been looked up, for the same reason. */
    private val checkedCredibilityIds = mutableSetOf<Long>()

    private val requestedReadingPositionIds = mutableSetOf<Long>()
    private val readingPositionWriteJobs = mutableMapOf<Long, Job>()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                val wasOnline = _uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                if (online && !wasOnline) retryPartialContent()
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
                articleReadingPositionRepository.getProgresses(missing)
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
                articlePageRepository.getOfflinePage(entryId, url)
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
                val text = articleContentRepository.getArticleContent(entryId, url, _uiState.value.isOnline)
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
        val localPaths = imageLoader.getLocalImagePaths(entryId)
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
        val cachedOverview = articleAiOverviewRepository.get(
            entryId = entryId,
            content = html,
            modelId = preferencesManager.getAiModelId()
        )
        if (cachedOverview != null) {
            _uiState.update { state ->
                if (entryId !in state.readerWindowIds()) {
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
            articleRepository.updateReadStatus(entry.id.toString(), newStatus)
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
        if (listResolved) return
        listResolved = true
        _uiState.update {
            it.copy(isLoading = true, credibilityEnabled = preferencesManager.getCredibilityScoreEnabled())
        }
        viewModelScope.launch {
            try {
                // The same visibility rule the list used, resolved to ids in one statement rather
                // than by loading the articles and filtering them here.
                val listed = articleRepository.listIds(
                    ArticleListQuery(
                        feedId = feedId,
                        starredOnly = starredOnly,
                        includeRead = includeRead,
                        sessionStart = Instant.ofEpochMilli(sessionStartMillis)
                    )
                )
                val ids = listed.windowAround(startArticleId).ifEmpty { listOf(startArticleId) }
                val startIndex = ids.indexOf(startArticleId).coerceAtLeast(0)
                val listSize = listed.size.coerceAtLeast(ids.size)
                val listWindowStartIndex = listed.indexOf(ids.first()).coerceAtLeast(0)
                val currentListPosition = (listWindowStartIndex + startIndex + 1)
                    .coerceIn(1, listSize)
                _uiState.update {
                    it.copy(
                        currentIndex = startIndex,
                        currentListPosition = currentListPosition,
                        listSize = listSize,
                        listWindowStartIndex = listWindowStartIndex
                    )
                }
                observeArticles(ids)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Could not load articles. Try again.") }
            }
        }
    }

    private suspend fun observeArticles(ids: List<Long>) {
        articleRepository.getArticlesByIds(ids).collect { articles ->
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

    /** [PAGER_WINDOW_RADIUS] articles either side of [articleId], clamped to what is there. */
    private fun List<Long>.windowAround(articleId: Long): List<Long> {
        if (size <= PAGER_WINDOW_RADIUS * 2) return this
        val focus = indexOf(articleId).coerceAtLeast(0)
        val from = (focus - PAGER_WINDOW_RADIUS).coerceIn(0, size - PAGER_WINDOW_RADIUS * 2)
        // Copied rather than a view: a sub-list would keep the whole id list alive behind it.
        return subList(from, from + PAGER_WINDOW_RADIUS * 2).toList()
    }

    fun setStarred(entryId: Long, starred: Boolean) {
        _uiState.update { state ->
            state.copy(entries = state.entries.map { if (it.id == entryId) it.copy(starred = starred) else it })
        }
        viewModelScope.launch {
            runCatching { articleRepository.updateStarred(entryId, starred) }
        }
    }

    fun getContentForEntry(entryId: Long): String? =
        _uiState.value.content[entryId]
            ?: _uiState.value.entries.find { it.id == entryId }?.content

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
            articleReadingPositionRepository.saveProgress(entryId, normalized)
        }
    }

    fun clearReadingProgress(entryId: Long) {
        _uiState.update { state ->
            state.copy(readingPositions = state.readingPositions - entryId)
        }
        enqueueReadingPositionWrite(entryId) {
            articleReadingPositionRepository.deleteProgress(entryId)
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

        _uiState.update {
            it.copy(generatingOverviewIds = it.generatingOverviewIds + entryId, overviewError = null)
        }

        viewModelScope.launch {
            val content = getContentForEntry(entryId).orEmpty()
            val modelId = preferencesManager.getAiModelId()
            val result = if (content.isBlank()) {
                Result.failure(Exception(MISSING_CONTENT_MESSAGE))
            } else {
                val cached = articleAiOverviewRepository.get(entryId, content, modelId)
                if (cached != null) {
                    Result.success(cached)
                } else articleAiService.generateArticleOverview(
                        title = entry.title,
                        content = content,
                        modelId = modelId
                    )
            }

            result.getOrNull()?.let { overview ->
                if (content.isNotBlank()) {
                    articleAiOverviewRepository.save(entryId, content, modelId, overview)
                }
            }

            _uiState.update { state ->
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
                        ?.let { "Couldn't generate summary. Try again." }
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
                        ?.let { it.message ?: "Failed to analyze credibility" }
                        ?: state.scoreError
                )
            }
        }
    }

    private fun loadCachedCredibility(entryIds: List<Long>) {
        if (!_uiState.value.credibilityEnabled) return
        // Looked up once per article. The pager re-emits its whole window every time a read state
        // changes, and articles with no stored report would be queried again on each of them.
        val missing = entryIds
            .filterNot { _uiState.value.credibilityReports.containsKey(it) }
            .filter { checkedCredibilityIds.add(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val cached = runCatching { credibilityRepository.getCached(missing) }.getOrElse { emptyMap() }
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

    fun clearOverviewError() {
        _uiState.update { it.copy(overviewError = null) }
    }

    fun clearScoreError() {
        _uiState.update { it.copy(scoreError = null) }
    }
}
