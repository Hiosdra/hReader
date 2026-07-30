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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hiosdra.hreader.data.paywall.PaywallBypassMethod
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.ui.theme.sectionCardColors
import com.hiosdra.hreader.util.SyncPerformanceRecord
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController? = null,
    preferencesManager: PreferencesManager = koinInject(),
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val serverSettings by settingsViewModel.uiState.collectAsState()
    val openRouterApiKey by settingsViewModel.openRouterApiKey.collectAsState()
    val aiModels by settingsViewModel.aiModels.collectAsState()
    val offline by settingsViewModel.offline.collectAsState()
    val sync by settingsViewModel.sync.collectAsState()
    var selectedBypassMethod by remember { mutableStateOf(preferencesManager.getPaywallBypassMethod()) }
    var bionicReadingEnabled by remember { mutableStateOf(preferencesManager.getBionicReadingEnabled()) }
    var credibilityScoreEnabled by remember { mutableStateOf(preferencesManager.getCredibilityScoreEnabled()) }
    var showPerformanceDialog by remember { mutableStateOf(false) }
    var showBypassDialog by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    val onToggleBionicReading: (Boolean) -> Unit = { enabled ->
        bionicReadingEnabled = enabled
        preferencesManager.setBionicReadingEnabled(enabled)
    }
    val onToggleCredibilityScore: (Boolean) -> Unit = { enabled ->
        credibilityScoreEnabled = enabled
        preferencesManager.setCredibilityScoreEnabled(enabled)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
                    text = "Feed server",
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
                        onBackendTypeChange = settingsViewModel::onBackendTypeRequested,
                        onServerUrlChange = settingsViewModel::onServerUrlChange,
                        onUsernameChange = settingsViewModel::onUsernameChange,
                        onSecretChange = settingsViewModel::onSecretChange,
                        onTestConnection = settingsViewModel::testConnection,
                        modifier = Modifier.padding(16.dp),
                        onSignOut = settingsViewModel::signOut
                    )
                }
            }

            item {
                Text(
                    text = "Synchronisation",
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
                    text = "Offline readiness",
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
                        onPrepare = settingsViewModel::prepareForOffline,
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
                    text = "Reading Experience",
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
                                    text = "Bionic Reading",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Highlight portions of words for faster reading",
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
                Text(
                    text = "AI Credibility Analysis",
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
                                    text = "Show credibility chip",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Adds a chip to every article that rates the text for sourcing, tone and balance",
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
                                text = "What the rating means:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = "• Strong signals: sourced, evidenced, balanced\n• Mixed signals: verify before relying on it\n• Weak signals: sensational or unsupported claims",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "This is an AI impression of the article text, not a fact check — " +
                                    "the model cannot browse the web or verify claims. Each analysis sends " +
                                    "the article to OpenRouter and is cached until you re-analyze it.",
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
                    text = "Paywall Bypass Method",
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
                        title = "Bypass service",
                        value = selectedBypassMethod.displayName,
                        supportingText = selectedBypassMethod.host,
                        onClick = { showBypassDialog = true }
                    )
                }
            }

            // OpenRouter Key Section
            item {
                Text(
                    text = "OpenRouter",
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
                    text = "AI Model for Article Overview",
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
                        title = "Model",
                        value = aiModels.selectedModelName,
                        supportingText = aiModels.selectedModelId.takeIf { it != aiModels.selectedModelName },
                        onClick = { showModelSheet = true }
                    )
                }
                if (aiModels.selectedModelIsMissing) {
                    Text(
                        text = "OpenRouter no longer offers this model. Pick another one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Performance Section
            item {
                Text(
                    text = "Performance",
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
                            Text("Show Performance Info")
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
                "Sync Performance Info",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn {
                if (performanceRecords.isEmpty()) {
                    item {
                        Text(
                            "No performance data available yet.\nSync some articles to see performance metrics.",
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
                        Text("Clear All")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun PerformanceRecordItem(record: SyncPerformanceRecord) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = record.operationName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = record.getFormattedDuration(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = record.getFormattedTimestamp(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Additional details based on what's available
        record.batchSize?.let { batchSize ->
            record.totalArticles?.let { totalArticles ->
                Text(
                    text = "$totalArticles articles in batches of $batchSize",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        record.isIncremental?.let { isIncremental ->
            val syncType = if (isIncremental) "Incremental" else "Full"
            val syncInfo = record.lastSyncHoursAgo?.let { hours ->
                "$syncType sync (last sync: ${hours}h ago)"
            } ?: "$syncType sync"

            Text(
                text = syncInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
