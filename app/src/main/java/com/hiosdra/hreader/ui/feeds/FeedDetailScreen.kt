package com.hiosdra.hreader.ui.feeds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedDetailScreen(feedId: Long, viewModel: FeedsViewModel = koinViewModel(), navController: NavController) {
    val uiState by viewModel.uiState.collectAsState()
    val feed = uiState.feeds.find { it.id == feedId }

    Surface {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            TopAppBar(
                title = { Text(feed?.title ?: "Feed Detail", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (feed != null) {
                Text(text = "Site URL: ${feed.siteUrl ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Feed URL: ${feed.feedUrl}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Category: ${feed.category.title}", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(text = "Feed not found.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
