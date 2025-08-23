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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController? = null,
    preferencesManager: PreferencesManager = koinInject()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            var selectedBypassMethod by remember { mutableStateOf(preferencesManager.getPaywallBypassMethod()) }
            var selectedAiModel by remember { mutableStateOf(preferencesManager.getAiModel()) }
            var showPerformanceDialog by remember { mutableStateOf(false) }

            // Paywall Bypass Method Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Paywall Bypass Method",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    PaywallBypassMethod.entries.forEach { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBypassMethod = method
                                    preferencesManager.setPaywallBypassMethod(method)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            RadioButton(
                                selected = selectedBypassMethod == method,
                                onClick = {
                                    selectedBypassMethod = method
                                    preferencesManager.setPaywallBypassMethod(method)
                                }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(text = method.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = method.baseUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // AI Model Selection Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "AI Model for Article Overviews",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    AiModel.entries.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAiModel = model
                                    preferencesManager.setAiModel(model)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            RadioButton(
                                selected = selectedAiModel == model,
                                onClick = {
                                    selectedAiModel = model
                                    preferencesManager.setAiModel(model)
                                }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(text = model.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = model.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Performance Info Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Performance",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(
                        onClick = { showPerformanceDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show Performance Info")
                    }
                }
            }

            // Performance Dialog
            if (showPerformanceDialog) {
                PerformanceInfoDialog(
                    performanceRecords = preferencesManager.getSyncPerformanceRecords(),
                    onDismiss = { showPerformanceDialog = false },
                    onClearRecords = {
                        preferencesManager.clearSyncPerformanceRecords()
                        showPerformanceDialog = false
                    }
                )
            }
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
