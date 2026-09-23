package com.hiosdra.hreader.presentation.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.ai.GemmaModelStatus
import com.hiosdra.hreader.core.application.ai.GemmaBackend
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.GemmaModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
import com.hiosdra.hreader.core.application.port.out.GemmaModelLifecycle
import com.hiosdra.hreader.core.application.port.out.PerformancePreferences
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import com.hiosdra.hreader.core.application.port.out.TtsPreferences
import com.hiosdra.hreader.presentation.components.rememberNotificationPermissionRequest
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.observability.SyncPerformanceRecord
import com.hiosdra.hreader.presentation.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController? = null,
    onSignedOut: () -> Unit = {},
    readerPreferences: ReaderPreferences,
    ttsPreferences: TtsPreferences,
    aiPreferences: AiPreferences,
    performancePreferences: PerformancePreferences,
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
    val storage by settingsViewModel.storage.collectAsStateWithLifecycle()
    val localAiStatus by gemmaModelManager.status.collectAsStateWithLifecycle()
    val currentRoute = navController?.currentBackStackEntryAsState()?.value?.destination?.route
    val context = LocalContext.current
    val actions = remember(settingsViewModel) { settingsViewModel.actions }
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    var selectedBypassMethod by remember { mutableStateOf(readerPreferences.getPaywallBypassMethod()) }
    var bionicReadingEnabled by remember { mutableStateOf(readerPreferences.getBionicReadingEnabled()) }
    var credibilityScoreEnabled by remember { mutableStateOf(readerPreferences.getCredibilityScoreEnabled()) }
    var sentryReportingEnabled by remember { mutableStateOf(errorReportingManager.isEnabled()) }
    var gemmaBackend by remember { mutableStateOf(aiPreferences.getGemmaBackend()) }
    var gemmaDownloadOnUnmeteredOnly by remember {
        mutableStateOf(aiPreferences.getGemmaDownloadOnUnmeteredOnly())
    }
    var showPerformanceDialog by remember { mutableStateOf(false) }
    var showBypassDialog by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    val onToggleBionicReading: (Boolean) -> Unit = { enabled ->
        bionicReadingEnabled = enabled
        readerPreferences.setBionicReadingEnabled(enabled)
    }
    val onToggleCredibilityScore: (Boolean) -> Unit = { enabled ->
        credibilityScoreEnabled = enabled
        readerPreferences.setCredibilityScoreEnabled(enabled)
    }
    val onToggleSentryReporting: (Boolean) -> Unit = { enabled ->
        sentryReportingEnabled = enabled
        errorReportingManager.setEnabled(enabled)
    }
    LaunchedEffect(currentRoute) {
        selectedBypassMethod = readerPreferences.getPaywallBypassMethod()
    }
    val readingSummary = "${stringResource(R.string.settings_bionic_reading)}: " +
        "${stringResource(if (bionicReadingEnabled) R.string.settings_state_enabled else R.string.settings_state_disabled)}\n" +
        "${stringResource(R.string.tts_read_aloud)}: ${stringResource(ttsPreferences.getTtsModel().displayNameRes)} · " +
        "${stringResource(R.string.settings_bypass_service)}: ${stringResource(selectedBypassMethod.displayNameRes)}"
    val storageSummary = storage.snapshot?.let { snapshot ->
        stringResource(
            R.string.storage_app_usage,
            Formatter.formatShortFileSize(context, snapshot.appBytes)
        )
    } ?: stringResource(
        if (storage.isLoading) R.string.storage_refreshing else R.string.settings_group_storage_summary
    )
    val localAiStatusSummary = stringResource(
        when (localAiStatus) {
            GemmaModelStatus.Available -> R.string.ai_model_ready
            GemmaModelStatus.NotInstalled -> R.string.ai_model_not_downloaded
            is GemmaModelStatus.Downloading -> R.string.ai_model_downloading
            is GemmaModelStatus.Failed -> R.string.ai_model_install_failed
        }
    )
    val localAiSummary = "$localAiStatusSummary\n${stringResource(R.string.ai_backend)}: " +
        "${stringResource(gemmaBackend.displayNameRes)} · ${stringResource(R.string.ai_model_wifi_only)}: " +
        stringResource(
            if (gemmaDownloadOnUnmeteredOnly) R.string.settings_state_enabled
            else R.string.settings_state_disabled
        )
    val cloudAiSummary = "${stringResource(R.string.article_ai_cloud_processing)}\n" +
        "${stringResource(if (openRouterApiKey.isBlank()) R.string.secret_not_configured else R.string.secret_configured)} · " +
        "${stringResource(R.string.settings_model)}: ${aiModels.selectedModelName} · " +
        "${stringResource(R.string.settings_show_credibility_chip)}: " +
        stringResource(if (credibilityScoreEnabled) R.string.settings_state_enabled else R.string.settings_state_disabled)
    val privacySummary = "${stringResource(R.string.error_reporting_title)}: " +
        stringResource(if (sentryReportingEnabled) R.string.settings_state_enabled else R.string.settings_state_disabled)
    val serverSummary = "${stringResource(serverSettings.backendType.displayNameRes)} · " +
        stringResource(
            if (serverSettings.hasAllFields) R.string.secret_configured
            else R.string.secret_not_configured
        )
    val syncSummary = syncSettingsSummary(sync)
    val licensesSummary = stringResource(R.string.settings_licenses_summary)

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
            settingsGroup(
                titleRes = R.string.settings_rss_server,
                summary = serverSummary
            ) {
                BackendServerFields(
                    state = serverSettings,
                    onBackendTypeChange = { backendType ->
                        requestNotificationPermission {
                            actions.server.onBackendTypeRequested(backendType)
                        }
                    },
                    onServerUrlChange = actions.server.onServerUrlChange,
                    onUsernameChange = actions.server.onUsernameChange,
                    onSecretChange = actions.server.onSecretChange,
                    onTestConnection = actions.server.onTestConnection,
                    onApplySettings = actions.server.onApplySettings
                )
            }

            settingsGroup(
                titleRes = R.string.settings_sync,
                summary = syncSummary
            ) {
                SyncSection(
                    state = sync,
                    onIntervalChange = actions.sync.onIntervalChange,
                    onSyncModeChange = actions.sync.onSyncModeChange,
                    onUnmeteredOnlyChange = actions.sync.onUnmeteredOnlyChange,
                    onSyncWhileRoamingChange = actions.sync.onSyncWhileRoamingChange,
                    onQuietHoursEnabledChange = actions.sync.onQuietHoursEnabledChange,
                    onQuietHoursChange = actions.sync.onQuietHoursChange,
                    onOpenFreshness = { navController?.navigate(Routes.SYNC_HEALTH) }
                )
            }

            item {
                SettingsDestinationCard(
                    title = stringResource(R.string.travel_mode_title),
                    summary = offlineSettingsSummary(offline),
                    onClick = { navController?.navigate(Routes.OFFLINE_SETTINGS) }
                )
            }

            settingsGroup(
                titleRes = R.string.settings_storage,
                summary = storageSummary
            ) {
                StorageSettingsSection(
                    state = storage,
                    onRefresh = actions.storage.onRefresh,
                    onCleanup = actions.storage.onCleanup
                )
            }

            settingsGroup(
                titleRes = R.string.settings_reading_experience,
                summary = readingSummary
            ) {
                ReadingSettingsSection(
                    bionicReadingEnabled = bionicReadingEnabled,
                    onBionicReadingChange = onToggleBionicReading,
                    selectedTtsModel = ttsPreferences.getTtsModel(),
                    selectedBypassMethod = selectedBypassMethod,
                    onOpenTts = { navController?.navigate(Routes.TTS_SETTINGS) },
                    onOpenBypass = { showBypassDialog = true }
                )
            }

            settingsGroup(
                titleRes = R.string.settings_local_ai,
                summary = localAiSummary
            ) {
                GemmaSettingsSection(
                    backend = gemmaBackend,
                    onBackendChange = { backend ->
                        gemmaBackend = backend
                        aiPreferences.setGemmaBackend(backend)
                    },
                    unmeteredOnly = gemmaDownloadOnUnmeteredOnly,
                    onUnmeteredOnlyChange = { enabled ->
                        gemmaDownloadOnUnmeteredOnly = enabled
                        aiPreferences.setGemmaDownloadOnUnmeteredOnly(enabled)
                    },
                    modelManager = gemmaModelManager,
                    downloadScheduler = gemmaModelDownloadScheduler,
                    modelLifecycle = gemmaModelLifecycle,
                    onRequestNotifications = requestNotificationPermission
                )
            }

            settingsGroup(
                titleRes = R.string.settings_ai_features,
                summary = cloudAiSummary
            ) {
                AiSettingsSection(
                    credibilityScoreEnabled = credibilityScoreEnabled,
                    onCredibilityScoreChange = onToggleCredibilityScore,
                    openRouterApiKey = openRouterApiKey,
                    onOpenRouterApiKeyChange = actions.ai.onOpenRouterApiKeyChange,
                    aiModels = aiModels,
                    onOpenModelPicker = { showModelSheet = true }
                )
            }

            settingsGroup(
                titleRes = R.string.settings_privacy_diagnostics,
                summary = privacySummary
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
                        requestNotificationPermission(actions.localData.onResyncFromScratch)
                    },
                    onSignOut = { showSignOutDialog = true }
                )
            }

            settingsGroup(
                titleRes = R.string.settings_licenses,
                summary = licensesSummary
            ) {
                Text(
                    text = stringResource(R.string.settings_licenses_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { navController?.navigate(Routes.LICENSES) }) {
                    Text(stringResource(R.string.settings_view_licenses))
                }
            }
        }

        serverSettings.pendingBackendType?.let { target ->
            BackendSwitchDialog(
                currentBackend = serverSettings.backendType,
                targetBackend = target,
                onConfirm = actions.server.onConfirmBackendSwitch,
                onDismiss = actions.server.onCancelBackendSwitch
            )
        }
        if (showBypassDialog) {
            PaywallBypassDialog(
                selected = selectedBypassMethod,
                onSelect = { method ->
                    selectedBypassMethod = method
                    readerPreferences.setPaywallBypassMethod(method)
                    showBypassDialog = false
                },
                onDismiss = { showBypassDialog = false }
            )
        }
        if (showModelSheet) {
            AiModelSheet(
                state = aiModels,
                onSearchQueryChange = actions.ai.onModelSearchQueryChange,
                onFreeOnlyChange = actions.ai.onFreeOnlyChange,
                onReload = actions.ai.onReloadModels,
                onModelSelected = { model ->
                    actions.ai.onModelSelected(model)
                    showModelSheet = false
                },
                onDismiss = { showModelSheet = false }
            )
        }
        if (showPerformanceDialog) {
            PerformanceInfoDialog(
                performanceRecords = performancePreferences.getSyncPerformanceRecords(),
                onDismiss = { showPerformanceDialog = false },
                onClearRecords = { performancePreferences.clearSyncPerformanceRecords() }
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
                            actions.localData.onSignOut()
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
        record.requestCount?.let { requestCount ->
            Text(
                text = stringResource(
                    R.string.settings_network_metrics,
                    requestCount,
                    record.errorCount ?: 0,
                    record.totalBytes ?: 0L,
                    record.throughputBytesPerSecond ?: 0L,
                    record.averageResponseMs ?: 0L,
                    record.maxConcurrentRequests ?: 0
                ),
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

private fun LazyListScope.settingsGroup(
    titleRes: Int,
    summary: String,
    content: @Composable () -> Unit
) {
    item {
        SettingsGroup(
            title = stringResource(titleRes),
            summary = summary,
            content = content
        )
    }
}

@Composable
private fun offlineSettingsSummary(state: OfflineUiState): String {
    val readiness = state.readiness
    val articleSummary = when {
        readiness.offlineTargetCount == 0 -> stringResource(R.string.offline_nothing_available)
        readiness.missingContentCount == 0 -> pluralStringResource(
            R.plurals.offline_ready,
            readiness.offlineTargetCount,
            readiness.offlineTargetCount
        )
        else -> pluralStringResource(
            R.plurals.offline_available,
            readiness.storedContentCount,
            readiness.storedContentCount,
            readiness.offlineTargetCount
        )
    }
    val targetSummary = if (state.backlogTarget == 0) {
        stringResource(R.string.offline_unread_only)
    } else {
        "${stringResource(R.string.offline_articles_to_keep)}: ${state.backlogTarget}"
    }
    val imageState = stringResource(
        if (state.imageDownloadEnabled) R.string.settings_state_enabled else R.string.settings_state_disabled
    )
    val imageBudget = state.imageCacheBudgetMegabytes.takeIf { state.imageDownloadEnabled && it > 0 }
        ?.let { " · ${stringResource(R.string.offline_image_budget, it)}" }
        .orEmpty()
    val imageSummary = "${stringResource(R.string.offline_download_images)}: $imageState$imageBudget"
    return "$articleSummary\n$targetSummary · $imageSummary"
}
