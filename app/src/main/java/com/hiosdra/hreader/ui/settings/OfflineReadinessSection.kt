package com.hiosdra.hreader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.model.OfflineReadiness
import com.hiosdra.hreader.worker.OfflinePreparationStage
import com.hiosdra.hreader.worker.SyncOperationState
import java.time.Duration
import java.time.Instant

private val BACKLOG_TARGETS = listOf(0, 200, 500, 1000)
private val CACHE_BUDGETS_MB = listOf(200, 500, 1000)
private const val BYTES_PER_MEGABYTE = 1024.0 * 1024

/**
 * The one screen that answers "can I leave now?". Everything on it is a count of what is already on
 * the device, because that is what survives losing signal — not what the server holds.
 */
@Composable
fun OfflineReadinessSection(
    state: OfflineUiState,
    onPrepare: () -> Unit,
    onFullOfflineSync: () -> Unit,
    onBacklogTargetChange: (Int) -> Unit,
    onImageDownloadEnabledChange: (Boolean) -> Unit,
    onImageCacheBudgetChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val readiness = state.readiness
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = readiness.headline(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { readiness.contentProgress() },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = readiness.detailLine(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.offline_last_sync, readiness.lastSyncLabel()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onPrepare,
            enabled = !state.isPreparing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isPreparing && !state.isFullOfflinePreparation) {
                PreparationProgressContent(state)
            } else {
                Text(stringResource(R.string.offline_download_reading))
            }
        }
        Button(
            onClick = onFullOfflineSync,
            enabled = !state.isPreparing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            if (state.isPreparing && state.isFullOfflinePreparation) {
                PreparationProgressContent(state)
            } else {
                Text(stringResource(R.string.offline_download_pages))
            }
        }
        // A real count rather than a spinner of unknown length: the reader is deciding whether
        // there is time to finish before leaving.
        if (state.isPreparing) {
            Spacer(modifier = Modifier.height(8.dp))
            val progress = state.preparationProgress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.offline_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        when (state.preparationStatus.state) {
            SyncOperationState.SUCCEEDED -> Text(
                text = stringResource(R.string.offline_download_complete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
            SyncOperationState.FAILED -> {
                val errorMessage = state.preparationStatus.errorMessageResId?.let { stringResource(it) }
                    ?: state.preparationStatus.errorMessage
                    ?: stringResource(R.string.sync_try_again)
                Text(
                    text = stringResource(R.string.offline_download_failed, errorMessage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            SyncOperationState.CANCELLED -> Text(
                text = stringResource(R.string.offline_download_cancelled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            SyncOperationState.IDLE,
            SyncOperationState.RUNNING -> Unit
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.offline_articles_to_keep),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.offline_articles_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BACKLOG_TARGETS.forEach { target ->
                FilterChip(
                    selected = state.backlogTarget == target,
                    onClick = { onBacklogTargetChange(target) },
                    label = { Text(if (target == 0) stringResource(R.string.offline_unread_only) else target.toString()) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onImageDownloadEnabledChange(!state.imageDownloadEnabled) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.offline_download_images),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.offline_images_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.imageDownloadEnabled,
                onCheckedChange = onImageDownloadEnabledChange
            )
        }

        if (state.imageDownloadEnabled) {
            Text(
                text = stringResource(R.string.offline_image_limit),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CACHE_BUDGETS_MB.forEach { budget ->
                    FilterChip(
                        selected = state.imageCacheBudgetMegabytes == budget,
                        onClick = { onImageCacheBudgetChange(budget) },
                        label = { Text(stringResource(R.string.offline_image_budget, budget)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineReadiness.headline(): String = when {
    offlineTargetCount == 0 -> stringResource(R.string.offline_nothing_available)
    isComplete -> pluralStringResource(R.plurals.offline_ready, offlineTargetCount, offlineTargetCount)
    else -> pluralStringResource(
        R.plurals.offline_available,
        offlineTargetCount,
        storedContentCount,
        offlineTargetCount
    )
}

private fun OfflineReadiness.contentProgress(): Float =
    if (offlineTargetCount == 0) 0f else (storedContentCount.toFloat() / offlineTargetCount).coerceIn(0f, 1f)

@Composable
private fun OfflineReadiness.detailLine(): String {
    val megabytes = storedImageBytes / BYTES_PER_MEGABYTE
    val backlog = if (backlogCount > 0) {
        pluralStringResource(R.plurals.offline_more_to_download, backlogCount, backlogCount)
    } else {
        ""
    }
    val images = if (expectedImageCount > 0) {
        pluralStringResource(
            R.plurals.offline_images_fraction,
            expectedImageCount,
            storedExpectedImageCount,
            expectedImageCount
        )
    } else {
        pluralStringResource(R.plurals.offline_images_count, storedImageCount, storedImageCount)
    }
    val fullPages = if (offlineTargetCount > 0) {
        pluralStringResource(
            R.plurals.offline_original_pages_fraction,
            offlineTargetCount,
            storedFullPageCount,
            offlineTargetCount
        )
    } else {
        pluralStringResource(R.plurals.offline_original_pages_count, 0, 0)
    }
    val unread = pluralStringResource(R.plurals.offline_unread_count, unreadCount, unreadCount)
    val articleBodies = pluralStringResource(
        R.plurals.offline_article_bodies,
        storedFullContentCount,
        storedFullContentCount
    )
    return stringResource(
        R.string.offline_article_summary,
        unread,
        backlog,
        articleBodies,
        fullPages,
        images,
        megabytes
    )
}

@Composable
private fun PreparationProgressContent(state: OfflineUiState) {
    CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onPrimary
    )
    Spacer(modifier = Modifier.size(8.dp))
    Text(state.preparationLabel())
}

@Composable
private fun OfflineUiState.preparationLabel(): String {
    val count = if (preparationTotal > 0) {
        stringResource(R.string.offline_progress_count, preparationDone, preparationTotal)
    } else {
        ""
    }
    return when (preparationStage) {
        OfflinePreparationStage.SYNCING -> stringResource(R.string.offline_syncing_articles)
        OfflinePreparationStage.DOWNLOADING_CONTENT -> stringResource(R.string.offline_downloading_content, count)
        OfflinePreparationStage.ARCHIVING_PAGES -> stringResource(R.string.offline_saving_pages, count)
        OfflinePreparationStage.IDLE -> if (isFullOfflinePreparation) {
            stringResource(R.string.offline_downloading_full_pages)
        } else {
            stringResource(R.string.offline_downloading)
        }
    }
}

@Composable
private fun OfflineReadiness.lastSyncLabel(): String {
    val syncedAt = lastSyncAt ?: return stringResource(R.string.offline_not_synced)
    val elapsed = Duration.between(syncedAt, Instant.now())
    return when {
        elapsed.isNegative -> stringResource(R.string.offline_just_now)
        elapsed.toMinutes() < 1 -> stringResource(R.string.offline_just_now)
        elapsed.toHours() < 1 -> stringResource(R.string.offline_minutes_ago, elapsed.toMinutes())
        elapsed.toDays() < 1 -> stringResource(R.string.offline_hours_ago, elapsed.toHours())
        else -> pluralStringResource(R.plurals.offline_days_ago, elapsed.toDays().toInt(), elapsed.toDays())
    }
}
