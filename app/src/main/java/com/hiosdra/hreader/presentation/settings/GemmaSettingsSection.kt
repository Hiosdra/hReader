package com.hiosdra.hreader.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.GemmaBackend
import com.hiosdra.hreader.core.application.ai.GemmaModelStatus
import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.core.application.port.out.GemmaModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
import com.hiosdra.hreader.core.application.port.out.GemmaModelLifecycle
import com.hiosdra.hreader.presentation.theme.sectionCardColors
import kotlinx.coroutines.launch

@Composable
internal fun GemmaSettingsSection(
    preferences: AppPreferences,
    modelManager: GemmaModelGateway,
    downloadScheduler: GemmaModelDownloadRequester,
    modelLifecycle: GemmaModelLifecycle,
    onRequestNotifications: (() -> Unit) -> Unit
) {
    val status by modelManager.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var backend by remember { mutableStateOf(preferences.getGemmaBackend()) }
    var backendMenuExpanded by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.settings_local_ai),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = sectionCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.ai_gemma_model_name),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.ai_gemma_model_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = stringResource(R.string.ai_model_size, modelManager.modelSizeBytes / 1_000_000_000f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            when (val currentStatus = status) {
                GemmaModelStatus.NotInstalled -> {
                    Text(
                        text = stringResource(R.string.ai_model_not_downloaded),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onRequestNotifications(downloadScheduler::enqueueDownload) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.ai_download_model))
                    }
                }
                GemmaModelStatus.Available -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.ai_model_ready),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = {
                            downloadScheduler.cancelDownload()
                            scope.launch {
                                modelLifecycle.close()
                                modelManager.remove()
                            }
                        }) {
                            Text(stringResource(R.string.ai_remove_model))
                        }
                    }
                }
                is GemmaModelStatus.Downloading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(progress = { currentStatus.progress })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.ai_model_downloading))
                            Text(
                                text = "${(currentStatus.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = downloadScheduler::cancelDownload) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
                is GemmaModelStatus.Failed -> {
                    Text(
                        text = currentStatus.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = { onRequestNotifications(downloadScheduler::enqueueDownload) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                text = stringResource(R.string.ai_backend),
                style = MaterialTheme.typography.bodyLarge
            )
            Box {
                TextButton(onClick = { backendMenuExpanded = true }) {
                    Text(stringResource(backend.displayNameRes))
                }
                DropdownMenu(
                    expanded = backendMenuExpanded,
                    onDismissRequest = { backendMenuExpanded = false }
                ) {
                    GemmaBackend.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.displayNameRes)) },
                            onClick = {
                                backend = option
                                preferences.setGemmaBackend(option)
                                backendMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.ai_backend_fallback),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val GemmaBackend.displayNameRes: Int
    get() = when (this) {
        GemmaBackend.AUTO -> R.string.ai_backend_auto
        GemmaBackend.CPU -> R.string.ai_backend_cpu
        GemmaBackend.GPU -> R.string.ai_backend_gpu
        GemmaBackend.NPU -> R.string.ai_backend_npu
    }
