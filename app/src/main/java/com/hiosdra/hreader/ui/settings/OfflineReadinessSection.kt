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
import androidx.compose.ui.unit.dp
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
            text = "Last sync: ${readiness.lastSyncLabel()}",
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
                Text("Download for offline reading")
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
                Text("Download full pages")
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
            text = "Offline reading stores article bodies and images. Download full pages also saves the " +
                "original web pages, but only when you press that button.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        when (state.preparationStatus.state) {
            SyncOperationState.SUCCEEDED -> Text(
                text = "Offline download complete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
            SyncOperationState.FAILED -> Text(
                text = "Offline download failed: ${state.preparationStatus.errorMessage ?: "try again."}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
            SyncOperationState.CANCELLED -> Text(
                text = "Offline download cancelled.",
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
            text = "Articles to keep offline",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "How many articles to keep downloaded in total. When the unread ones run out, " +
                "recent articles you have already read fill the rest.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BACKLOG_TARGETS.forEach { target ->
                FilterChip(
                    selected = state.backlogTarget == target,
                    onClick = { onBacklogTargetChange(target) },
                    label = { Text(if (target == 0) "Unread only" else "$target") }
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
                    text = "Download images",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Off keeps the cache small when storage matters more than pictures",
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
                text = "Image storage limit",
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
                        label = { Text("$budget MB") }
                    )
                }
            }
        }
    }
}

private fun OfflineReadiness.headline(): String = when {
    offlineTargetCount == 0 -> "Nothing is available offline yet"
    isComplete -> "Ready for offline reading — all $offlineTargetCount articles available offline"
    else -> "$storedContentCount of $offlineTargetCount articles available offline"
}

private fun OfflineReadiness.contentProgress(): Float =
    if (offlineTargetCount == 0) 0f else (storedContentCount.toFloat() / offlineTargetCount).coerceIn(0f, 1f)

private fun OfflineReadiness.detailLine(): String {
    val megabytes = storedImageBytes / BYTES_PER_MEGABYTE
    val backlog = if (backlogCount > 0) " · $backlogCount more to download" else ""
    val images = if (expectedImageCount > 0) {
        "$storedExpectedImageCount/$expectedImageCount images"
    } else {
        "$storedImageCount images"
    }
    val fullPages = if (offlineTargetCount > 0) {
        "$storedFullPageCount/$offlineTargetCount original pages"
    } else {
        "0 original pages"
    }
    return "$unreadCount unread$backlog · $storedFullContentCount article bodies, " +
        "$fullPages, $images (%.0f MB)".format(megabytes)
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

private fun OfflineUiState.preparationLabel(): String {
    val count = if (preparationTotal > 0) {
        " $preparationDone of $preparationTotal"
    } else {
        ""
    }
    return when (preparationStage) {
        OfflinePreparationStage.SYNCING -> "Syncing articles…"
        OfflinePreparationStage.DOWNLOADING_CONTENT -> "Downloading article content$count…"
        OfflinePreparationStage.ARCHIVING_PAGES -> "Saving original pages$count…"
        OfflinePreparationStage.IDLE -> if (isFullOfflinePreparation) {
            "Downloading full pages…"
        } else {
            "Downloading for offline reading…"
        }
    }
}

private fun OfflineReadiness.lastSyncLabel(): String {
    val syncedAt = lastSyncAt ?: return "not synced yet"
    val elapsed = Duration.between(syncedAt, Instant.now())
    return when {
        elapsed.isNegative -> "just now"
        elapsed.toMinutes() < 1 -> "just now"
        elapsed.toHours() < 1 -> "${elapsed.toMinutes()} min ago"
        elapsed.toDays() < 1 -> "${elapsed.toHours()} h ago"
        else -> "${elapsed.toDays()} days ago"
    }
}
