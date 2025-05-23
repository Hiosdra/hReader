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

    init {
        loadEntries()
    }

    internal fun loadEntries() {
        Log.i("MainViewModel", "Loading entries...")
        if (_uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                articleRepository.getAllArticlesOldestFirst().collect { fetchedEntries ->
                    _uiState.value = _uiState.value.copy(entries = fetchedEntries, isLoading = false)
                    Log.i("MainViewModel", "Entries loaded successfully: ${fetchedEntries.size} entries")
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

    fun refreshFromNetwork() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                articleRepository.refreshArticles()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Network refresh failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    fun updateEntryReadStatus(entryId: Long, checked: Boolean) {
        val entries = _uiState.value.entries.map {
            if (it.id == entryId) it.copy(status = if (checked) "read" else "unread") else it
        }
        _uiState.value = _uiState.value.copy(entries = entries)
        viewModelScope.launch {
            try {
                articleRepository.updateReadStatus(entryId.toString(), if (checked) "read" else "unread")
            } catch (e: Exception) {
                // Optionally handle error (rollback local change, show error, etc)
            }
        }
    }

    fun markAllAsRead() {
        val entries = _uiState.value.entries.map { it.copy(status = "read") }
        _uiState.value = _uiState.value.copy(entries = entries)
        viewModelScope.launch {
            try {
                val articleIds = _uiState.value.entries.map { it.id.toString() }
                articleRepository.updateReadStatus(articleIds, "read")
            } catch (e: Exception) {
                // Optionally handle error (rollback local change, show error, etc)
            }
        }
    }
}
