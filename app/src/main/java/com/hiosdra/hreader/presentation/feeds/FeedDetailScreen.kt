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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.hiosdra.hreader.presentation.navigation.openChromeCustomTab
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.domain.service.cleanUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedDetailScreen(feedId: Long, navController: NavController, viewModel: FeedsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val feed = uiState.feeds.find { it.id == feedId }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.article_copied)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                FeedAddress(
                    label = stringResource(R.string.feeds_site_url),
                    value = feed.siteUrl,
                    onCopy = {
                        copyUrl(context, feed.siteUrl.orEmpty())
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = copiedMessage,
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                FeedAddress(
                    label = stringResource(R.string.feeds_feed_url),
                    value = feed.feedUrl,
                    onCopy = {
                        copyUrl(context, feed.feedUrl.orEmpty())
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = copiedMessage,
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
                AiOverviewPreloadSetting(
                    checked = feed.preloadAiOverview,
                    enabled = !uiState.isBusy,
                    onCheckedChange = { enabled ->
                        viewModel.setAiOverviewPreloading(feed.id, enabled)
                    }
                )
            } else {
                Text(text = stringResource(R.string.feeds_not_found), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AiOverviewPreloadSetting(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.feeds_preload_ai_overview),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.feeds_preload_ai_overview_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null
        )
    }
}

@Composable
private fun FeedAddress(label: String, value: String?, onCopy: () -> Unit) {
    val displayedValue = value ?: stringResource(R.string.label_not_available)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.feeds_label_value, label, displayedValue),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!value.isNullOrBlank()) {
            TextButton(onClick = onCopy) {
                Text(stringResource(R.string.action_copy_url))
            }
            val context = LocalContext.current
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
