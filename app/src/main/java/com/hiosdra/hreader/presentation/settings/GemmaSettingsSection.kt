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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.GemmaBackend
import com.hiosdra.hreader.core.application.ai.GemmaModelStatus
import com.hiosdra.hreader.core.application.usecase.settings.GemmaSettingsSnapshot
import com.hiosdra.hreader.presentation.theme.sectionCardColors

@Composable
internal fun GemmaSettingsSection(
    state: GemmaSettingsSnapshot,
    backend: GemmaBackend,
    onBackendChange: (GemmaBackend) -> Unit,
    unmeteredOnly: Boolean,
    onUnmeteredOnlyChange: (Boolean) -> Unit,
    actions: GemmaRuntimeSettingsActions,
    onRequestNotifications: (() -> Unit) -> Unit
) {
    var backendMenuExpanded by remember { mutableStateOf(false) }
    val preflight = state.preflight
    val status = state.status
    val requestDownload = {
        if (preflight.hasEnoughStorage) onRequestNotifications(actions.onEnqueueDownload)
    }

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
                text = stringResource(R.string.ai_model_size, state.modelSizeBytes / 1_000_000_000f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = stringResource(
                    R.string.ai_model_download_details,
                    state.modelSizeBytes / 1_000_000_000f
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            ToggleSettingRow(
                title = stringResource(R.string.ai_model_wifi_only),
                description = if (unmeteredOnly) {
                    stringResource(R.string.ai_model_wifi_only_description)
                } else {
                    stringResource(
                        R.string.ai_model_mobile_data_warning,
                        state.modelSizeBytes / 1_000_000_000f
                    )
                },
                checked = unmeteredOnly,
                onCheckedChange = { enabled ->
                    onUnmeteredOnlyChange(enabled)
                }
            )
            if (status !is GemmaModelStatus.Available) {
                Text(
                    text = stringResource(
                        R.string.ai_model_storage_required,
                        preflight.requiredBytes / 1_000_000_000f
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = stringResource(
                        R.string.ai_model_storage_available,
                        preflight.availableBytes / 1_000_000_000f
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (!preflight.hasEnoughStorage) {
                    Text(
                        text = stringResource(
                            R.string.ai_model_insufficient_storage,
                            preflight.requiredBytes / 1_000_000_000f,
                            preflight.availableBytes / 1_000_000_000f
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    TextButton(
                        onClick = actions.onRefreshPreflight,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(stringResource(R.string.action_refresh))
                    }
                }
            }
            if (preflight.isLowRamDevice) {
                Text(
                    text = stringResource(R.string.ai_model_low_ram_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            when (val currentStatus = status) {
                GemmaModelStatus.NotInstalled -> {
                    Text(
                        text = stringResource(R.string.ai_model_not_downloaded),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = requestDownload,
                        enabled = preflight.hasEnoughStorage,
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
                        TextButton(onClick = actions.onRemoveModel) {
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
                        TextButton(onClick = actions.onCancelDownload) {
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
                        onClick = requestDownload,
                        enabled = preflight.hasEnoughStorage,
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
                                onBackendChange(option)
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

internal val GemmaBackend.displayNameRes: Int
    get() = when (this) {
        GemmaBackend.AUTO -> R.string.ai_backend_auto
        GemmaBackend.CPU -> R.string.ai_backend_cpu
        GemmaBackend.GPU -> R.string.ai_backend_gpu
        GemmaBackend.NPU -> R.string.ai_backend_npu
    }
