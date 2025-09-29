package com.hiosdra.hreader.ui.feeds.add

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedScreen(
    navController: NavController,
    onFeedAdded: () -> Unit = {}
) {
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.feedUrl,
                    onValueChange = { addFeedViewModel.onFeedUrlChange(it) },
                    label = { Text("Feed URL or Site URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    isError = uiState.error != null
                )
                if (uiState.error != null) {
                    Text(text = uiState.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp))
                } else if (uiState.feedUrl.isNotBlank() && !uiState.canSubmit) {
                    Text(text = "Enter a valid URL like https://example.com or example.com/feed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp))
                }
                if (uiState.showFeedPicker && uiState.discoveredFeeds.isNotEmpty()) {
                    Text("Select a feed to add:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
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
                        enabled = uiState.canSubmit && !uiState.isLoading,
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
