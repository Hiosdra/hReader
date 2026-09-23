package com.hiosdra.hreader.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.travel.estimateTravelMode
import com.hiosdra.hreader.presentation.travel.formatTravelModeSize

@Composable
internal fun TravelModeSettingsSection(
    offline: OfflineUiState,
    sync: SyncUiState,
    isOnline: Boolean,
    actions: OfflineSettingsActions,
    requestNotificationPermission: ((() -> Unit) -> Unit)
) {
    val estimate = estimateTravelMode(
        readiness = offline.readiness,
        includeImages = offline.imageDownloadEnabled,
        includeFullPages = false
    )
    val fullPageEstimate = estimateTravelMode(
        readiness = offline.readiness,
        includeImages = offline.imageDownloadEnabled,
        includeFullPages = true
    )
    val estimateSummary = "${stringResource(R.string.travel_mode_estimate_network, formatTravelModeSize(estimate.networkBytes))} · " +
        stringResource(R.string.travel_mode_estimate_storage, formatTravelModeSize(estimate.storageBytes))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.travel_mode_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            OfflineReadinessSection(
                state = offline,
                onPrepare = {
                    requestNotificationPermission { actions.onTravelModePrepare(false) }
                },
                onFullOfflineSync = {
                    requestNotificationPermission { actions.onTravelModePrepare(true) }
                },
                onBacklogTargetChange = actions.onBacklogTargetChange,
                onImageDownloadEnabledChange = actions.onImageDownloadEnabledChange,
                onImageCacheBudgetChange = actions.onImageCacheBudgetChange,
                canPrepare = isOnline,
                onRetry = if (isOnline) {
                    {
                        requestNotificationPermission {
                            actions.onTravelModePrepare(offline.isFullOfflinePreparation)
                        }
                    }
                } else {
                    null
                },
                estimateSummary = estimateSummary,
                fullPagesEstimateSummary = stringResource(
                    R.string.travel_mode_estimate_full_pages,
                    formatTravelModeSize(fullPageEstimate.networkBytes),
                    formatTravelModeSize(fullPageEstimate.storageBytes)
                ),
                showDownloadOptions = false,
                modifier = Modifier.padding(16.dp)
            )
        }
        val targetSummary = if (offline.backlogTarget == 0) {
            stringResource(R.string.offline_unread_only)
        } else {
            "${stringResource(R.string.offline_articles_to_keep)} · ${offline.backlogTarget}"
        }
        val imageSummary = "${stringResource(R.string.offline_download_images)}: " +
            stringResource(
                if (offline.imageDownloadEnabled) R.string.settings_state_enabled
                else R.string.settings_state_disabled
            )
        val imageBudgetSummary = offline.imageCacheBudgetMegabytes
            .takeIf { offline.imageDownloadEnabled && it > 0 }
            ?.let { stringResource(R.string.offline_image_budget, it) }
            ?.let { " · $it" }
            .orEmpty()
        SettingsGroup(
            title = stringResource(R.string.travel_mode_download_options),
            summary = "$targetSummary · $imageSummary$imageBudgetSummary"
        ) {
            OfflineDownloadOptionsSection(
                state = offline,
                onBacklogTargetChange = actions.onBacklogTargetChange,
                onImageDownloadEnabledChange = actions.onImageDownloadEnabledChange,
                onImageCacheBudgetChange = actions.onImageCacheBudgetChange
            )
        }
        SettingsGroup(
            title = stringResource(R.string.travel_mode_download_details),
            summary = "${stringResource(R.string.travel_mode_estimate_title)} · " +
                stringResource(R.string.travel_mode_constraints_title)
        ) {
            TravelModeDetails(
                sync = sync,
                estimate = estimate
            )
        }
    }
}

@Composable
private fun TravelModeDetails(
    sync: SyncUiState,
    estimate: com.hiosdra.hreader.presentation.travel.TravelModeEstimate
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.travel_mode_estimate_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(stringResource(R.string.travel_mode_estimate_network, formatTravelModeSize(estimate.networkBytes)))
        Text(stringResource(R.string.travel_mode_estimate_storage, formatTravelModeSize(estimate.storageBytes)))
        Text(
            text = stringResource(R.string.travel_mode_estimate_basis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.travel_mode_constraints_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            stringResource(if (sync.unmeteredOnly) R.string.sync_wifi_only else R.string.travel_mode_network_any)
        )
        Text(
            stringResource(
                if (sync.syncWhileRoaming) R.string.travel_mode_roaming_allowed
                else R.string.travel_mode_roaming_blocked
            )
        )
        Text(
            stringResource(
                if (sync.quietHoursEnabled) R.string.travel_mode_quiet_hours_enabled
                else R.string.travel_mode_quiet_hours_disabled
            )
        )
        Text(
            text = stringResource(R.string.travel_mode_constraint_power),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.travel_mode_snapshot_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = stringResource(R.string.travel_mode_snapshot_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
