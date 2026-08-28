package com.hiosdra.hreader.presentation.feeds

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.hiosdra.hreader.presentation.navigation.openChromeCustomTab
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.domain.service.cleanUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedDetailScreen(feedId: Long, navController: NavController, viewModel: FeedsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val feed = uiState.feeds.find { it.id == feedId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(feed?.title ?: stringResource(R.string.feeds_detail_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (feed != null) {
                FeedAddress(label = stringResource(R.string.feeds_site_url), value = feed.siteUrl)
                Spacer(modifier = Modifier.height(8.dp))
                FeedAddress(label = stringResource(R.string.feeds_feed_url), value = feed.feedUrl)
            } else {
                Text(text = stringResource(R.string.feeds_not_found), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FeedAddress(label: String, value: String?) {
    val displayedValue = value ?: stringResource(R.string.label_not_available)
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.feeds_label_value, label, displayedValue),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!value.isNullOrBlank()) {
            TextButton(onClick = { copyUrl(context, value) }) {
                Text(stringResource(R.string.action_copy_url))
            }
            TextButton(onClick = { openChromeCustomTab(context, cleanUrl(value)) }) {
                Text(stringResource(R.string.action_open_url))
            }
        }
    }
}

private fun copyUrl(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.action_copy_url), value))
}
