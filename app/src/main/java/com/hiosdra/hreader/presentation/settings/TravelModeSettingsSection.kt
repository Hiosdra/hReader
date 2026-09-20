package com.hiosdra.hreader.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.travel.estimateTravelMode
import com.hiosdra.hreader.presentation.travel.formatTravelModeSize

private val TRAVEL_PRESETS = listOf(
    0 to R.string.travel_mode_preset_unread,
    200 to R.string.travel_mode_preset_200,
    500 to R.string.travel_mode_preset_500,
    1000 to R.string.travel_mode_preset_1000
)

@OptIn(ExperimentalLayoutApi::class)
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.travel_mode_intro),
            style = MaterialTheme.typography.bodyLarge
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.travel_mode_presets),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.travel_mode_presets_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    TRAVEL_PRESETS.forEach { (target, label) ->
                        FilterChip(
                            selected = offline.backlogTarget == target,
                            onClick = { actions.onBacklogTargetChange(target) },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.travel_mode_unavailable_selection),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.travel_mode_estimate_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        R.string.travel_mode_estimate_network,
                        formatTravelModeSize(estimate.networkBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = stringResource(
                        R.string.travel_mode_estimate_storage,
                        formatTravelModeSize(estimate.storageBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(
                        R.string.travel_mode_estimate_full_pages,
                        formatTravelModeSize(fullPageEstimate.networkBytes),
                        formatTravelModeSize(fullPageEstimate.storageBytes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = stringResource(R.string.travel_mode_estimate_basis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.travel_mode_constraints_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        if (sync.unmeteredOnly) R.string.sync_wifi_only else R.string.travel_mode_network_any
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = stringResource(
                        if (sync.syncWhileRoaming) {
                            R.string.travel_mode_roaming_allowed
                        } else {
                            R.string.travel_mode_roaming_blocked
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(
                        if (sync.quietHoursEnabled) {
                            R.string.travel_mode_quiet_hours_enabled
                        } else {
                            R.string.travel_mode_quiet_hours_disabled
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.travel_mode_constraint_power),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (!isOnline) {
                    Text(
                        text = stringResource(R.string.travel_mode_offline_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
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
                modifier = Modifier.padding(16.dp)
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.travel_mode_snapshot_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.travel_mode_snapshot_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
