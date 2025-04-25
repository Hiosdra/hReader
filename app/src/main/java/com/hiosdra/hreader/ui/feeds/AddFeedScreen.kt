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
    onFeedAdded: () -> Unit = {}
) {
    val feedsViewModel: FeedsViewModel = koinViewModel()
    val feedsUiState by feedsViewModel.uiState.collectAsState()
    val addFeedViewModel: AddFeedViewModel = koinViewModel()
    val uiState by addFeedViewModel.uiState.collectAsState()

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
                    value = uiState.feedUrl,
                    onValueChange = { addFeedViewModel.onFeedUrlChange(it) },
                    label = { Text("Feed URL or Site URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                if (uiState.error != null) {
                    Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
                if (uiState.showFeedPicker && uiState.discoveredFeeds.isNotEmpty()) {
                    Text("Select a feed to add:")
                    uiState.discoveredFeeds.forEach { discovered ->
                        Button(
                            onClick = {
                                addFeedViewModel.onSelectDiscoveredFeed(
                                    discovered = discovered,
                                    onFeedAdded = onFeedAdded,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            },
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(discovered.title ?: discovered.url)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            addFeedViewModel.onAddFeed(
                                onFeedAdded = onFeedAdded,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        },
                        enabled = !uiState.isLoading && uiState.feedUrl.isNotBlank(),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (uiState.isLoading) {
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
