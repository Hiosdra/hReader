package com.hiosdra.hreader.presentation.main

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.OfflinePreparationStage
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import com.hiosdra.hreader.core.application.usecase.main.MainReaderUseCase
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

private const val TAG = "MainViewModel"

private const val SEARCH_DEBOUNCE_MILLIS = 250L

private const val KEY_SHOW_READ = "show_read_articles"
private const val KEY_SEARCH_QUERY = "search_query"

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val reader: MainReaderUseCase,
    private val articlePaging: ArticlePagingProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MainUiState(
            isOnline = reader.isOnline.value,
            showReadArticles = savedStateHandle[KEY_SHOW_READ] ?: false,
            searchQuery = savedStateHandle[KEY_SEARCH_QUERY] ?: ""
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val readActions = MainArticleReadActions(reader, viewModelScope, _uiState)

    private val query = MutableStateFlow(
        ArticleListQuery(
            includeRead = _uiState.value.showReadArticles,
            searchQuery = _uiState.value.searchQuery
        )
    )

    private val searchInput = MutableStateFlow(_uiState.value.searchQuery)

    private val cacheReady = MutableStateFlow(false)

    private val readyQuery = cacheReady
        .filter { it }
        .flatMapLatest { query }

    val articles: Flow<PagingData<ArticleListItem>> = readyQuery
        .flatMapLatest { articlePaging.pageArticles(it) }
        .cachedIn(viewModelScope)

    init {
        ensureCacheOwner()
        observeSearchInput()
        MainUiObservers(reader, viewModelScope, readyQuery, _uiState).start()
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

    @OptIn(FlowPreview::class)
    private fun observeSearchInput() {
        searchInput
            .drop(1)
            .debounce(SEARCH_DEBOUNCE_MILLIS)
            .distinctUntilChanged()
            .onEach { text -> query.update { it.withSearch(text) } }
            .launchIn(viewModelScope)
    }

    fun setShowReadArticles(show: Boolean) {
        if (_uiState.value.showReadArticles == show) return
        savedStateHandle[KEY_SHOW_READ] = show
        _uiState.update { it.copy(showReadArticles = show) }
        query.update { it.withIncludeRead(show) }
    }

    fun dismissAiModelWarning() {
        _uiState.update { it.copy(unavailableAiModelId = null) }
    }

    internal fun setFeed(feedId: Long?) {
        query.update { it.withFeed(feedId, Instant.now()) }
    }

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
                val status = reader.observeOperation(operationId).first { current ->
                    current.state != SyncOperationState.IDLE &&
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
                        operationIds = setOf(operationId)
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
        readActions.updateEntryReadStatus(entryId, checked)
    }

    fun markAllAsRead(onMarkedAsRead: (Long) -> Unit = {}) =
        readActions.markAllAsRead(query.value.feedId, onMarkedAsRead)

    fun undoLastAction() = readActions.undoLastAction()

    fun dismissUndo(actionId: Long? = null) = readActions.dismissUndo(actionId)
}
