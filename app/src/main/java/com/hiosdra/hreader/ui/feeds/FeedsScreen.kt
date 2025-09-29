package com.hiosdra.hreader.ui.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.util.NetworkMonitor
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.getKoin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsScreen(
    navController: NavController,
    viewModel: FeedsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val koin = getKoin()
    val networkMonitor = remember { koin.get<NetworkMonitor>() }
    val isOnline by networkMonitor.isOnline.collectAsState()

    val feedAddedFlow = navController.currentBackStackEntry?.savedStateHandle?.getStateFlow("feed_added", false)
    val feedAdded by feedAddedFlow?.collectAsState() ?: androidx.compose.runtime.remember { mutableStateOf(false) }
    val showSuccess = remember { mutableStateOf(false) }

    LaunchedEffect(feedAdded) {
        if (feedAdded) {
            showSuccess.value = true
            viewModel.reload()
            navController.currentBackStackEntry?.savedStateHandle?.set("feed_added", false)
            delay(2500)
            showSuccess.value = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscriptions") },
                actions = {
                    IconButton(onClick = { navController.navigate("add_feed") }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Feed")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (!isOnline) {
                Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(8.dp), contentAlignment = Alignment.Center) {
                    Text("Offline – some actions may fail", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (showSuccess.value) {
                Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(8.dp), contentAlignment = Alignment.Center) {
                    Text("Feed added", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                            Button(onClick = { viewModel.reload() }, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
                            OutlinedButton(onClick = { navController.navigate("add_feed") }, modifier = Modifier.padding(top = 8.dp)) { Text("Add Feed") }
                        }
                    }
                }
                uiState.filteredFeeds.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (uiState.searchQuery.isEmpty()) {
                                Text("No subscriptions yet", style = MaterialTheme.typography.titleMedium)
                                Button(onClick = { navController.navigate("add_feed") }, modifier = Modifier.padding(top = 16.dp)) { Text("Add your first feed") }
                            } else {
                                Text("No matches for '${'$'}{uiState.searchQuery}'", style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = { viewModel.updateSearchQuery("") }, modifier = Modifier.padding(top = 16.dp)) { Text("Clear search") }
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
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) { Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search") }
                            }
                        },
                        singleLine = true
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(uiState.filteredFeeds) { feed ->
                            val unreadCount = uiState.unreadCounts[feed.id] ?: 0
                            FeedItem(
                                feed = feed,
                                unreadCount = unreadCount,
                                onFeedClick = { navController.navigate("main?feedId=${'$'}{feed.id}") },
                                onDetailsClick = { navController.navigate("feed_details/${'$'}{feed.id}") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedItem(
    feed: Feed,
    unreadCount: Int = 0,
    onFeedClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onFeedClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = feed.title, style = MaterialTheme.typography.titleMedium)
                if (unreadCount > 0) {
                    Text(
                        text = "  ($unreadCount)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            feed.siteUrl?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = onDetailsClick) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_details_24),
                contentDescription = "Feed Details",
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
    HorizontalDivider()
}
