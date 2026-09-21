package com.hiosdra.hreader.presentation.article

import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import com.hiosdra.hreader.core.application.ai.AiProvider
import com.hiosdra.hreader.core.domain.model.ArticleContentDelivery
import com.hiosdra.hreader.core.domain.model.ArticleContentKind
import com.hiosdra.hreader.core.domain.model.ArticleContentProvenance
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.OfflinePage
import com.hiosdra.hreader.presentation.text.UiText

internal val CONTENT_UNAVAILABLE_MESSAGE = UiText.Resource(R.string.article_content_unavailable)
internal val PARTIAL_CONTENT_MESSAGE = UiText.Resource(R.string.article_partial_content)

enum class ArticleContentLoadState {
    LOADING,
    FULL,
    FALLBACK,
    UNAVAILABLE
}

data class ArticleNavigationState(
    val entries: List<Entry> = emptyList(),
    val currentIndex: Int = 0,
    val currentListPosition: Int = 0,
    val listSize: Int = 0,
    val listWindowStartIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: UiText? = null
)

data class ArticleReadingProgressState(
    val positions: Map<Long, Float> = emptyMap(),
    val loadedIds: Set<Long> = emptySet()
) {
    fun withPosition(entryId: Long, progress: Float): ArticleReadingProgressState = copy(
        positions = positions + (entryId to progress.coerceIn(0f, 1f))
    )

    fun withoutPosition(entryId: Long): ArticleReadingProgressState = copy(
        positions = positions - entryId
    )

    fun withLoaded(entryIds: Collection<Long>): ArticleReadingProgressState = copy(
        loadedIds = loadedIds + entryIds
    )

    internal fun trimTo(retainedIds: Set<Long>): ArticleReadingProgressState = copy(
        positions = positions.filterKeys { it in retainedIds },
        loadedIds = loadedIds.filterTo(mutableSetOf()) { it in retainedIds }
    )
}

data class ArticleContentState(
    val content: Map<Long, String> = emptyMap(),
    val contentLoadStates: Map<Long, ArticleContentLoadState> = emptyMap(),
    val contentProvenance: Map<Long, ArticleContentProvenance> = emptyMap(),
    val leadImages: Map<Long, String?> = emptyMap(),
    val partialContentIds: Set<Long> = emptySet(),
    val localImagePaths: Map<Long, Map<String, String>> = emptyMap(),
    val readingProgress: ArticleReadingProgressState = ArticleReadingProgressState(),
    val offlinePages: Map<Long, OfflinePage> = emptyMap(),
    val contentError: UiText? = null,
    val isOnline: Boolean = true
)

data class ArticleAiState(
    val aiOverviews: Map<Long, String> = emptyMap(),
    val aiProvider: AiProvider = AiProvider.OPENROUTER,
    val generatingOverviewIds: Set<Long> = emptySet(),
    val aiOverviewProgress: Map<Long, ArticleAiProgress> = emptyMap(),
    val overviewError: UiText? = null,
    val credibilityEnabled: Boolean = false,
    val credibilityReports: Map<Long, CredibilityReport> = emptyMap(),
    val analyzingCredibilityIds: Set<Long> = emptySet(),
    val scoreError: UiText? = null
)

data class ArticleUiState(
    val navigation: ArticleNavigationState = ArticleNavigationState(),
    val content: ArticleContentState = ArticleContentState(),
    val ai: ArticleAiState = ArticleAiState()
)

internal fun ArticleNavigationState.readerWindowIds(index: Int = currentIndex): Set<Long> {
    if (entries.isEmpty()) return emptySet()
    val focus = index.coerceIn(0, entries.lastIndex)
    val windowSize = minOf(3, entries.size)
    val start = (focus - 1).coerceIn(0, entries.size - windowSize)
    val endExclusive = start + windowSize
    return entries.subList(start, endExclusive).mapTo(mutableSetOf()) { it.id }
}

internal fun ArticleNavigationState.selectIndex(index: Int): ArticleNavigationState {
    val listPosition = if (listSize > 0) {
        (listWindowStartIndex + index + 1).coerceIn(1, listSize)
    } else {
        0
    }
    return copy(currentIndex = index, currentListPosition = listPosition)
}

internal fun ArticleNavigationState.resolveList(
    currentIndex: Int,
    windowStartIndex: Int,
    totalCount: Int,
    entryCount: Int = entries.size
): ArticleNavigationState {
    val resolvedIndex = currentIndex.coerceIn(0, entryCount.coerceAtLeast(1) - 1)
    val resolvedListSize = totalCount.coerceAtLeast(entries.size)
    val resolvedPosition = (windowStartIndex + resolvedIndex + 1)
        .coerceIn(1, resolvedListSize.coerceAtLeast(1))
    return copy(
        currentIndex = resolvedIndex,
        currentListPosition = resolvedPosition,
        listSize = resolvedListSize,
        listWindowStartIndex = windowStartIndex
    )
}

internal fun ArticleContentState.contentLoadState(entryId: Long): ArticleContentLoadState =
    contentLoadStates[entryId] ?: when {
        entryId in content && entryId in partialContentIds -> ArticleContentLoadState.FALLBACK
        entryId in content -> ArticleContentLoadState.FULL
        else -> ArticleContentLoadState.LOADING
    }

internal fun ArticleContentState.errorFor(entryId: Long): UiText? = when {
    entryId in partialContentIds -> PARTIAL_CONTENT_MESSAGE
    contentLoadStates[entryId] == ArticleContentLoadState.UNAVAILABLE -> CONTENT_UNAVAILABLE_MESSAGE
    else -> null
}

internal fun ArticleContentState.trimTo(retainedIds: Set<Long>): ArticleContentState = copy(
    content = content.filterKeys { it in retainedIds },
    contentLoadStates = contentLoadStates.filterKeys { it in retainedIds },
    contentProvenance = contentProvenance.filterKeys { it in retainedIds },
    leadImages = leadImages.filterKeys { it in retainedIds },
    localImagePaths = localImagePaths.filterKeys { it in retainedIds },
    readingProgress = readingProgress.trimTo(retainedIds),
    offlinePages = offlinePages.filterKeys { it in retainedIds },
    partialContentIds = partialContentIds.filterTo(mutableSetOf()) { it in retainedIds }
)

internal fun ArticleAiState.trimTo(retainedIds: Set<Long>): ArticleAiState = copy(
    aiOverviews = aiOverviews.filterKeys { it in retainedIds },
    aiOverviewProgress = aiOverviewProgress.filterKeys { it in retainedIds },
    credibilityReports = credibilityReports.filterKeys { it in retainedIds }
)

internal fun ArticleUiState.readerWindowIds(index: Int = navigation.currentIndex): Set<Long> =
    navigation.readerWindowIds(index)

internal fun ArticleUiState.isCurrentEntry(entryId: Long): Boolean =
    navigation.entries.getOrNull(navigation.currentIndex)?.id == entryId

internal fun ArticleUiState.trimReaderState(index: Int = navigation.currentIndex): ArticleUiState {
    val retainedIds = readerWindowIds(index)
    return copy(
        content = content.trimTo(retainedIds),
        ai = ai.trimTo(retainedIds)
    )
}

internal fun ArticleUiState.getContentProvenance(entryId: Long): ArticleContentProvenance {
    content.contentProvenance[entryId]?.let { return it }
    val entry = navigation.entries.firstOrNull { it.id == entryId }
    return when (content.contentLoadState(entryId)) {
        ArticleContentLoadState.FALLBACK -> ArticleContentProvenance(
            kind = ArticleContentKind.FEED_FALLBACK,
            sourceUrl = entry?.url,
            delivery = ArticleContentDelivery.LOCAL_STORAGE,
            isComplete = false
        )
        ArticleContentLoadState.UNAVAILABLE -> ArticleContentProvenance(
            kind = ArticleContentKind.UNAVAILABLE,
            sourceUrl = entry?.url,
            isComplete = false
        )
        ArticleContentLoadState.FULL -> ArticleContentProvenance(
            kind = ArticleContentKind.FULL_ARTICLE,
            sourceUrl = entry?.url,
            isComplete = true
        )
        ArticleContentLoadState.LOADING -> if (entry?.let(::readerFallbackContent) != null) {
            ArticleContentProvenance(
                kind = ArticleContentKind.FEED_FALLBACK,
                sourceUrl = entry.url
            )
        } else {
            ArticleContentProvenance(
                kind = ArticleContentKind.UNKNOWN,
                sourceUrl = entry?.url
            )
        }
    }
}

internal fun ArticleUiState.displayedProvenance(
    entry: Entry,
    webViewActive: Boolean
): ArticleContentProvenance {
    if (!webViewActive) return getContentProvenance(entry.id)
    if (content.isOnline) {
        return ArticleContentProvenance(
            kind = ArticleContentKind.EXTERNAL_WEB_PAGE,
            sourceUrl = entry.url,
            delivery = ArticleContentDelivery.NETWORK
        )
    }
    return content.offlinePages[entry.id]?.let { page ->
        ArticleContentProvenance(
            kind = ArticleContentKind.SAVED_WEB_PAGE,
            sourceUrl = page.finalUrl.ifBlank { page.originalUrl },
            fetchedAt = page.fetchedAt,
            delivery = ArticleContentDelivery.LOCAL_STORAGE,
            isComplete = page.isComplete
        )
    } ?: getContentProvenance(entry.id)
}
