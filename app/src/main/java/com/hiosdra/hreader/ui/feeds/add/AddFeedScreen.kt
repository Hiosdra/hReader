package com.hiosdra.hreader.ui.feeds.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedScreen(
    navController: NavController,
    /** A URL shared into the app from elsewhere, so the field is already filled in. */
    initialUrl: String? = null,
    onFeedAdded: () -> Unit = {}
) {
    val addFeedViewModel: AddFeedViewModel = koinViewModel()
    val uiState by addFeedViewModel.uiState.collectAsState()

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) addFeedViewModel.onFeedUrlChange(initialUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Feed", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
    ) { paddingValues ->
        val keyboardController = LocalSoftwareKeyboardController.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp)
            ) {
                OutlinedTextField(
                    value = uiState.feedUrl,
                    onValueChange = { addFeedViewModel.onFeedUrlChange(it) },
                    label = { Text("Feed URL or Site URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                        if (uiState.canSubmit && !uiState.isLoading) {
                            addFeedViewModel.onAddFeed(onFeedAdded)
                        }
                    }),
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
                                    onFeedAdded = onFeedAdded
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
                                onFeedAdded = onFeedAdded
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
