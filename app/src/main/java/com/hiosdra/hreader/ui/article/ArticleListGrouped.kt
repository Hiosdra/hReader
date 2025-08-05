package com.hiosdra.hreader.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.data.model.Entry
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ArticleListGrouped(
    entries: List<Entry>,
    navController: NavController,
    modifier: Modifier,
    onCheckedChange: (entryId: Long, checked: Boolean) -> Unit
) {
    val grouped = entries.groupBy { it.publishedAt.substring(0, 10) }
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
    val allArticleIds = entries.map { it.id }
    
    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        grouped.forEach { (date, items) ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = try {
                            LocalDate.parse(date).format(dateFormatter)
                        } catch (_: Exception) {
                            date
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
            itemsIndexed(items) { index, entry ->
                val globalIndex = allArticleIds.indexOf(entry.id)
                ArticleRow(
                    entry = entry,
                    navController = navController,
                    articleIds = allArticleIds,
                    articleIndex = globalIndex,
                    onCheckedChange = onCheckedChange
                )
            }
        }
    }
}
