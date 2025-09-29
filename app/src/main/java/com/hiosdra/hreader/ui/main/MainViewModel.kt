package com.hiosdra.hreader.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.Entry
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
    val searchQuery: String = ""
)

class MainViewModel(private val articleRepository: ArticleRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var fullList: List<Entry> = emptyList()
    private val sessionReadIds = mutableSetOf<Long>()
    private var collectionJob: Job? = null
    private var currentFeedId: Long? = null
    private var lastStatuses: Map<Long, ArticleStatus> = emptyMap()
    private var searchQuery: String = ""

    init {
        loadEntries() // default: all items
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
        val filtered = fullList.filter { entry ->
            (entry.status != ArticleStatus.READ || sessionReadIds.contains(entry.id)) && (
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
            searchQuery = searchQuery
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
        val ids = fullList.map { it.id }
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
