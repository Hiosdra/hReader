package com.hiosdra.hreader.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.OfflinePreparationStage
import com.hiosdra.hreader.presentation.theme.HReaderSpacing

internal data class EmptyStateModel(
    val error: String? = null,
    val isSyncing: Boolean = false,
    val onRetry: () -> Unit
)

@Composable
internal fun OfflinePreparationProgressBanner(progress: OfflinePreparationProgress) {
    val count = progress.total.takeIf { it > 0 }?.let {
        stringResource(R.string.offline_progress_count, progress.done, it)
    }.orEmpty()
    val label = when (progress.stage) {
        OfflinePreparationStage.SYNCING -> stringResource(R.string.offline_syncing_articles)
        OfflinePreparationStage.DOWNLOADING_CONTENT ->
            stringResource(R.string.offline_downloading_content, count)
        OfflinePreparationStage.ARCHIVING_PAGES ->
            stringResource(R.string.offline_saving_pages, count)
        OfflinePreparationStage.IDLE -> if (progress.isFullOffline) {
            stringResource(R.string.offline_downloading_full_pages)
        } else {
            stringResource(R.string.offline_downloading)
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(8.dp))
            if (progress.total > 0) {
                LinearProgressIndicator(
                    progress = { (progress.done.toFloat() / progress.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmptyState(
    modifier: Modifier,
    model: EmptyStateModel,
    hasSearchQuery: Boolean,
    showReadArticles: Boolean,
    readCount: Int,
    feedId: Long?,
    onClearSearch: () -> Unit,
    onBrowseFeeds: () -> Unit,
    onAddFeed: () -> Unit,
    onBack: () -> Unit,
    onShowAllArticles: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            if (model.isSyncing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.notification_sync_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when {
                model.error != null -> {
                    Text(
                        text = model.error,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = model.onRetry, modifier = Modifier.padding(top = 20.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                hasSearchQuery -> {
                    Text(
                        text = stringResource(R.string.main_nothing_matches_search),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    Button(onClick = onClearSearch, modifier = Modifier.padding(top = 20.dp)) {
                        Text(stringResource(R.string.main_clear_search))
                    }
                }
                !showReadArticles && readCount > 0 -> {
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = stringResource(R.string.main_all_caught_up),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.main_all_caught_up_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Button(onClick = onShowAllArticles, modifier = Modifier.padding(top = 20.dp)) {
                        Text(stringResource(R.string.main_show_all_from_empty))
                    }
                    TextButton(onClick = model.onRetry) {
                        Text(stringResource(R.string.main_refresh_now))
                    }
                }
                else -> {
                    Text(
                        text = stringResource(
                            if (feedId == null) R.string.main_no_articles else R.string.main_no_feed_articles
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { if (feedId == null) onBrowseFeeds() else onBack() },
                        modifier = Modifier.padding(top = 20.dp)
                    ) {
                        Text(
                            stringResource(
                                if (feedId == null) R.string.main_browse_subscriptions
                                else R.string.main_back_to_all_articles
                            )
                        )
                    }
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = onAddFeed) {
                            Text(stringResource(R.string.main_add_subscription))
                        }
                        TextButton(onClick = model.onRetry) {
                            Text(stringResource(R.string.main_refresh_now))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InitialSyncState(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.main_syncing_first_time),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArticleScopeBar(
    feedId: Long?,
    feedTitle: String?,
    searchQuery: String,
    showReadArticles: Boolean,
    onShowReadArticles: (Boolean) -> Unit,
    onClearSearch: () -> Unit,
    onLeaveFeed: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        FlowRow(
            modifier = Modifier.padding(
                horizontal = HReaderSpacing.space4,
                vertical = HReaderSpacing.space1
            ),
            horizontalArrangement = Arrangement.spacedBy(HReaderSpacing.space2),
            verticalArrangement = Arrangement.spacedBy(HReaderSpacing.space1)
        ) {
            FilterChip(
                selected = !showReadArticles,
                onClick = { onShowReadArticles(false) },
                label = { Text(stringResource(R.string.main_scope_unread)) },
                leadingIcon = { Icon(Icons.Filled.Done, contentDescription = null) }
            )
            FilterChip(
                selected = showReadArticles,
                onClick = { onShowReadArticles(true) },
                label = { Text(stringResource(R.string.main_scope_all)) }
            )
            if (feedId != null) {
                AssistChip(
                    onClick = onLeaveFeed,
                    label = {
                        Text(
                            text = feedTitle ?: stringResource(R.string.main_scope_feed),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
            if (searchQuery.isNotBlank()) {
                AssistChip(
                    onClick = onClearSearch,
                    label = {
                        Text(
                            text = stringResource(R.string.main_scope_search, searchQuery),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
internal fun OfflineBanner(onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.main_offline_banner),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_dismiss)
                )
            }
        }
    }
}

@Composable
internal fun AiModelUnavailableBanner(
    modelId: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.main_ai_model_unavailable_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.main_ai_model_unavailable_message, modelId),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.action_show_settings))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
    }
}
