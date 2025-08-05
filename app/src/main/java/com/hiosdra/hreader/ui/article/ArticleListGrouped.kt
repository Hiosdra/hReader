package com.hiosdra.hreader.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.ui.theme.LocalExtendedColors
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
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy") // Removed the dot after day
    val allArticleIds = entries.map { it.id }
    val extendedColors = LocalExtendedColors.current

    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        grouped.forEach { (date, items) ->
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = try {
                            LocalDate.parse(date).format(dateFormatter)
                        } catch (_: Exception) {
                            date
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = extendedColors.header,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = extendedColors.divider,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
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
                if (index < items.size - 1) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = extendedColors.divider,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}
