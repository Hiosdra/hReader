package com.hiosdra.hreader.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.hiosdra.hreader.R

@Composable
internal fun SecretSettingRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    placeholder: String? = null,
    onSave: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingRow(
        title = title,
        value = stringResource(if (value.isBlank()) R.string.secret_not_configured else R.string.secret_configured),
        supportingText = supportingText,
        modifier = modifier,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        SecretEditorDialog(
            title = title,
            initialValue = value,
            supportingText = supportingText,
            placeholder = placeholder,
            onSave = {
                onSave(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun SecretEditorDialog(
    title: String,
    initialValue: String,
    supportingText: String?,
    placeholder: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                supportingText?.let { Text(it) }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(title) },
                    placeholder = {
                        Text(placeholder ?: stringResource(R.string.secret_editor_placeholder))
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
