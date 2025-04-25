package com.hiosdra.hreader.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.NavController
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.ui.theme.MainChecked
import com.hiosdra.hreader.ui.theme.MainHeader
import com.hiosdra.hreader.ui.theme.MainUnchecked
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            TopAppBar(
                title = {
                    Text("All items", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate("feeds") }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Feeds")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Handle refresh */ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { /* Handle search */ }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { /* Handle overflow menu */ }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                }
            )
        },
    ) { paddingValues ->
        ArticleListGrouped(
            entries = uiState.entries,
            navController = navController,
            modifier = Modifier.padding(paddingValues)
        )
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
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = try {
                            LocalDate.parse(date).format(dateFormatter)
                        } catch (_: Exception) { date },
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
    var checked by remember { mutableStateOf(false) }
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
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.author ?: "Source", color = MaterialTheme.colorScheme.secondaryContainer, fontSize = 13.sp)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(entry.publishedAt.substring(11, 16), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                        }
                        Text(
                            entry.title,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val preview = entry.content?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: ""
                        if (preview.isNotBlank()) {
                            Text(
                                preview,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Box(
                        modifier = Modifier.size(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val imageUrl = entry.enclosures?.firstOrNull {
                            it.mimeType?.startsWith("image/") == true
                        }?.url
                        if (imageUrl != null) {
                            Image(
                                painter = rememberAsyncImagePainter(imageUrl),
                                contentDescription = "Article image",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MainChecked,
                        uncheckedColor = MainUnchecked
                    )
                )
            }
        }
    }
}
