package com.hiosdra.hreader.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.ai.AiModelRepository
import com.hiosdra.hreader.data.ai.SelectedModelStatus
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.util.NetworkMonitor
import com.hiosdra.hreader.worker.SyncScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val entries: List<Entry> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val feedTitle: String? = null,
    val searchQuery: String = "",
    val unavailableAiModelId: String? = null,
    val isOnline: Boolean = true,
    val showBacklog: Boolean = false,
    val backlogCount: Int = 0
)

class MainViewModel(
    private val articleRepository: ArticleRepository,
    private val aiModelRepository: AiModelRepository,
    private val syncScheduler: SyncScheduler,
    networkMonitor: NetworkMonitor
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState(isOnline = networkMonitor.isOnline.value))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var fullList: List<Entry> = emptyList()
    private val sessionReadIds = mutableSetOf<Long>()
    private var collectionJob: Job? = null
    private var currentFeedId: Long? = null
    private var lastStatuses: Map<Long, ArticleStatus> = emptyMap()
    private var searchQuery: String = ""

    init {
        loadEntries() // default: all items
        checkSelectedAiModel()
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOnline = online)
            }
        }
    }

    fun setShowBacklog(show: Boolean) {
        if (_uiState.value.showBacklog == show) return
        _uiState.value = _uiState.value.copy(showBacklog = show)
        applyFilterAndEmit()
    }

    fun dismissAiModelWarning() {
        _uiState.value = _uiState.value.copy(unavailableAiModelId = null)
    }

    private fun checkSelectedAiModel() {
        viewModelScope.launch {
            val status = runCatching { aiModelRepository.checkSelectedModel() }.getOrNull()
            if (status is SelectedModelStatus.Unavailable) {
                _uiState.value = _uiState.value.copy(unavailableAiModelId = status.modelId)
            }
        }
    }

    internal fun setFeed(feedId: Long) {
        if (currentFeedId == feedId) return
        currentFeedId = feedId
        sessionReadIds.clear()
        loadEntries()
    }

    internal fun clearFeed() {
        if (currentFeedId == null) return
        currentFeedId = null
        sessionReadIds.clear()
        loadEntries()
    }

    internal fun loadEntries() {
        collectionJob?.cancel()
        Log.i("MainViewModel", "Loading entries for ${currentFeedId?.let { "feed $it" } ?: "all feeds"} (filtering previously read, keeping session newly read visible)")
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        collectionJob = viewModelScope.launch {
            try {
                val feedId = currentFeedId
                val flow = if (feedId == null) {
                    articleRepository.getAllArticlesOldestFirst()
                } else {
                    articleRepository.getAllArticlesForFeed(feedId)
                }
                val feedTitle = feedId?.let { articleRepository.getFeed(it)?.title }
                flow.collect { fetchedEntries ->
                    fetchedEntries.forEach { entry ->
                        val previous = lastStatuses[entry.id]
                        if (previous != null && previous != ArticleStatus.READ && entry.status == ArticleStatus.READ) {
                            sessionReadIds.add(entry.id)
                        }
                    }
                    fullList = fetchedEntries
                    lastStatuses = fetchedEntries.associate { it.id to it.status }
                    applyFilterAndEmit(isLoadingDone = true, feedTitle = feedTitle)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error loading entries: ${e.message}",
                    isLoading = false
                )
                Log.e("MainViewModel", "Error loading entries", e)
            }
        }
    }

    private fun applyFilterAndEmit(isLoadingDone: Boolean = false, feedTitle: String? = _uiState.value.feedTitle) {
        val trimmedQuery = searchQuery.trim().lowercase()
        val showBacklog = _uiState.value.showBacklog
        val filtered = fullList.filter { entry ->
            (
                entry.status != ArticleStatus.READ ||
                    sessionReadIds.contains(entry.id) ||
                    (showBacklog && entry.isBacklog)
                ) && (
                trimmedQuery.isBlank() ||
                    entry.title.lowercase().contains(trimmedQuery) ||
                    (entry.author?.lowercase()?.contains(trimmedQuery) == true) ||
                    entry.feed.title.lowercase().contains(trimmedQuery) ||
                    (entry.content?.lowercase()?.contains(trimmedQuery) == true)
                )
        }
        _uiState.value = _uiState.value.copy(
            entries = filtered,
            isLoading = if (isLoadingDone) false else _uiState.value.isLoading,
            feedTitle = feedTitle,
            searchQuery = searchQuery,
            backlogCount = fullList.count { it.isBacklog }
        )
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        applyFilterAndEmit()
    }

    fun refreshFromNetwork() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                sessionReadIds.clear()
                articleRepository.refreshArticles()
                // The refresh brings down the article list; the bodies and images that make those
                // articles readable offline are what this queues. Without it a pull-to-refresh
                // right before losing signal left a list of titles and nothing behind them.
                syncScheduler.enqueuePrefetch()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Network refresh failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
                applyFilterAndEmit()
            }
        }
    }

    fun updateEntryReadStatus(entryId: Long, checked: Boolean) {
        if (fullList.none { it.id == entryId }) return

        val newStatus = if (checked) ArticleStatus.READ else ArticleStatus.UNREAD
        fullList = fullList.map { if (it.id == entryId) it.copy(status = newStatus) else it }

        if (checked) sessionReadIds.add(entryId) else sessionReadIds.remove(entryId)
        applyFilterAndEmit()

        viewModelScope.launch {
            try {
                articleRepository.updateReadStatus(entryId.toString(), newStatus)
            } catch (_: Exception) { }
        }
    }

    fun markAllAsRead() {
        // Only the entries this actually changes. Sweeping in the already-read ones would push a
        // no-op update for every article the cache holds.
        val ids = fullList.filter { it.status != ArticleStatus.READ }.map { it.id }
        if (ids.isEmpty()) return

        fullList = fullList.map { it.copy(status = ArticleStatus.READ) }
        sessionReadIds.addAll(ids)
        applyFilterAndEmit()
        viewModelScope.launch {
            try {
                articleRepository.updateReadStatus(ids.map { it.toString() }, ArticleStatus.READ)
            } catch (_: Exception) { }
        }
    }
}
