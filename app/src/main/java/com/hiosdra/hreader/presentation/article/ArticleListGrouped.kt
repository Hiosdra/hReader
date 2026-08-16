package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.hiosdra.hreader.R
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.hiosdra.hreader.core.domain.model.ArticleListEntry
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleListGrouped(
    items: LazyPagingItems<ArticleListEntry>,
    modifier: Modifier,
    listState: LazyListState,
    onOpen: (Long) -> Unit,
    onCheckedChange: (entryId: Long, checked: Boolean) -> Unit
) {
    // Only over what is loaded. Grouping the whole list was what forced it into memory in the first
    // place; the days are recomputed as pages arrive, and the keys keep positions steady.
    val loaded = items.itemSnapshotList.items
    val days = remember(loaded) { loaded.groupIntoDays() }

    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        state = listState
    ) {
        days.forEach { day ->
            stickyHeader(key = "day-${day.date}") {
                DayHeader(day.date)
            }
            items(
                count = day.size,
                key = { offset -> loaded[day.startIndex + offset].id }
            ) { offset ->
                val index = day.startIndex + offset
                // Read through the pager rather than out of the snapshot: this is what tells it how
                // far down the reader has got, and so when to fetch the next page.
                val entry = items[index] ?: return@items
                ArticleRow(
                    entry = entry,
                    onOpen = onOpen,
                    onCheckedChange = onCheckedChange
                )
            }
        }
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

            // Without this the list simply stops, with no sign that more was meant to follow.
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

/** A run of articles published on the same day, as a window onto the loaded list. */
internal data class ArticleDay(
    val date: LocalDate,
    val startIndex: Int,
    val size: Int
)

/**
 * Runs rather than a `groupBy`: the list already arrives ordered by publication date, so equal days
 * are adjacent, and windowing it avoids copying every row into a second structure on each page.
 */
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
