package com.hiosdra.hreader.presentation.main

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.hiosdra.hreader.core.application.ai.SelectedModelStatus
import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.OfflinePreparationStage
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import com.hiosdra.hreader.core.application.usecase.main.MainReaderUseCase
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.core.domain.model.ArticleListItem
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.text.UiText
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
    val message: UiText,
    val articleIds: List<Long>,
    val markedAt: Instant
)

data class MainUiState(
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
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
    val syncState: SyncOperationState = SyncOperationState.IDLE,
    val offlinePreparation: OfflinePreparationProgress = OfflinePreparationProgress(),
    val isBulkReadStateUpdating: Boolean = false,
    val undo: UndoableAction? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val reader: MainReaderUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MainUiState(
            isOnline = reader.isOnline.value,
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

    private val cacheReady = MutableStateFlow(false)

    private val readyQuery = cacheReady
        .filter { it }
        .flatMapLatest { query }

    /**
     * Cached in the view-model scope so a configuration change re-collects the pages already
     * loaded instead of starting the list again from the top.
     */
    val articles: Flow<PagingData<ArticleListItem>> = readyQuery
        .flatMapLatest { reader.pageArticles(it) }
        .cachedIn(viewModelScope)

    init {
        ensureCacheOwner()
        observeSearchInput()
        observeCounts()
        observeFeedTitle()
        checkSelectedAiModel()
        viewModelScope.launch {
            reader.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            reader.observeSync().collect { status ->
                _uiState.update { it.copy(syncState = status.state) }
            }
        }
        viewModelScope.launch {
            reader.observeOfflinePreparation().collect { progress ->
                _uiState.update { it.copy(offlinePreparation = progress) }
            }
        }
    }

    private fun ensureCacheOwner() {
        viewModelScope.launch {
            try {
                reader.ensureCacheOwner()
                cacheReady.value = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not verify the local cache owner", e)
                _uiState.update { it.copy(error = UiText.Resource(R.string.error_prepare_local_storage)) }
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
        readyQuery
            .map { it.feedId to it.starredOnly }
            .distinctUntilChanged()
            .flatMapLatest { (feedId, starredOnly) ->
                combine(
                    reader.observeUnreadCount(feedId, starredOnly),
                    reader.observeReadCount(feedId, starredOnly)
                ) { unread, read -> unread to read }
            }
            .onEach { (unread, read) ->
                _uiState.update { it.copy(unreadCount = unread, readCount = read) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeFeedTitle() {
        readyQuery
            .map { it.feedId }
            .distinctUntilChanged()
            .onEach { feedId ->
                val title = feedId?.let { runCatchingCancellable { reader.getFeed(it)?.title }.getOrNull() }
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
            val status = runCatchingCancellable { reader.checkSelectedAiModel() }.getOrNull()
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
        if (!_uiState.value.isOnline) {
            _uiState.update { it.copy(error = UiText.Resource(R.string.error_need_connection_refresh)) }
            return
        }
        viewModelScope.launch {
            val operationId = reader.requestRefresh()
            if (operationId == null) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = UiText.Resource(R.string.error_refresh_articles)
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    error = null,
                    syncState = SyncOperationState.RUNNING
                )
            }
            try {
                val status = reader.observeSync().first { current ->
                    operationId in current.workIds && current.state != SyncOperationState.IDLE &&
                        current.state != SyncOperationState.RUNNING
                }
                query.update { it.withSessionRestarted(Instant.now()) }
                if (status.state == SyncOperationState.FAILED ||
                    status.state == SyncOperationState.CANCELLED
                ) {
                    _uiState.update { it.copy(error = UiText.Resource(R.string.error_refresh_articles)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiText.Resource(R.string.error_refresh_articles)) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun prepareForOffline(): Boolean {
        if (!_uiState.value.isOnline) {
            _uiState.update { it.copy(error = UiText.Resource(R.string.error_need_connection_refresh)) }
            return false
        }
        val operationId = reader.prepareForOffline()
        if (operationId == null) {
            _uiState.update { it.copy(error = UiText.Resource(R.string.error_refresh_articles)) }
            return false
        }
        _uiState.update {
            it.copy(
                error = null,
                offlinePreparation = OfflinePreparationProgress(
                    isRunning = true,
                    status = SyncOperationStatus(
                        state = SyncOperationState.RUNNING,
                        workIds = setOf(operationId)
                    ),
                    stage = OfflinePreparationStage.SYNCING
                )
            )
        }
        return true
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
    fun markAllAsRead(onMarkedAsRead: (Long) -> Unit = {}) {
        if (_uiState.value.isBulkReadStateUpdating) return
        _uiState.update { it.copy(isBulkReadStateUpdating = true) }
        val current = query.value
        viewModelScope.launch {
            try {
                // Only what this actually changes. Sweeping in the already-read ones would push a
                // no-op update for every article the cache holds.
                val ids = runCatchingCancellable { reader.unreadIds(current.feedId, current.starredOnly) }
                    .getOrElse {
                        Log.w(TAG, "Could not read the unread set", it)
                        return@launch
                    }
                if (ids.isEmpty()) return@launch

                persistReadStatus(
                    entryIds = ids,
                    read = true,
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                undo = UndoableAction(
                                    id = System.currentTimeMillis(),
                                    message = UiText.Plural(
                                        id = R.plurals.main_marked_articles_read,
                                        count = ids.size,
                                        args = listOf(ids.size)
                                    ),
                                    articleIds = ids,
                                    markedAt = Instant.now()
                                )
                            )
                        }
                        current.feedId?.let(onMarkedAsRead)
                    }
                )
            } finally {
                _uiState.update { it.copy(isBulkReadStateUpdating = false) }
            }
        }
    }

    fun undoLastAction() {
        val action = _uiState.value.undo ?: return
        _uiState.update { it.copy(undo = null) }
        viewModelScope.launch {
            // Only what the action itself marked. Reading an article while the snackbar is up
            // stamps it later than the action did, and used to be swept back to unread with it.
            val revertible = runCatchingCancellable {
                reader.idsStillReadSince(action.articleIds, action.markedAt.plus(UNDO_GRACE))
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
    private fun applyReadStatus(entryIds: List<Long>, read: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            persistReadStatus(entryIds, read, onSuccess)
        }
    }

    private suspend fun persistReadStatus(
        entryIds: List<Long>,
        read: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        runCatchingCancellable { reader.updateReadStatus(entryIds, read) }
            .onFailure { Log.w(TAG, "Could not store read state for ${entryIds.size} articles", it) }
            .onSuccess { onSuccess() }
    }
}
