package com.hiosdra.hreader.ui.article

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.data.model.Entry
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleListGrouped(
    entries: List<Entry>,
    navController: NavController,
    modifier: Modifier,
    onCheckedChange: (entryId: Long, checked: Boolean) -> Unit
) {
    val sortedEntries = entries.sortedBy { it.publishedAt }
    val grouped = sortedEntries.groupBy { it.publishedAt.atZone(ZoneId.systemDefault()).toLocalDate() }
    val sortedKeys = grouped.keys.sorted() // oldest date first
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
    val allArticleIds = sortedEntries.map { it.id }
    val articleIndexById = allArticleIds.withIndex().associate { (index, id) -> id to index }

    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        sortedKeys.forEach { date ->
            val dayEntries = grouped[date].orEmpty()
            stickyHeader {
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
                            text = date.format(dateFormatter),
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
            items(dayEntries, key = { it.id }) { entry ->
                ArticleRow(
                    entry = entry,
                    navController = navController,
                    articleIds = allArticleIds,
                    articleIndex = articleIndexById.getValue(entry.id),
                    onCheckedChange = onCheckedChange
                )
            }
        }
    }
}
