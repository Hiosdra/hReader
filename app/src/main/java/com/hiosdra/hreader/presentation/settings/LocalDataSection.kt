package com.hiosdra.hreader.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.presentation.theme.sectionCardColors

@Composable
internal fun LocalDataSection(
    state: SyncUiState,
    canSignOut: Boolean,
    isBusy: Boolean,
    onResyncFromScratch: () -> Unit,
    onSignOut: () -> Unit
) {
    var showResyncDialog by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.settings_local_data),
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
            Text(
                text = stringResource(R.string.sync_clear_local_data),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.sync_clear_local_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
            TextButton(
                onClick = { showResyncDialog = true },
                enabled = !state.isResyncing && !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        if (state.isResyncing) R.string.sync_clearing_data else R.string.sync_clear_and_sync
                    ),
                    color = if (state.isResyncing || isBusy) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            ResyncStatus(state)
            if (canSignOut) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(R.string.settings_sign_out_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_sign_out_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
                TextButton(
                    onClick = onSignOut,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.action_sign_out),
                        color = if (isBusy) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        }
    }

    if (showResyncDialog) {
        AlertDialog(
            onDismissRequest = { showResyncDialog = false },
            title = { Text(stringResource(R.string.sync_clear_confirm_title)) },
            text = { Text(stringResource(R.string.sync_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResyncDialog = false
                        onResyncFromScratch()
                    },
                    enabled = !isBusy
                ) {
                    Text(
                        stringResource(R.string.sync_clear_data),
                        color = if (isBusy) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResyncDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ResyncStatus(state: SyncUiState) {
    if (!state.showResyncStatus) return
    when (state.resyncStatus.state) {
        SyncOperationState.SUCCEEDED -> Text(
            text = stringResource(R.string.sync_complete),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall
        )
        SyncOperationState.FAILED -> {
            val errorMessage = state.resyncStatus.error?.let { stringResource(it.messageResId) }
                ?: state.resyncStatus.errorMessage
                ?: stringResource(R.string.sync_try_again)
            Text(
                text = stringResource(R.string.sync_failed, errorMessage),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        SyncOperationState.CANCELLED -> Text(
            text = stringResource(R.string.sync_cancelled),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        SyncOperationState.IDLE,
        SyncOperationState.RUNNING -> Unit
    }
}
