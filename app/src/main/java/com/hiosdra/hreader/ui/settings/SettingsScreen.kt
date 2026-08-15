package com.hiosdra.hreader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.paywall.PaywallBypassMethod
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.tts.TtsModelManager
import com.hiosdra.hreader.ui.components.ErrorReportingPreferenceCard
import com.hiosdra.hreader.ui.components.rememberNotificationPermissionRequest
import com.hiosdra.hreader.ui.theme.sectionCardColors
import com.hiosdra.hreader.util.ErrorReportingManager
import com.hiosdra.hreader.util.SyncPerformanceOperation
import com.hiosdra.hreader.util.SyncPerformanceRecord
import com.hiosdra.hreader.worker.TtsModelDownloadScheduler
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController? = null,
    onSignedOut: () -> Unit = {},
    preferencesManager: PreferencesManager = koinInject(),
    errorReportingManager: ErrorReportingManager = koinInject(),
    ttsModelManager: TtsModelManager = koinInject(),
    ttsModelDownloadScheduler: TtsModelDownloadScheduler = koinInject(),
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val serverSettings by settingsViewModel.uiState.collectAsState()
    val openRouterApiKey by settingsViewModel.openRouterApiKey.collectAsState()
    val aiModels by settingsViewModel.aiModels.collectAsState()
    val offline by settingsViewModel.offline.collectAsState()
    val sync by settingsViewModel.sync.collectAsState()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    var selectedBypassMethod by remember { mutableStateOf(preferencesManager.getPaywallBypassMethod()) }
    var bionicReadingEnabled by remember { mutableStateOf(preferencesManager.getBionicReadingEnabled()) }
    var credibilityScoreEnabled by remember { mutableStateOf(preferencesManager.getCredibilityScoreEnabled()) }
    var sentryReportingEnabled by remember { mutableStateOf(errorReportingManager.isEnabled()) }
    var showPerformanceDialog by remember { mutableStateOf(false) }
    var showBypassDialog by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    val onToggleBionicReading: (Boolean) -> Unit = { enabled ->
        bionicReadingEnabled = enabled
        preferencesManager.setBionicReadingEnabled(enabled)
    }
    val onToggleCredibilityScore: (Boolean) -> Unit = { enabled ->
        credibilityScoreEnabled = enabled
        preferencesManager.setCredibilityScoreEnabled(enabled)
    }
    val onToggleSentryReporting: (Boolean) -> Unit = { enabled ->
        sentryReportingEnabled = enabled
        errorReportingManager.setEnabled(enabled)
    }

    LaunchedEffect(serverSettings.signOutCompleted) {
        if (serverSettings.signOutCompleted) onSignedOut()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onSurface
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
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // FreshRSS Server Card
            item {
                Text(
                    text = stringResource(R.string.settings_rss_server),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = sectionCardColors()
                ) {
                    BackendServerFields(
                        state = serverSettings,
                        onBackendTypeChange = { backendType ->
                            requestNotificationPermission {
                                settingsViewModel.onBackendTypeRequested(backendType)
                            }
                        },
                        onServerUrlChange = settingsViewModel::onServerUrlChange,
                        onUsernameChange = settingsViewModel::onUsernameChange,
                        onSecretChange = settingsViewModel::onSecretChange,
                        onTestConnection = settingsViewModel::testConnection,
                        modifier = Modifier.padding(16.dp),
                        onSignOut = { showSignOutDialog = true }
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.settings_sync),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = sectionCardColors()
                ) {
                    SyncSection(
                        state = sync,
                        onIntervalChange = settingsViewModel::onSyncIntervalChange,
                        onUnmeteredOnlyChange = settingsViewModel::onUnmeteredOnlyChange,
                        onSyncWhileRoamingChange = settingsViewModel::onSyncWhileRoamingChange,
                        onQuietHoursEnabledChange = settingsViewModel::onQuietHoursEnabledChange,
                        onQuietHoursChange = settingsViewModel::onQuietHoursChange,
                        onResyncFromScratch = {
                            requestNotificationPermission(settingsViewModel::resyncFromScratch)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.settings_offline_reading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = sectionCardColors()
                ) {
                    OfflineReadinessSection(
                        state = offline,
                        onPrepare = {
                            requestNotificationPermission(settingsViewModel::prepareForOffline)
                        },
                        onFullOfflineSync = {
                            requestNotificationPermission(settingsViewModel::prepareFullOffline)
                        },
                        onBacklogTargetChange = settingsViewModel::onBacklogTargetChange,
                        onImageDownloadEnabledChange = settingsViewModel::onImageDownloadEnabledChange,
                        onImageCacheBudgetChange = settingsViewModel::onImageCacheBudgetChange,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Reading Experience Card
            item {
                Text(
                    text = stringResource(R.string.settings_reading_experience),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = sectionCardColors()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleBionicReading(!bionicReadingEnabled) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_bionic_reading),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_bionic_reading_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = bionicReadingEnabled,
                                onCheckedChange = onToggleBionicReading
                            )
                        }
                    }
                }
            }

            item {
                TtsSettingsSection(
                    preferences = preferencesManager,
                    modelManager = ttsModelManager,
                    downloadScheduler = ttsModelDownloadScheduler,
                    onRequestNotifications = requestNotificationPermission
                )
            }

            item {
                Text(
                    text = stringResource(R.string.settings_ai_credibility),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = sectionCardColors()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleCredibilityScore(!credibilityScoreEnabled) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_show_credibility_chip),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.settings_credibility_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = credibilityScoreEnabled,
                                onCheckedChange = onToggleCredibilityScore
                            )
                        }
                        if (credibilityScoreEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = stringResource(R.string.settings_rating_meaning),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_rating_values),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.settings_credibility_disclaimer),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Paywall Bypass Method Section
            item {
                Text(
                    text = stringResource(R.string.settings_paywall_service),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = sectionCardColors()
                ) {
                    SettingRow(
                        title = stringResource(R.string.settings_bypass_service),
                        value = stringResource(selectedBypassMethod.displayNameRes),
                        supportingText = selectedBypassMethod.host,
                        onClick = { showBypassDialog = true }
                    )
                }
            }

            // OpenRouter Key Section
            item {
                Text(
                    text = stringResource(R.string.settings_ai_provider),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = sectionCardColors()
                ) {
                    OpenRouterKeyField(
                        apiKey = openRouterApiKey,
                        onApiKeyChange = settingsViewModel::onOpenRouterApiKeyChange,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // AI Model Selection Section
            item {
                Text(
                    text = stringResource(R.string.settings_ai_model_summaries),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = sectionCardColors()
                ) {
                    SettingRow(
                        title = stringResource(R.string.settings_model),
                        value = aiModels.selectedModelName,
                        supportingText = aiModels.selectedModelId.takeIf { it != aiModels.selectedModelName },
                        onClick = { showModelSheet = true }
                    )
                }
                if (aiModels.selectedModelIsMissing) {
                    Text(
                        text = stringResource(R.string.settings_model_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.settings_privacy_diagnostics),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ErrorReportingPreferenceCard(
                    enabled = sentryReportingEnabled,
                    onEnabledChange = onToggleSentryReporting,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Performance Section
            item {
                Text(
                    text = stringResource(R.string.settings_performance),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = sectionCardColors()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { showPerformanceDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_show_performance))
                        }
                    }
                }
            }
        }
        serverSettings.pendingBackendType?.let { target ->
            BackendSwitchDialog(
                currentBackend = serverSettings.backendType,
                targetBackend = target,
                onConfirm = settingsViewModel::confirmBackendSwitch,
                onDismiss = settingsViewModel::cancelBackendSwitch
            )
        }
        if (showBypassDialog) {
            PaywallBypassDialog(
                selected = selectedBypassMethod,
                onSelect = { method ->
                    selectedBypassMethod = method
                    preferencesManager.setPaywallBypassMethod(method)
                    showBypassDialog = false
                },
                onDismiss = { showBypassDialog = false }
            )
        }
        if (showModelSheet) {
            AiModelSheet(
                state = aiModels,
                onSearchQueryChange = settingsViewModel::onModelSearchQueryChange,
                onFreeOnlyChange = settingsViewModel::onFreeOnlyChange,
                onReload = { settingsViewModel.loadAiModels(forceRefresh = true) },
                onModelSelected = { model ->
                    settingsViewModel.onModelSelected(model)
                    showModelSheet = false
                },
                onDismiss = { showModelSheet = false }
            )
        }
        if (showPerformanceDialog) {
            PerformanceInfoDialog(
                performanceRecords = preferencesManager.getSyncPerformanceRecords(),
                onDismiss = { showPerformanceDialog = false },
                onClearRecords = { preferencesManager.clearSyncPerformanceRecords() }
            )
        }
        if (showSignOutDialog) {
            AlertDialog(
                onDismissRequest = { showSignOutDialog = false },
                title = { Text(stringResource(R.string.settings_sign_out_title)) },
                text = { Text(stringResource(R.string.settings_sign_out_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSignOutDialog = false
                            settingsViewModel.signOut()
                        },
                        enabled = !serverSettings.isSwitchingBackend
                    ) { Text(stringResource(R.string.action_sign_out)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }
    }
}

@Composable
private fun PerformanceInfoDialog(
    performanceRecords: List<SyncPerformanceRecord>,
    onDismiss: () -> Unit,
    onClearRecords: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.settings_sync_performance),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn {
                if (performanceRecords.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.settings_no_performance),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    items(performanceRecords) { record ->
                        PerformanceRecordItem(record)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (performanceRecords.isNotEmpty()) {
                    TextButton(onClick = onClearRecords) {
                        Text(stringResource(R.string.action_clear_all))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    )
}

@Composable
private fun PerformanceRecordItem(record: SyncPerformanceRecord) {
    val locale = LocalLocale.current.platformLocale
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(record.operationLabelRes()),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = record.durationLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = record.timestampLabel(locale),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Additional details based on what's available
        record.batchSize?.let { batchSize ->
            record.totalArticles?.let { totalArticles ->
                Text(
                    text = pluralStringResource(
                        R.plurals.settings_articles_batches,
                        totalArticles,
                        totalArticles,
                        batchSize
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        record.isIncremental?.let { isIncremental ->
            val syncInfo = record.lastSyncHoursAgo?.let { hours ->
                stringResource(
                    if (isIncremental) R.string.settings_incremental_sync else R.string.settings_full_sync,
                    hours
                )
            } ?: stringResource(
                if (isIncremental) R.string.settings_incremental_sync_plain else R.string.settings_full_sync_plain
            )

            Text(
                text = syncInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SyncPerformanceRecord.durationLabel(): String = when {
    durationMs < 1000 -> stringResource(R.string.settings_duration_milliseconds, durationMs)
    durationMs < 60000 -> stringResource(R.string.settings_duration_seconds, durationMs / 1000.0)
    else -> stringResource(R.string.settings_duration_minutes, durationMs / 60000.0)
}

private fun SyncPerformanceRecord.timestampLabel(locale: java.util.Locale): String =
    java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.MEDIUM,
        locale
    ).format(java.util.Date(timestamp))

private fun SyncPerformanceRecord.operationLabelRes(): Int = when (operationName) {
    SyncPerformanceOperation.ARTICLE_PAGES.key -> R.string.settings_operation_article_pages
    SyncPerformanceOperation.OFFLINE_BACKLOG_TOP_UP.key -> R.string.settings_operation_offline_backlog_top_up
    SyncPerformanceOperation.FULL_PAGE_PREFETCH.key -> R.string.settings_operation_full_page_prefetch
    SyncPerformanceOperation.ARTICLE_REFRESH.key -> R.string.settings_operation_article_refresh
    SyncPerformanceOperation.ORPHANED_CONTENT_CLEANUP.key -> R.string.settings_operation_orphaned_content_cleanup
    SyncPerformanceOperation.ARTICLE_CONTENT_PREFETCH.key -> R.string.settings_operation_article_content_prefetch
    SyncPerformanceOperation.ENCLOSURE_IMAGES_DOWNLOAD.key -> R.string.settings_operation_enclosure_images_download
    SyncPerformanceOperation.BATCH_PROCESSING.key -> R.string.settings_operation_batch_processing
    SyncPerformanceOperation.INCREMENTAL_SYNC.key -> R.string.settings_operation_incremental_sync
    SyncPerformanceOperation.FULL_SYNC.key -> R.string.settings_operation_full_sync
    else -> R.string.settings_operation_other
}
