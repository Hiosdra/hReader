package com.hiosdra.hreader.presentation.travel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.presentation.components.rememberNotificationPermissionRequest
import com.hiosdra.hreader.presentation.navigation.Routes
import com.hiosdra.hreader.presentation.settings.OfflineReadinessSection
import com.hiosdra.hreader.presentation.settings.SettingsViewModel

private val TRAVEL_PRESETS = listOf(
    0 to R.string.travel_mode_preset_unread,
    200 to R.string.travel_mode_preset_200,
    500 to R.string.travel_mode_preset_500,
    1000 to R.string.travel_mode_preset_1000
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TravelModeScreen(
    navController: NavController,
    networkStatus: NetworkStatus,
    settingsViewModel: SettingsViewModel
) {
    val offline by settingsViewModel.offline.collectAsStateWithLifecycle()
    val sync by settingsViewModel.sync.collectAsStateWithLifecycle()
    val isOnline by networkStatus.isOnline.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.travel_mode_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.travel_mode_intro),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            item {
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
                                    onClick = { settingsViewModel.onBacklogTargetChange(target) },
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
            }
            item {
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
            }
            item {
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
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    OfflineReadinessSection(
                        state = offline,
                        onPrepare = {
                            requestNotificationPermission {
                                settingsViewModel.prepareTravelMode(fullOffline = false)
                            }
                        },
                        onFullOfflineSync = {
                            requestNotificationPermission {
                                settingsViewModel.prepareTravelMode(fullOffline = true)
                            }
                        },
                        onBacklogTargetChange = settingsViewModel::onBacklogTargetChange,
                        onImageDownloadEnabledChange = settingsViewModel::onImageDownloadEnabledChange,
                        onImageCacheBudgetChange = settingsViewModel::onImageCacheBudgetChange,
                        canPrepare = isOnline,
                        onRetry = if (isOnline) {
                            {
                                requestNotificationPermission {
                                    settingsViewModel.prepareTravelMode(offline.isFullOfflinePreparation)
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            item {
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
            item {
                TextButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                    Text(stringResource(R.string.travel_mode_advanced))
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}
