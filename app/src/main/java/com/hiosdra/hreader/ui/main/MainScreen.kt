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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import androidx.navigation.NavController
import com.hiosdra.hreader.data.model.Entry
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
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("All items", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Filter")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("All items") }, onClick = { expanded = false })
                            DropdownMenuItem(text = { Text("Favorites") }, onClick = { expanded = false })
                        }
                    }
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
    // Group entries by date (fake logic for demo)
    val grouped = entries.groupBy { it.publishedAt?.substring(0, 10) ?: "Unknown date" }
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy")
    // Prepare flat list of all article IDs for navigation
    val allArticleIds = entries.map { it.id }
    LazyColumn(modifier = modifier.background(Color(0xFF181818))) {
        grouped.forEach { (date, items) ->
            item {
                Surface(color = Color(0xFF272727), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = try {
                            LocalDate.parse(date).format(dateFormatter)
                        } catch (e: Exception) { date },
                        color = Color.LightGray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 14.sp
                    )
                }
            }
            itemsIndexed(items) { index, entry ->
                // Find the index of this entry in the flat list
                val globalIndex = allArticleIds.indexOf(entry.id)
                ArticleRow(
                    entry = entry,
                    navController = navController,
                    articleIds = allArticleIds,
                    articleIndex = globalIndex
                )
                HorizontalDivider(thickness = 8.dp, color = Color(0xFF222222))
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
            .clickable {
                navController.navigate("article/${articleIds.joinToString(",")}/$articleIndex")
            }
            .background(Color(0xFF232323)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Favorite",
                tint = Color(0xFFCCCC00),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.author ?: "Source", color = Color(0xFF7FBFFF), fontSize = 13.sp)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(entry.publishedAt.substring(11, 16), color = Color.Gray, fontSize = 12.sp)
                }
                Text(
                    entry.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val preview = entry.content?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: ""
                if (preview.isNotBlank()) {
                    Text(
                        preview,
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color(0xFF222222)),
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = entry.enclosures?.firstOrNull {
                    it.mimeType?.startsWith("image/") == true
                }?.url
                if (imageUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(imageUrl),
                        contentDescription = "Article image",
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            Checkbox(
                checked = checked,
                onCheckedChange = { checked = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF2196F3),
                    uncheckedColor = Color.Gray
                )
            )
        }
    }
}
