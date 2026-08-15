package com.hiosdra.hreader.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.model.BackendType

@Composable
fun BackendSwitchDialog(
    currentBackend: BackendType,
    targetBackend: BackendType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backend_switch_title, stringResource(targetBackend.displayNameRes))) },
        text = {
            Text(
                stringResource(
                    R.string.backend_switch_message,
                    stringResource(currentBackend.displayNameRes),
                    stringResource(targetBackend.displayNameRes)
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_switch_and_clear_data)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
