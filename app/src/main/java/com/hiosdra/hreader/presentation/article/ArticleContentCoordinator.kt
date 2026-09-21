package com.hiosdra.hreader.presentation.article

import com.hiosdra.hreader.core.application.content.hasReadableArticleText
import com.hiosdra.hreader.core.application.usecase.article.ArticleReaderUseCase
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.core.domain.model.ArticleContentDelivery
import com.hiosdra.hreader.core.domain.model.ArticleContentKind
import com.hiosdra.hreader.core.domain.model.ArticleContentProvenance
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import com.hiosdra.hreader.core.domain.model.ArticleText
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.OfflinePage
import com.hiosdra.hreader.core.domain.model.toProvenance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ArticleContentCoordinator(
    private val reader: ArticleReaderUseCase,
    private val uiState: MutableStateFlow<ArticleUiState>,
    private val scope: CoroutineScope,
    private val loadCachedOverview: (Long, String) -> Unit,
    private val loadCachedCredibility: (List<Long>) -> Unit
) {
    private val requestedContentIds = mutableSetOf<Long>()
    private val requestedOfflinePageUrls = mutableMapOf<Long, String>()
    private val requestedReadingPositionIds = mutableSetOf<Long>()
    private val imagePathJobs = mutableMapOf<Long, Job>()

    fun retryPartialContent() {
        val state = uiState.value
        val unavailableIds = state.content.contentLoadStates
            .filterValues { it == ArticleContentLoadState.UNAVAILABLE }
            .keys
        (state.content.partialContentIds + unavailableIds).forEach(::retryContent)
    }

    fun loadAround(index: Int) {
        val entries = uiState.value.navigation.entries
        val nearby = listOfNotNull(
            entries.getOrNull(index),
            entries.getOrNull(index - 1),
            entries.getOrNull(index + 1)
        )
        val nearbyIds = nearby.mapTo(mutableSetOf(), Entry::id)
        requestedOfflinePageUrls.keys.retainAll(nearbyIds)
        requestedContentIds.retainAll(nearbyIds)
        requestedReadingPositionIds.retainAll(nearbyIds)
        imagePathJobs.keys
            .filterNot(nearbyIds::contains)
            .toList()
            .forEach { imagePathJobs.remove(it)?.cancel() }
        uiState.update { it.trimReaderState(index) }
        loadReadingPositions(nearbyIds)
        observeLocalImagePaths(nearbyIds)
        nearby.forEach { entry ->
            loadOfflinePage(entry.id, entry.url)
            loadArticleText(entry.id, entry.url)
        }
        loadCachedCredibility(nearbyIds.toList())
    }

    fun getContentForEntry(entryId: Long): String? {
        val state = uiState.value
        return state.content.content[entryId]
            ?: state.navigation.entries.find { it.id == entryId }?.let(::readerFallbackContent)
    }

    fun getContentStateForEntry(entryId: Long): ArticleContentLoadState =
        uiState.value.content.contentLoadState(entryId)

    fun getContentProvenanceForEntry(entryId: Long): ArticleContentProvenance =
        uiState.value.getContentProvenance(entryId)

    fun getLeadImageForEntry(entryId: Long): String? = uiState.value.content.leadImages[entryId]

    fun getOfflinePageForEntry(entryId: Long): OfflinePage? = uiState.value.content.offlinePages[entryId]

    fun retryContent(entryId: Long) {
        val entry = uiState.value.navigation.entries.find { it.id == entryId } ?: return
        requestedContentIds.remove(entryId)
        uiState.update { state ->
            val hasUsableStoredContent = hasReadableArticleText(state.content.content[entryId])
            state.copy(
                content = state.content.copy(
                    content = if (hasUsableStoredContent) {
                        state.content.content
                    } else {
                        state.content.content - entryId
                    },
                    leadImages = if (hasUsableStoredContent) {
                        state.content.leadImages
                    } else {
                        state.content.leadImages - entryId
                    },
                    contentLoadStates = state.content.contentLoadStates +
                        (entryId to ArticleContentLoadState.LOADING),
                    contentProvenance = if (hasUsableStoredContent) {
                        state.content.contentProvenance
                    } else {
                        state.content.contentProvenance - entryId
                    },
                    partialContentIds = state.content.partialContentIds - entryId,
                    contentError = if (state.isCurrentEntry(entryId)) null else state.content.contentError
                )
            )
        }
        loadArticleText(entry.id, entry.url, force = true)
    }

    private fun observeLocalImagePaths(articleIds: Set<Long>) {
        articleIds.forEach { entryId ->
            if (imagePathJobs[entryId]?.isActive == true) return@forEach
            imagePathJobs[entryId] = scope.launch {
                reader.observeLocalImagePaths(entryId).collect { paths ->
                    uiState.update { state ->
                        if (entryId !in state.readerWindowIds()) {
                            state
                        } else {
                            state.copy(
                                content = state.content.copy(
                                    localImagePaths = state.content.localImagePaths + (entryId to paths)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun loadReadingPositions(articleIds: Set<Long>) {
        val state = uiState.value
        val missing = articleIds.filter { entryId ->
            entryId !in state.content.readingProgress.loadedIds && requestedReadingPositionIds.add(entryId)
        }
        if (missing.isEmpty()) return
        scope.launch {
            val positions = runCatchingCancellable {
                reader.getReadingProgresses(missing)
            }.getOrElse {
                requestedReadingPositionIds.removeAll(missing.toSet())
                emptyMap()
            }
            uiState.update { state ->
                val retainedIds = state.readerWindowIds()
                state.copy(
                    content = state.content.copy(
                        readingProgress = state.content.readingProgress.copy(
                            positions = state.content.readingProgress.positions.filterKeys { it in retainedIds } +
                                positions.filterKeys { it in retainedIds },
                            loadedIds = state.content.readingProgress.loadedIds +
                                missing.filter { it in retainedIds }
                        )
                    )
                )
            }
        }
    }

    private fun loadOfflinePage(entryId: Long, url: String) {
        val loadedPage = uiState.value.content.offlinePages[entryId]
        if (loadedPage?.originalUrl == url || requestedOfflinePageUrls[entryId] == url) return
        requestedOfflinePageUrls[entryId] = url
        if (loadedPage != null) {
            uiState.update {
                it.copy(content = it.content.copy(offlinePages = it.content.offlinePages - entryId))
            }
        }
        scope.launch {
            val offlinePage = runCatchingCancellable {
                reader.getOfflinePage(entryId, url)
            }.getOrNull()
            uiState.update { state ->
                if (requestedOfflinePageUrls[entryId] != url) return@update state
                requestedOfflinePageUrls.remove(entryId)
                state.copy(
                    content = state.content.copy(
                        offlinePages = if (offlinePage == null) {
                            state.content.offlinePages - entryId
                        } else {
                            state.content.offlinePages + (entryId to offlinePage)
                        }
                    )
                )
            }
        }
    }

    private fun loadArticleText(entryId: Long, url: String, force: Boolean = false) {
        if (!force && uiState.value.content.content.containsKey(entryId)) return
        if (!requestedContentIds.add(entryId)) return
        uiState.update { state ->
            if (entryId !in state.readerWindowIds()) {
                state
            } else {
                state.copy(
                    content = state.content.copy(
                        contentLoadStates = state.content.contentLoadStates +
                            (entryId to ArticleContentLoadState.LOADING)
                    )
                )
            }
        }
        scope.launch {
            val text = runCatchingCancellable {
                reader.getArticleContent(entryId, url, uiState.value.content.isOnline)
            }.getOrElse {
                val state = uiState.value
                val entry = state.navigation.entries.find { it.id == entryId }
                when {
                    hasReadableArticleText(state.content.content[entryId]) -> restoreStoredContent(entryId)
                    entry != null && readerFallbackContent(entry) != null -> markPartial(entryId)
                    else -> markUnavailable(entryId)
                }
                return@launch
            }
            store(entryId, text)
        }
    }

    private fun restoreStoredContent(entryId: Long) {
        updateVisibleEntry(entryId) { state ->
            val previousProvenance = state.content.contentProvenance[entryId]
            val isFallback = previousProvenance?.kind == ArticleContentKind.FEED_FALLBACK
            val restoredState = if (isFallback) {
                ArticleContentLoadState.FALLBACK
            } else {
                ArticleContentLoadState.FULL
            }
            state.copy(
                content = state.content.copy(
                    contentLoadStates = state.content.contentLoadStates + (entryId to restoredState),
                    contentProvenance = previousProvenance?.let {
                        state.content.contentProvenance
                    } ?: state.content.contentProvenance + (
                        entryId to ArticleContentProvenance(
                            kind = if (isFallback) ArticleContentKind.FEED_FALLBACK else ArticleContentKind.FULL_ARTICLE,
                            sourceUrl = state.navigation.entries.firstOrNull { it.id == entryId }?.url,
                            delivery = ArticleContentDelivery.LOCAL_STORAGE,
                            isComplete = !isFallback
                        )
                    ),
                    partialContentIds = if (isFallback) {
                        state.content.partialContentIds + entryId
                    } else {
                        state.content.partialContentIds - entryId
                    },
                    contentError = if (state.isCurrentEntry(entryId)) {
                        if (isFallback) PARTIAL_CONTENT_MESSAGE else null
                    } else {
                        state.content.contentError
                    }
                )
            )
        }
    }

    private fun markPartial(entryId: Long) {
        updateVisibleEntry(entryId) { state ->
            val partialState = state.withPartialContent(entryId)
            partialState.copy(
                content = partialState.content.copy(
                    contentProvenance = state.content.contentProvenance + (
                        entryId to ArticleContentProvenance(
                            kind = ArticleContentKind.FEED_FALLBACK,
                            sourceUrl = state.navigation.entries.firstOrNull { it.id == entryId }?.url,
                            delivery = ArticleContentDelivery.LOCAL_STORAGE,
                            isComplete = false
                        )
                    )
                )
            )
        }
    }

    private fun markUnavailable(entryId: Long) {
        updateVisibleEntry(entryId) { state ->
            state.copy(
                content = state.content.copy(
                    contentLoadStates = state.content.contentLoadStates +
                        (entryId to ArticleContentLoadState.UNAVAILABLE),
                    partialContentIds = state.content.partialContentIds - entryId,
                    contentProvenance = state.content.contentProvenance + (
                        entryId to ArticleContentProvenance(
                            kind = ArticleContentKind.UNAVAILABLE,
                            sourceUrl = state.navigation.entries.firstOrNull { it.id == entryId }?.url,
                            isComplete = false
                        )
                    ),
                    contentError = if (state.isCurrentEntry(entryId)) {
                        CONTENT_UNAVAILABLE_MESSAGE
                    } else {
                        state.content.contentError
                    }
                )
            )
        }
    }

    private suspend fun store(entryId: Long, text: ArticleText) {
        val provenance = text.toProvenance()
        val localPaths = reader.getLocalImagePaths(entryId)
        updateVisibleEntry(entryId) {
            val stored = it.trimReaderState()
            val withContent = stored.content.copy(
                content = stored.content.content + (entryId to text.html),
                contentLoadStates = stored.content.contentLoadStates + (
                    entryId to if (text.source == ArticleContentSource.FULL) {
                        ArticleContentLoadState.FULL
                    } else {
                        ArticleContentLoadState.FALLBACK
                    }
                ),
                contentProvenance = stored.content.contentProvenance + (entryId to provenance),
                leadImages = stored.content.leadImages + (entryId to text.leadImageUrl),
                localImagePaths = stored.content.localImagePaths + (entryId to localPaths)
            )
            val withContentState = stored.copy(content = withContent)
            if (text.source == ArticleContentSource.FULL) {
                withContentState.copy(
                    content = withContent.copy(
                        partialContentIds = withContent.partialContentIds - entryId,
                        contentError = if (
                            withContentState.isCurrentEntry(entryId) &&
                                withContent.contentError in setOf(
                                PARTIAL_CONTENT_MESSAGE,
                                CONTENT_UNAVAILABLE_MESSAGE
                            )
                        ) {
                            null
                        } else {
                            stored.content.contentError
                        }
                    )
                )
            } else {
                withContentState.withPartialContent(entryId)
            }
        }
        loadCachedOverview(entryId, text.html)
        requestedContentIds.retainAll(uiState.value.readerWindowIds())
    }

    private fun updateVisibleEntry(
        entryId: Long,
        transform: (ArticleUiState) -> ArticleUiState
    ) {
        uiState.update { state ->
            if (entryId in state.readerWindowIds()) transform(state) else state
        }
    }

    private fun ArticleUiState.withPartialContent(entryId: Long) = copy(
        content = content.copy(
            contentLoadStates = content.contentLoadStates +
                (entryId to ArticleContentLoadState.FALLBACK),
            partialContentIds = content.partialContentIds + entryId,
            contentError = if (isCurrentEntry(entryId)) {
                PARTIAL_CONTENT_MESSAGE
            } else {
                content.contentError
            }
        )
    )
}
