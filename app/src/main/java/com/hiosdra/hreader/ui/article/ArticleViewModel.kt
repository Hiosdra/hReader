package com.hiosdra.hreader.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.ai.ArticleAiService
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.local.repository.CredibilityRepository
import com.hiosdra.hreader.data.model.ArticleListQuery
import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.CredibilityReport
import com.hiosdra.hreader.data.model.CredibilitySource
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.util.ImageLoader
import com.hiosdra.hreader.util.NetworkMonitor
import com.hiosdra.hreader.util.absolutizeArticleImages
import com.hiosdra.hreader.widget.UnreadWidgetUpdater
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
 * swipes two hundred articles in one sitting, and going back to the list starts a fresh window.
 */
private const val PAGER_WINDOW_RADIUS = 200

private const val PARTIAL_CONTENT_MESSAGE =
    "The full text of this article was never downloaded, so this is only what the feed itself carried."

data class ArticleUiState(
    val entries: List<Entry> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val originalContent: Map<Long, String> = emptyMap(),
    /** [originalContent] with every image address resolved, ready to render. */
    val displayContent: Map<Long, String> = emptyMap(),
    /** Per article, where each of its images was downloaded, keyed by published address. */
    val localImagePaths: Map<Long, Map<String, String>> = emptyMap(),
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

class ArticleViewModel(
    private val articleRepository: ArticleRepository,
    private val articleContentRepository: ArticleContentRepository,
    private val articleAiService: ArticleAiService,
    private val credibilityRepository: CredibilityRepository,
    private val preferencesManager: PreferencesManager,
    private val imageLoader: ImageLoader,
    private val unreadWidgetUpdater: UnreadWidgetUpdater,
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

    /** Articles whose stored credibility report has already been looked up, for the same reason. */
    private val checkedCredibilityIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    fun setCurrentIndex(index: Int) {
        _uiState.update { it.copy(currentIndex = index) }
        val entry = _uiState.value.entries.getOrNull(index) ?: return
        loadOriginalContent(entry.id, entry.url)
    }

    /**
     * A failure here is the normal offline case, not an oddity: the fetch needs the backend, and
     * what the feed itself carried is all that is left. Silence used to leave the reader staring
     * at an empty screen wondering whether it was still loading.
     *
     * Asked for once per article: a failed fetch has already fallen back to what the feed carried,
     * so there is nothing a second attempt against the same cache would turn up.
     */
    private fun loadOriginalContent(entryId: Long, url: String) {
        if (!requestedContentIds.add(entryId)) return
        viewModelScope.launch {
            val content = runCatching { articleContentRepository.getArticleContent(entryId, url) }
                .getOrNull()
            if (content != null) {
                store(entryId, url, content, isFullText = true)
                return@launch
            }

            val syncedWithFeed = _uiState.value.entries.find { it.id == entryId }?.content
            if (syncedWithFeed.isNullOrBlank()) {
                _uiState.update { it.copy(contentError = PARTIAL_CONTENT_MESSAGE) }
            } else {
                store(entryId, url, syncedWithFeed, isFullText = false)
            }
        }
    }

    private suspend fun store(entryId: Long, url: String, content: String, isFullText: Boolean) {
        val prepared = absolutizeArticleImages(content, url)
        val localPaths = imageLoader.getLocalImagePaths(entryId)
        _uiState.update {
            it.copy(
                originalContent = it.originalContent + (entryId to content),
                displayContent = it.displayContent + (entryId to prepared),
                localImagePaths = it.localImagePaths + (entryId to localPaths),
                contentError = if (isFullText) it.contentError else PARTIAL_CONTENT_MESSAGE
            )
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
            // Reading here is how most articles stop being unread, so the widget hears about it
            // from the same place rather than waiting for the next sync. Requested rather than run:
            // paging through a feed marks one article read per swipe.
            unreadWidgetUpdater.requestRefresh()
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
                _uiState.update { it.copy(currentIndex = startIndex) }
                observeArticles(ids)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    private suspend fun observeArticles(ids: List<Long>) {
        articleRepository.getArticlesByIds(ids).collect { articles ->
            // Room returns them ordered by date; the pager has to walk them in the order the list
            // handed over, which is the same order but resolved once rather than re-derived.
            val byId = articles.associateBy { it.id }
            val ordered = ids.mapNotNull { byId[it] }
            _uiState.update { it.copy(entries = ordered, isLoading = false, error = null) }
            val currentArticle = ordered.getOrNull(_uiState.value.currentIndex)
            if (currentArticle != null) {
                loadOriginalContent(currentArticle.id, currentArticle.url)
            }
            loadCachedCredibility(ordered.map { it.id })
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
        _uiState.value.originalContent[entryId]
            ?: _uiState.value.entries.find { it.id == entryId }?.content

    /** What the reader sees: the same text with its images resolved to the downloaded copies. */
    fun getDisplayContentForEntry(entryId: Long): String? =
        _uiState.value.displayContent[entryId] ?: getContentForEntry(entryId)

    fun clearContentError() {
        _uiState.update { it.copy(contentError = null) }
    }

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
        // Looked up once per article. The pager re-emits its whole window every time a read state
        // changes, and articles with no stored report would be queried again on each of them.
        val missing = entryIds
            .filterNot { _uiState.value.credibilityReports.containsKey(it) }
            .filter { checkedCredibilityIds.add(it) }
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
