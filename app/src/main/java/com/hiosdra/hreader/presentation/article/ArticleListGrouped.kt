package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil3.ImageLoader as CoilImageLoader
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import com.hiosdra.hreader.core.domain.model.ArticleListEntry
import com.hiosdra.hreader.core.domain.model.ArticleListItem
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private const val ARTICLE_CONTENT_TYPE = "article"
private const val DAY_CONTENT_TYPE = "day"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleListGrouped(
    items: LazyPagingItems<ArticleListItem>,
    modifier: Modifier,
    listState: LazyListState,
    onOpen: (Long) -> Unit,
    onCheckedChange: (entryId: Long, checked: Boolean) -> Unit,
    imageLoader: ArticleImageLoader,
    coilImageLoader: CoilImageLoader,
    remoteResourcePolicy: RemoteResourcePolicy,
    isOnline: Boolean = true,
) {
    val snapshot = items.itemSnapshotList.items
    val imageRequests = remember(snapshot) {
        snapshot.mapNotNull { item ->
            (item as? ArticleListItem.Article)?.entry?.let { entry ->
                entry.imageUrl?.let { imageUrl -> entry.id to imageUrl }
            }
        }
    }
    var localImagePaths by remember { mutableStateOf<Map<Long, Map<String, String>>>(emptyMap()) }
    LaunchedEffect(imageRequests) {
        val ids = imageRequests.map { it.first }
        localImagePaths = imageLoader.getLocalImagePaths(ids)
    }
    val separatorIndices = remember(snapshot) {
        snapshot.mapIndexedNotNull { index, item ->
            index.takeIf { item is ArticleListItem.DayHeader }
        }
    }
    val scrollbarMetrics by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val measuredArticleSizes = layoutInfo.visibleItemsInfo
                .filter { it.contentType == ARTICLE_CONTENT_TYPE }
                .map { it.size }
                .filter { it > 0 }
            val averageArticleSizePx = measuredArticleSizes
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.roundToInt()
                ?: 0
            listScrollbarMetrics(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                totalItemsCount = layoutInfo.totalItemsCount,
                averageItemSizePx = averageArticleSizePx,
                viewportSizePx = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset,
                isAtEnd = !listState.canScrollForward && listState.canScrollBackward
            )
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            var rangeStart = 0
            separatorIndices.forEach { separatorIndex ->
                articleRange(
                    pagingItems = items,
                    snapshot = snapshot,
                    start = rangeStart,
                    endExclusive = separatorIndex,
                    onOpen = onOpen,
                    onCheckedChange = onCheckedChange,
                    articleImageLoader = imageLoader,
                    coilImageLoader = coilImageLoader,
                    remoteResourcePolicy = remoteResourcePolicy,
                    isOnline = isOnline,
                    localImagePaths = localImagePaths
                )
                val separator = snapshot[separatorIndex] as ArticleListItem.DayHeader
                stickyHeader(
                    key = "day-${separator.date}",
                    contentType = DAY_CONTENT_TYPE
                ) {
                    DayHeader(separator.date)
                }
                rangeStart = separatorIndex + 1
            }
            articleRange(
                pagingItems = items,
                snapshot = snapshot,
                start = rangeStart,
                endExclusive = snapshot.size,
                onOpen = onOpen,
                onCheckedChange = onCheckedChange,
                articleImageLoader = imageLoader,
                coilImageLoader = coilImageLoader,
                remoteResourcePolicy = remoteResourcePolicy,
                isOnline = isOnline,
                localImagePaths = localImagePaths
            )
            when (val append = items.loadState.append) {
                is LoadState.Loading -> item(key = "append-spinner") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is LoadState.Error -> item(key = "append-error") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.article_more_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { items.retry() }) { Text(stringResource(R.string.action_retry)) }
                    }
                }

                else -> Unit
            }
        }
        VerticalScrollbar(
            metrics = scrollbarMetrics,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
        )
    }
}

private fun LazyListScope.articleRange(
    pagingItems: LazyPagingItems<ArticleListItem>,
    snapshot: List<ArticleListItem>,
    start: Int,
    endExclusive: Int,
    onOpen: (Long) -> Unit,
    onCheckedChange: (entryId: Long, checked: Boolean) -> Unit,
    articleImageLoader: ArticleImageLoader,
    coilImageLoader: CoilImageLoader,
    remoteResourcePolicy: RemoteResourcePolicy,
    isOnline: Boolean,
    localImagePaths: Map<Long, Map<String, String>>
) {
    if (start >= endExclusive) return
    items(
        count = endExclusive - start,
        key = { offset -> itemKey(snapshot[start + offset]) },
        contentType = { offset -> itemContentType(snapshot[start + offset]) }
    ) { offset ->
        val index = start + offset
        when (val item = pagingItems[index]) {
            is ArticleListItem.Article -> ArticleRow(
                entry = item.entry,
                onOpen = onOpen,
                onCheckedChange = onCheckedChange,
                isOnline = isOnline,
                articleImageLoader = articleImageLoader,
                coilImageLoader = coilImageLoader,
                remoteResourcePolicy = remoteResourcePolicy,
                localImagePath = item.entry.imageUrl?.let { localImagePaths[item.entry.id]?.get(it) }
            )

            is ArticleListItem.DayHeader -> DayHeader(item.date)
            null -> Unit
        }
    }
}

private fun itemKey(item: ArticleListItem): String = when (item) {
    is ArticleListItem.Article -> "article-${item.entry.id}"
    is ArticleListItem.DayHeader -> "day-${item.date}"
}

private fun itemContentType(item: ArticleListItem): String = when (item) {
    is ArticleListItem.Article -> ARTICLE_CONTENT_TYPE
    is ArticleListItem.DayHeader -> DAY_CONTENT_TYPE
}

@Composable
private fun DayHeader(date: LocalDate) {
    val locale = LocalLocale.current.platformLocale
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
        ) {
            Text(
                text = date.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
    }
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

internal data class ArticleDay(
    val date: LocalDate,
    val startIndex: Int,
    val size: Int
)

internal fun List<ArticleListEntry>.groupIntoDays(): List<ArticleDay> {
    if (isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val days = mutableListOf<ArticleDay>()
    var runStart = 0
    var runDate = this[0].publishedAt.atZone(zone).toLocalDate()

    forEachIndexed { index, entry ->
        val date = entry.publishedAt.atZone(zone).toLocalDate()
        if (date != runDate) {
            days += ArticleDay(runDate, runStart, index - runStart)
            runStart = index
            runDate = date
        }
    }
    days += ArticleDay(runDate, runStart, size - runStart)
    return days
}
