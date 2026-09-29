package com.hiosdra.hreader.presentation.main

import com.hiosdra.hreader.core.application.ai.SelectedModelStatus
import com.hiosdra.hreader.core.application.usecase.main.MainReaderUseCase
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class MainUiObservers(
    private val reader: MainReaderUseCase,
    private val scope: CoroutineScope,
    private val readyQuery: Flow<ArticleListQuery>,
    private val uiState: MutableStateFlow<MainUiState>
) {
    fun start() {
        observeRuntime()
        observeCounts()
        observeFeedTitle()
        checkSelectedAiModel()
    }

    private fun observeRuntime() {
        scope.launch {
            reader.isOnline.collect { online ->
                uiState.update { it.copy(isOnline = online) }
            }
        }
        scope.launch {
            reader.observeSync().collect { status ->
                uiState.update { it.copy(syncState = status.state) }
            }
        }
        scope.launch {
            reader.observeHasCompletedSync().collect { hasCompleted ->
                uiState.update { it.copy(hasCompletedSync = hasCompleted) }
            }
        }
        scope.launch {
            reader.observeOfflinePreparation().collect { progress ->
                uiState.update { it.copy(offlinePreparation = progress) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCounts() {
        readyQuery
            .map { it.feedId }
            .distinctUntilChanged()
            .flatMapLatest(reader::observeStatusCounts)
            .onEach { counts ->
                uiState.update { it.copy(unreadCount = counts.unread, readCount = counts.read) }
            }
            .launchIn(scope)
    }

    private fun observeFeedTitle() {
        readyQuery
            .map { it.feedId }
            .distinctUntilChanged()
            .onEach { feedId ->
                val title = feedId?.let { runCatchingCancellable { reader.getFeed(it)?.title }.getOrNull() }
                uiState.update { it.copy(feedTitle = title) }
            }
            .launchIn(scope)
    }

    private fun checkSelectedAiModel() {
        scope.launch {
            var status = runCatchingCancellable { reader.checkSelectedAiModel() }.getOrNull()
            if (status == SelectedModelStatus.Unknown) {
                runCatchingCancellable { reader.refreshAiModelCatalogIfStale() }
                status = runCatchingCancellable { reader.checkSelectedAiModel() }.getOrNull()
            }
            uiState.update {
                it.copy(unavailableAiModelId = (status as? SelectedModelStatus.Unavailable)?.modelId)
            }
        }
    }
}
