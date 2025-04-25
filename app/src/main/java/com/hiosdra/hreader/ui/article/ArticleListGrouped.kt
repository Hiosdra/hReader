package com.hiosdra.hreader.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.ui.main.ArticleRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ArticleListGrouped(entries: List<Entry>, navController: NavController, modifier: Modifier) {
    val grouped = entries.groupBy { it.publishedAt.substring(0, 10) }
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy")
    val allArticleIds = entries.map { it.id }
    LazyColumn(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        grouped.forEach { (date, items) ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = try {
                            LocalDate.parse(date).format(dateFormatter)
                        } catch (_: Exception) {
                            date
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 14.sp
                    )
                }
            }
            itemsIndexed(items) { index, entry ->
                val globalIndex = allArticleIds.indexOf(entry.id)
                ArticleRow(
                    entry = entry,
                    navController = navController,
                    articleIds = allArticleIds,
                    articleIndex = globalIndex
                )
                HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.background)
            }
        }
    }
}
