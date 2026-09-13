package com.hiosdra.hreader.presentation.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.storage.StorageCategory
import com.hiosdra.hreader.core.application.storage.StorageCategoryUsage
import com.hiosdra.hreader.core.application.storage.StorageCleanupAction
import com.hiosdra.hreader.core.application.storage.StorageSnapshot
import com.hiosdra.hreader.presentation.text.resolve
import com.hiosdra.hreader.presentation.theme.sectionCardColors

@Composable
internal fun StorageSettingsSection(
    state: StorageUiState,
    onRefresh: () -> Unit,
    onCleanup: (StorageCleanupAction) -> Unit
) {
    var pendingAction by remember { mutableStateOf<StorageCleanupAction?>(null) }
    val context = LocalContext.current
    val snapshot = state.snapshot

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = sectionCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.storage_title),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.storage_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (snapshot == null && state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            snapshot?.let { current ->
                Text(
                    text = stringResource(R.string.storage_app_usage, formatBytes(context, current.appBytes)),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(
                        R.string.storage_available,
                        formatBytes(context, current.availableDeviceBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current.isLowStorage) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (current.isLowStorage) {
                    Text(
                        text = stringResource(R.string.storage_low_space),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    current.largestRemovableCategory?.let { largest ->
                        cleanupActionFor(largest.category)?.let { action ->
                            Button(
                                onClick = { pendingAction = action },
                                enabled = state.cleanupAction == null && !state.isLoading && largest.bytes > 0L
                            ) {
                                Text(
                                    stringResource(
                                        R.string.storage_free_category,
                                        stringResource(largest.category.labelRes)
                                    )
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                current.categories.forEach { usage ->
                    StorageUsageRow(
                        usage = usage,
                        snapshot = current,
                        enabled = state.cleanupAction == null && !state.isLoading,
                        onCleanup = { action -> pendingAction = action },
                        context = context
                    )
                }
            }
            if (state.isLoading && snapshot != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.cleanupAction?.let { action ->
                val progress = state.cleanupProgress
                if (progress?.fraction == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { progress.fraction ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = progress?.let {
                        stringResource(
                            R.string.storage_cleanup_progress,
                            stringResource(action.category.labelRes),
                            it.completedItems,
                            it.totalItems
                        )
                    } ?: stringResource(R.string.storage_cleanup_in_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.error?.let { error ->
                Text(
                    text = error.resolve(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            state.cleanupResult?.let { result ->
                val message = if (result.isPartial) {
                    pluralStringResource(
                        R.plurals.storage_cleanup_partial,
                        result.failedItems,
                        formatBytes(context, result.reclaimedBytes),
                        result.failedItems
                    )
                } else {
                    stringResource(
                        R.string.storage_cleanup_reclaimed,
                        formatBytes(context, result.reclaimedBytes)
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.isPartial) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Text(
                text = stringResource(R.string.storage_protected_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onRefresh,
                enabled = state.cleanupAction == null && !state.isLoading,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    stringResource(
                        if (state.isLoading) R.string.storage_refreshing else R.string.storage_refresh
                    )
                )
            }
        }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = {
                Text(
                    stringResource(
                        R.string.storage_cleanup_confirm_title,
                        stringResource(action.category.labelRes)
                    )
                )
            },
            text = { Text(stringResource(action.messageRes)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction = null
                        onCleanup(action)
                    }
                ) {
                    Text(stringResource(R.string.storage_cleanup_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun StorageUsageRow(
    usage: StorageCategoryUsage,
    snapshot: StorageSnapshot,
    enabled: Boolean,
    onCleanup: (StorageCleanupAction) -> Unit,
    context: android.content.Context
) {
    val action = cleanupActionFor(usage.category)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(usage.category.labelRes),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = usageDetails(usage, snapshot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (usage.category.isProtected) {
                    stringResource(R.string.storage_protected)
                } else {
                    stringResource(R.string.storage_can_remove)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (usage.category.isProtected) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatBytes(context, usage.bytes),
                style = MaterialTheme.typography.bodyMedium
            )
            action?.let {
                TextButton(
                    onClick = { onCleanup(it) },
                    enabled = enabled && usage.bytes > 0L
                ) {
                    Text(stringResource(it.actionLabelRes))
                }
            }
        }
    }
}

@Composable
private fun usageDetails(
    usage: StorageCategoryUsage,
    snapshot: StorageSnapshot
): String = if (usage.category == StorageCategory.ARTICLE_DATA) {
    stringResource(
        R.string.storage_article_data_details,
        snapshot.articleCount,
        snapshot.feedCount,
        snapshot.storedContentCount,
        snapshot.readingPositionCount
    )
} else {
    pluralStringResource(R.plurals.storage_items, usage.itemCount, usage.itemCount)
}

private fun formatBytes(context: android.content.Context, bytes: Long): String =
    Formatter.formatFileSize(context, bytes.coerceAtLeast(0L))

private fun cleanupActionFor(category: StorageCategory): StorageCleanupAction? =
    StorageCleanupAction.entries.firstOrNull { it.category == category }

private val StorageCategory.labelRes: Int
    get() = when (this) {
        StorageCategory.ARTICLE_DATA -> R.string.storage_category_article_data
        StorageCategory.OFFLINE_PAGES -> R.string.storage_category_pages
        StorageCategory.DOWNLOADED_IMAGES -> R.string.storage_category_images
        StorageCategory.TTS_MODELS -> R.string.storage_category_tts
        StorageCategory.ON_DEVICE_AI_MODEL -> R.string.storage_category_ai
        StorageCategory.TEMPORARY_CACHE -> R.string.storage_category_cache
        StorageCategory.OTHER -> R.string.storage_category_other
    }

private val StorageCleanupAction.actionLabelRes: Int
    get() = when (this) {
        StorageCleanupAction.OFFLINE_PAGES -> R.string.storage_remove_pages
        StorageCleanupAction.DOWNLOADED_IMAGES -> R.string.storage_remove_images
        StorageCleanupAction.TTS_MODELS -> R.string.storage_remove_tts
        StorageCleanupAction.ON_DEVICE_AI_MODEL -> R.string.storage_remove_ai
        StorageCleanupAction.TEMPORARY_CACHE -> R.string.storage_remove_cache
    }

private val StorageCleanupAction.messageRes: Int
    get() = when (this) {
        StorageCleanupAction.OFFLINE_PAGES -> R.string.storage_cleanup_pages_message
        StorageCleanupAction.DOWNLOADED_IMAGES -> R.string.storage_cleanup_images_message
        StorageCleanupAction.TTS_MODELS -> R.string.storage_cleanup_tts_message
        StorageCleanupAction.ON_DEVICE_AI_MODEL -> R.string.storage_cleanup_ai_message
        StorageCleanupAction.TEMPORARY_CACHE -> R.string.storage_cleanup_cache_message
    }
