package com.hiosdra.hreader.ui.feeds

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.data.model.CreateFeedRequest
import com.hiosdra.hreader.data.model.DiscoverRequest
import com.hiosdra.hreader.data.model.DiscoverResponse
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.MinifluxApiService
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedScreen(
    navController: NavController,
    apiService: MinifluxApiService,
    onFeedAdded: () -> Unit = {}
) {
    val context = LocalContext.current
    var feedUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discoveredFeeds by remember { mutableStateOf<List<DiscoverResponse>>(emptyList()) }
    var showFeedPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val feedsViewModel: FeedsViewModel = koinViewModel()
    val feedsUiState by feedsViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Add Feed") })
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = feedUrl,
                    onValueChange = { feedUrl = it },
                    label = { Text("Feed URL or Site URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                if (error != null) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error)
                }
                if (showFeedPicker && discoveredFeeds.isNotEmpty()) {
                    Text("Select a feed to add:")
                    discoveredFeeds.forEach { discovered ->
                        Button(
                            onClick = {
                                isLoading = true
                                error = null
                                scope.launch {
                                    try {
                                        apiService.createFeed(
                                            request = CreateFeedRequest(feed_url = discovered.url)
                                        )
                                        isLoading = false
                                        onFeedAdded()
                                        navController.popBackStack()
                                    } catch (e: Exception) {
                                        error = e.message
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(discovered.title ?: discovered.url)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            isLoading = true
                            error = null
                            scope.launch {
                                val alreadyExists = feedsUiState.feeds.any { it.feedUrl == feedUrl || it.siteUrl == feedUrl }
                                if (alreadyExists) {
                                    error = "Feed already exists."
                                    isLoading = false
                                    return@launch
                                }
                                try {
                                    // Try direct add first
                                    apiService.createFeed(
                                        request = CreateFeedRequest(feed_url = feedUrl)
                                    )
                                    isLoading = false
                                    onFeedAdded()
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    // If failed, try discover
                                    try {
                                        val discovered = apiService.discoverFeeds(
                                            request = DiscoverRequest(url = feedUrl)
                                        )
                                        if (discovered.isNotEmpty()) {
                                            discoveredFeeds = discovered
                                            showFeedPicker = true
                                        } else {
                                            error = "No feeds discovered at this URL."
                                        }
                                    } catch (e2: Exception) {
                                        error = "Failed to discover feeds: ${e2.message}"
                                    }
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading && feedUrl.isNotBlank(),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Text("Add Feed")
                        }
                    }
                }
            }
        }
    }
}
