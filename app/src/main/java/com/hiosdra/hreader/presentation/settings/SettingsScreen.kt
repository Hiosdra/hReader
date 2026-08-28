package com.hiosdra.hreader.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.GemmaModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
import com.hiosdra.hreader.core.application.port.out.GemmaModelLifecycle
import com.hiosdra.hreader.presentation.components.rememberNotificationPermissionRequest
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.observability.SyncPerformanceRecord
import com.hiosdra.hreader.presentation.navigation.Routes
import com.hiosdra.hreader.presentation.theme.sectionCardColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController? = null,
    onSignedOut: () -> Unit = {},
    preferencesManager: AppPreferences,
    errorReportingManager: ErrorReporter,
    gemmaModelManager: GemmaModelGateway,
    gemmaModelDownloadScheduler: GemmaModelDownloadRequester,
    gemmaModelLifecycle: GemmaModelLifecycle,
    settingsViewModel: SettingsViewModel
) {
    val serverSettings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val openRouterApiKey by settingsViewModel.openRouterApiKey.collectAsStateWithLifecycle()
    val aiModels by settingsViewModel.aiModels.collectAsStateWithLifecycle()
    val offline by settingsViewModel.offline.collectAsStateWithLifecycle()
    val sync by settingsViewModel.sync.collectAsStateWithLifecycle()
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
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                        modifier = Modifier.padding(16.dp)
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

            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_reading_experience),
                    summary = stringResource(R.string.settings_group_reading_summary)
                ) {
                    ReadingSettingsSection(
                        bionicReadingEnabled = bionicReadingEnabled,
                        onBionicReadingChange = onToggleBionicReading,
                        selectedTtsModel = preferencesManager.getTtsModel(),
                        selectedBypassMethod = selectedBypassMethod,
                        onOpenTts = { navController?.navigate(Routes.TTS_SETTINGS) },
                        onOpenBypass = { showBypassDialog = true }
                    )
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_local_ai),
                    summary = stringResource(R.string.settings_group_local_ai_summary)
                ) {
                    GemmaSettingsSection(
                        preferences = preferencesManager,
                        modelManager = gemmaModelManager,
                        downloadScheduler = gemmaModelDownloadScheduler,
                        modelLifecycle = gemmaModelLifecycle,
                        onRequestNotifications = requestNotificationPermission
                    )
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_ai_features),
                    summary = stringResource(R.string.settings_group_ai_summary)
                ) {
                    AiSettingsSection(
                        credibilityScoreEnabled = credibilityScoreEnabled,
                        onCredibilityScoreChange = onToggleCredibilityScore,
                        openRouterApiKey = openRouterApiKey,
                        onOpenRouterApiKeyChange = settingsViewModel::onOpenRouterApiKeyChange,
                        aiModels = aiModels,
                        onOpenModelPicker = { showModelSheet = true }
                    )
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_privacy_diagnostics),
                    summary = stringResource(R.string.settings_group_privacy_summary)
                ) {
                    DiagnosticsSettingsSection(
                        errorReportingEnabled = sentryReportingEnabled,
                        onErrorReportingChange = onToggleSentryReporting,
                        onShowPerformance = { showPerformanceDialog = true }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LocalDataSection(
                        state = sync,
                        canSignOut = serverSettings.hasAllFields,
                        isBusy = serverSettings.isSwitchingBackend,
                        onResyncFromScratch = {
                            requestNotificationPermission(settingsViewModel::resyncFromScratch)
                        },
                        onSignOut = { showSignOutDialog = true }
                    )
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
                    ) {
                        Text(stringResource(R.string.action_sign_out))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
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
    SyncPerformanceOperation.ARTICLE_RECONCILIATION.key -> R.string.settings_operation_article_reconciliation
    SyncPerformanceOperation.INCREMENTAL_SYNC.key -> R.string.settings_operation_incremental_sync
    SyncPerformanceOperation.FULL_SYNC.key -> R.string.settings_operation_full_sync
    else -> R.string.settings_operation_other
}
