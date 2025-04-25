package com.hiosdra.hreader.ui.article

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.hiosdra.hreader.data.model.Entry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    feedId: Long,
    navController: NavController,
    viewModel: ArticleListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    androidx.compose.runtime.LaunchedEffect(feedId) {
        viewModel.loadArticlesForFeed(feedId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Articles") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
            }
            else -> {
                ArticleListGrouped(
                    entries = uiState.entries,
                    navController = navController,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

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

@Composable
fun ArticleRow(
    entry: Entry,
    navController: NavController,
    articleIds: List<Long>,
    articleIndex: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clickable {
                navController.navigate("article/${articleIds.joinToString(",")}/$articleIndex")
            }
            .background(MaterialTheme.colorScheme.background),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    entry.author?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = entry.publishedAt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                // Remove iconUrl usage since Feed does not have iconUrl
                // Optionally, display a placeholder or nothing
                // if (entry.feed.iconUrl != null) {
                //     Image(
                //         painter = rememberAsyncImagePainter(entry.feed.iconUrl),
                //         contentDescription = null,
                //         modifier = Modifier.size(40.dp),
                //         contentScale = ContentScale.Crop
                //     )
                // }
            }
        }
    }
}

@Composable
fun HorizontalDivider(thickness: Dp, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}
