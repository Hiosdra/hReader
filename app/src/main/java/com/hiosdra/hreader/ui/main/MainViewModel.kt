package com.hiosdra.hreader.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.model.Entry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val entries: List<Entry> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class MainViewModel(private val articleRepository: ArticleRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var fullList: List<Entry> = emptyList()
    private val sessionReadIds = mutableSetOf<Long>()
    private var collectStarted = false

    init {
        loadEntries()
    }

    internal fun loadEntries() {
        if (collectStarted) return
        collectStarted = true
        Log.i("MainViewModel", "Loading all entries (filtering previously read, keeping session newly read visible)")
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                articleRepository.getAllArticlesOldestFirst().collect { fetchedEntries ->
                    fullList = fetchedEntries
                    applyFilterAndEmit(isLoadingDone = true)
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

    private fun applyFilterAndEmit(isLoadingDone: Boolean = false) {
        val filtered = fullList.filter { entry ->
            if (entry.status != "read") true else sessionReadIds.contains(entry.id)
        }
        _uiState.value = _uiState.value.copy(
            entries = filtered,
            isLoading = if (isLoadingDone) false else _uiState.value.isLoading
        )
    }

    fun refreshFromNetwork() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                // Clearing sessionReadIds enforces removal of previously session-kept read items after refresh
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
        val target = fullList.find { it.id == entryId } ?: return
        val newStatus = if (checked) "read" else "unread"
        fullList = fullList.map {
            if (it.id == entryId) it.copy(status = newStatus) else it
        }
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
        fullList = fullList.map { it.copy(status = "read") }
        sessionReadIds.addAll(ids)
        applyFilterAndEmit()
        viewModelScope.launch {
            try {
                articleRepository.updateReadStatus(ids.map { it.toString() }, "read")
            } catch (_: Exception) { }
        }
    }
}
