package com.hiosdra.hreader.ui.feeds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.data.model.CreateFeedRequest
import com.hiosdra.hreader.data.model.DiscoverRequest
import com.hiosdra.hreader.data.model.DiscoverResponse
import com.hiosdra.hreader.data.remote.MinifluxApiService
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.androidx.compose.koinViewModel
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedScreen(
    navController: NavController,
    apiService: MinifluxApiService,
    onFeedAdded: () -> Unit = {}
) {
    var feedUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discoveredFeeds by remember { mutableStateOf<List<DiscoverResponse>>(emptyList()) }
    var showFeedPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val feedsViewModel: FeedsViewModel = koinViewModel()
    val feedsUiState by feedsViewModel.uiState.collectAsState()

    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun extractErrorMessage(e: Exception): String {
        if (e is HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            if (!errorBody.isNullOrEmpty()) {
                try {
                    val json = JSONObject(errorBody)
                    return json.optString("error_message", e.message ?: "Unknown error")
                } catch (_: Exception) {}
            }
        }
        return e.message ?: "Unknown error"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Feed") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
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
                                        val normalizedDiscoveredUrl = normalizeUrl(discovered.url)
                                        apiService.createFeed(
                                            request = CreateFeedRequest(feed_url = normalizedDiscoveredUrl)
                                        )
                                        isLoading = false
                                        onFeedAdded()
                                        navController.popBackStack()
                                    } catch (e: Exception) {
                                        error = extractErrorMessage(e)
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
                                val normalizedUrl = normalizeUrl(feedUrl)
                                try {
                                    // Try direct add first
                                    apiService.createFeed(
                                        request = CreateFeedRequest(feed_url = normalizedUrl)
                                    )
                                    isLoading = false
                                    onFeedAdded()
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    // If failed, try discover
                                    try {
                                        val discovered = apiService.discoverFeeds(
                                            request = DiscoverRequest(url = normalizedUrl)
                                        )
                                        if (discovered.isNotEmpty()) {
                                            discoveredFeeds = discovered
                                            showFeedPicker = true
                                        } else {
                                            error = "No feeds discovered at this URL."
                                        }
                                    } catch (e2: Exception) {
                                        error = "Failed to discover feeds: ${extractErrorMessage(e2)}"
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
