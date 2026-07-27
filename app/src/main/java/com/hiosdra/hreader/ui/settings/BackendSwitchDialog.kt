package com.hiosdra.hreader.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
        title = { Text("Switch to ${targetBackend.displayName}?") },
        text = {
            Text(
                "hReader syncs with one server at a time. Switching signs you out of " +
                    "${currentBackend.displayName} and deletes every article, feed and image " +
                    "downloaded from it. Your ${targetBackend.displayName} credentials are kept " +
                    "and a fresh sync starts afterwards."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Switch and clear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
