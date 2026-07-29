package com.hiosdra.hreader.ui.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.util.NetworkMonitor
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.getKoin

@Composable
fun SubscriptionsPanel(
    selectedFeedId: Long?,
    visible: Boolean,
    onSelectFeed: (Long?) -> Unit,
    onFeedDetails: (Long) -> Unit,
    onAddFeed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val koin = getKoin()
    val networkMonitor = remember { koin.get<NetworkMonitor>() }
    val isOnline by networkMonitor.isOnline.collectAsState()

    LaunchedEffect(visible) {
        if (visible) viewModel.reload()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Subscriptions", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onAddFeed) {
                Icon(Icons.Filled.Add, contentDescription = "Add subscription")
            }
        }
        if (!isOnline) {
            Text(
                text = "Offline – some actions may fail",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        when {
            uiState.isLoading && uiState.feeds.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.feeds.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(text = uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.reload() }, modifier = Modifier.padding(top = 16.dp)) {
                            Text("Retry")
                        }
                        OutlinedButton(onClick = onAddFeed, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Add subscription")
                        }
                    }
                }
            }
            uiState.feeds.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No subscriptions yet", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onAddFeed, modifier = Modifier.padding(top = 16.dp)) {
                            Text("Add your first feed")
                        }
                    }
                }
            }
            else -> {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search subscriptions...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        PanelRow(
                            title = "All items",
                            selected = selectedFeedId == null,
                            onClick = { onSelectFeed(null) }
                        )
                        HorizontalDivider()
                    }
                    if (uiState.filteredFeeds.isEmpty()) {
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                Text(
                                    "No matches for '${uiState.searchQuery}'",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedButton(
                                    onClick = { viewModel.updateSearchQuery("") },
                                    modifier = Modifier.padding(top = 12.dp)
                                ) { Text("Clear search") }
                            }
                        }
                    }
                    items(uiState.filteredFeeds) { feed ->
                        PanelRow(
                            title = feed.title,
                            subtitle = feed.siteUrl,
                            unreadCount = uiState.unreadCounts[feed.id] ?: 0,
                            selected = feed.id == selectedFeedId,
                            onClick = { onSelectFeed(feed.id) },
                            onDetailsClick = { onFeedDetails(feed.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    unreadCount: Int = 0,
    onDetailsClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (unreadCount > 0) {
                    Text(
                        text = "  ($unreadCount)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onDetailsClick != null) {
            IconButton(onClick = onDetailsClick) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_details_24),
                    contentDescription = "Feed details",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
