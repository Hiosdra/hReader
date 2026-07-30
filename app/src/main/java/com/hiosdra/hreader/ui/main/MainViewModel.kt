package com.hiosdra.hreader.ui.main

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.hiosdra.hreader.data.ai.AiModelRepository
import com.hiosdra.hreader.data.ai.SelectedModelStatus
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.model.ArticleListEntry
import com.hiosdra.hreader.data.model.ArticleListQuery
import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.util.NetworkMonitor
import com.hiosdra.hreader.worker.SyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private const val TAG = "MainViewModel"

/** Long enough that typing does not run a query per keystroke, short enough to feel immediate. */
private const val SEARCH_DEBOUNCE_MILLIS = 250L

private const val KEY_SHOW_READ = "show_read_articles"
private const val KEY_STARRED_ONLY = "starred_only"
private const val KEY_SEARCH_QUERY = "search_query"

/**
 * How far past the action's own timestamp an article still counts as part of it. Marking a backlog
 * read is one statement per few hundred articles, so the stamps span a moment rather than an
 * instant; nothing the reader could open lands inside it.
 */
private val UNDO_GRACE: Duration = Duration.ofSeconds(2)

/**
 * A completed action the reader can still take back, surfaced as a snackbar.
 *
 * [markedAt] is when the action ran. Taking it back reverts what it changed and nothing else, so an
 * article opened while the snackbar was still up keeps the read state the reader just gave it.
 */
data class UndoableAction(
    val id: Long,
    val message: String,
    val articleIds: List<Long>,
    val markedAt: Instant
)

data class MainUiState(
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val feedTitle: String? = null,
    val searchQuery: String = "",
    val unavailableAiModelId: String? = null,
    val isOnline: Boolean = true,
    /**
     * Read articles stay in the cache for a month, so hiding them is a view choice rather than a
     * fact about what is stored. Showing them also brings in the offline backlog, which is read by
     * definition.
     */
    val showReadArticles: Boolean = false,
    val starredOnly: Boolean = false,
    val unreadCount: Int = 0,
    val readCount: Int = 0,
    val undo: UndoableAction? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val articleRepository: ArticleRepository,
    private val aiModelRepository: AiModelRepository,
    private val syncScheduler: SyncScheduler,
    private val savedStateHandle: SavedStateHandle,
    networkMonitor: NetworkMonitor
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MainUiState(
            isOnline = networkMonitor.isOnline.value,
            showReadArticles = savedStateHandle[KEY_SHOW_READ] ?: false,
            starredOnly = savedStateHandle[KEY_STARRED_ONLY] ?: false,
            searchQuery = savedStateHandle[KEY_SEARCH_QUERY] ?: ""
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val query = MutableStateFlow(
        ArticleListQuery(
            starredOnly = _uiState.value.starredOnly,
            includeRead = _uiState.value.showReadArticles,
            searchQuery = _uiState.value.searchQuery
        )
    )

    /** What the text field holds, which lags the query it drives by one debounce. */
    private val searchInput = MutableStateFlow(_uiState.value.searchQuery)

    /**
     * Cached in the view-model scope so a configuration change re-collects the pages already
     * loaded instead of starting the list again from the top.
     */
    val articles: Flow<PagingData<ArticleListEntry>> = query
        .flatMapLatest { articleRepository.pageArticles(it) }
        .cachedIn(viewModelScope)

    init {
        observeSearchInput()
        observeCounts()
        observeFeedTitle()
        checkSelectedAiModel()
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    /**
     * Only what the reader types afterwards is debounced. The first value is the query the screen
     * opened with, which [query] already holds — running it again would rebuild the list a quarter
     * of a second after it appeared.
     *
     * The drop comes before the debounce. After it, the first value to arrive is whatever the
     * debounce settles on, so someone who started typing within the debounce window had their
     * first search discarded and the list went on showing everything until they typed again.
     */
    @OptIn(FlowPreview::class)
    private fun observeSearchInput() {
        searchInput
            .drop(1)
            .debounce(SEARCH_DEBOUNCE_MILLIS)
            .distinctUntilChanged()
            .onEach { text -> query.update { it.withSearch(text) } }
            .launchIn(viewModelScope)
    }

    /**
     * Counted in SQLite over the whole list rather than over the loaded pages: with the list read
     * a page at a time, counting what is on screen would report a fraction of the real total.
     */
    private fun observeCounts() {
        query
            .map { it.feedId to it.starredOnly }
            .distinctUntilChanged()
            .flatMapLatest { (feedId, starredOnly) ->
                combine(
                    articleRepository.observeUnreadCount(feedId, starredOnly),
                    articleRepository.observeReadCount(feedId, starredOnly)
                ) { unread, read -> unread to read }
            }
            .onEach { (unread, read) ->
                _uiState.update { it.copy(unreadCount = unread, readCount = read) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeFeedTitle() {
        query
            .map { it.feedId }
            .distinctUntilChanged()
            .onEach { feedId ->
                val title = feedId?.let { runCatching { articleRepository.getFeed(it)?.title }.getOrNull() }
                _uiState.update { it.copy(feedTitle = title) }
            }
            .launchIn(viewModelScope)
    }

    fun setShowReadArticles(show: Boolean) {
        if (_uiState.value.showReadArticles == show) return
        savedStateHandle[KEY_SHOW_READ] = show
        _uiState.update { it.copy(showReadArticles = show) }
        query.update { it.withIncludeRead(show) }
    }

    fun setStarredOnly(starredOnly: Boolean) {
        if (_uiState.value.starredOnly == starredOnly) return
        savedStateHandle[KEY_STARRED_ONLY] = starredOnly
        _uiState.update { it.copy(starredOnly = starredOnly) }
        query.update { it.withStarredOnly(starredOnly, Instant.now()) }
    }

    fun dismissAiModelWarning() {
        _uiState.update { it.copy(unavailableAiModelId = null) }
    }

    private fun checkSelectedAiModel() {
        viewModelScope.launch {
            val status = runCatching { aiModelRepository.checkSelectedModel() }.getOrNull()
            if (status is SelectedModelStatus.Unavailable) {
                _uiState.update { it.copy(unavailableAiModelId = status.modelId) }
            }
        }
    }

    internal fun setFeed(feedId: Long?) {
        query.update { it.withFeed(feedId, Instant.now()) }
    }

    /** The query the reader is looking at, for opening one of its articles. */
    internal fun currentQuery(): ArticleListQuery = query.value

    fun updateSearchQuery(text: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = text
        _uiState.update { it.copy(searchQuery = text) }
        searchInput.value = text
    }

    fun refreshFromNetwork() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            // Before the network rather than after it: the read articles clear out the moment the
            // reader pulls, and a sync that fails leaves the list looking the same as one that
            // worked. Nothing is lost either way — the read filter brings them all back.
            query.update { it.withSessionRestarted(Instant.now()) }
            try {
                articleRepository.refreshArticles()
                // The refresh brings down the article list; the bodies and images that make those
                // articles readable offline are what this queues. Without it a pull-to-refresh
                // right before losing signal left a list of titles and nothing behind them.
                syncScheduler.enqueuePrefetch()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Network refresh failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun updateEntryReadStatus(entryId: Long, checked: Boolean) {
        applyReadStatus(listOf(entryId), read = checked)
    }

    /**
     * Marking everything read happens at once and is offered back afterwards, rather than being
     * guarded by a confirmation dialog: the dialog cost a tap every single time and still could not
     * put anything right when the answer was wrong.
     */
    fun markAllAsRead() {
        viewModelScope.launch {
            val current = query.value
            // Only what this actually changes. Sweeping in the already-read ones would push a
            // no-op update for every article the cache holds.
            val ids = runCatching { articleRepository.unreadIds(current.feedId, current.starredOnly) }
                .getOrElse {
                    Log.w(TAG, "Could not read the unread set", it)
                    return@launch
                }
            if (ids.isEmpty()) return@launch

            applyReadStatus(ids, read = true)
            _uiState.update {
                it.copy(
                    undo = UndoableAction(
                        id = System.currentTimeMillis(),
                        message = "Marked ${ids.size} as read",
                        articleIds = ids,
                        markedAt = Instant.now()
                    )
                )
            }
        }
    }

    fun undoLastAction() {
        val action = _uiState.value.undo ?: return
        _uiState.update { it.copy(undo = null) }
        viewModelScope.launch {
            // Only what the action itself marked. Reading an article while the snackbar is up
            // stamps it later than the action did, and used to be swept back to unread with it.
            val revertible = runCatching {
                articleRepository.idsStillReadSince(action.articleIds, action.markedAt.plus(UNDO_GRACE))
            }.getOrElse {
                Log.w(TAG, "Could not work out what the undo covers", it)
                return@launch
            }
            if (revertible.isNotEmpty()) applyReadStatus(revertible, read = false)
        }
    }

    fun dismissUndo() {
        _uiState.update { it.copy(undo = null) }
    }

    /**
     * Written straight to the cache; the list is a view over it and redraws itself. The failure is
     * logged rather than surfaced — the change is stored locally and queued for the next sync, so
     * the reader has lost nothing worth a dialog.
     */
    private fun applyReadStatus(entryIds: List<Long>, read: Boolean) {
        val newStatus = if (read) ArticleStatus.READ else ArticleStatus.UNREAD
        viewModelScope.launch {
            runCatching { articleRepository.updateReadStatus(entryIds.map { it.toString() }, newStatus) }
                .onFailure { Log.w(TAG, "Could not store read state for ${entryIds.size} articles", it) }
        }
    }
}
