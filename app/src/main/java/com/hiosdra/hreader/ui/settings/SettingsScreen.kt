package com.hiosdra.hreader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.hiosdra.hreader.data.ai.AiModel
import com.hiosdra.hreader.data.paywall.PaywallBypassMethod
import com.hiosdra.hreader.data.preferences.PreferencesManager
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
    var selectedBypassMethod by remember { mutableStateOf(preferencesManager.getPaywallBypassMethod()) }
    var selectedAiModel by remember { mutableStateOf(preferencesManager.getAiModel()) }
    var bionicReadingEnabled by remember { mutableStateOf(preferencesManager.getBionicReadingEnabled()) }
    var credibilityScoreEnabled by remember { mutableStateOf(preferencesManager.getCredibilityScoreEnabled()) }
    var showPerformanceDialog by remember { mutableStateOf(false) }
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
                        style = MaterialTheme.typography.titleLarge
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
                    text = "FreshRSS Server",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    FreshRssServerFields(
                        state = serverSettings,
                        onServerUrlChange = settingsViewModel::onServerUrlChange,
                        onUsernameChange = settingsViewModel::onUsernameChange,
                        onApiPasswordChange = settingsViewModel::onApiPasswordChange,
                        onTestConnection = settingsViewModel::testConnection,
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
                    shape = MaterialTheme.shapes.medium
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

            // Credibility Score Section
            item {
                Text(
                    text = "AI Credibility Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
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
                                    text = "Credibility Scoring",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "AI analysis of article reliability and trustworthiness",
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
                                text = "Score Meaning:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = "• 70-100%: High credibility (well-sourced, factual)\n• 40-69%: Moderate credibility (some bias or verification needed)\n• 0-39%: Low credibility (high risk of misinformation)",
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
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        PaywallBypassMethod.entries.forEach { method ->
                            ListItem(
                                headlineContent = { 
                                    Text(
                                        method.displayName,
                                        style = MaterialTheme.typography.bodyLarge
                                    ) 
                                },
                                supportingContent = { 
                                    Text(
                                        method.baseUrl, 
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ) 
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    RadioButton(
                                        selected = selectedBypassMethod == method,
                                        onClick = {
                                            selectedBypassMethod = method
                                            preferencesManager.setPaywallBypassMethod(method)
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedBypassMethod = method
                                        preferencesManager.setPaywallBypassMethod(method)
                                    }
                            )
                        }
                    }
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
            }
            items(AiModel.entries.toList()) { model ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    ListItem(
                        headlineContent = { 
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selectedAiModel == model) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            ) 
                        },
                        supportingContent = { 
                            Text(
                                text = model.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedAiModel == model) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 2
                            ) 
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (selectedAiModel == model) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        trailingContent = {
                            if (selectedAiModel == model) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = if (selectedAiModel == model) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedAiModel = model
                                preferencesManager.setAiModel(model)
                            }
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
                    shape = MaterialTheme.shapes.medium
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
