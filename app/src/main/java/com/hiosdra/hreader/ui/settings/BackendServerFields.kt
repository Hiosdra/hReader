package com.hiosdra.hreader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.data.model.BackendType

@Composable
fun BackendServerFields(
    state: ServerSettingsUiState,
    onBackendTypeChange: (BackendType) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onSecretChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier,
    onSignOut: (() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackendType.entries.forEach { backendType ->
                FilterChip(
                    selected = state.backendType == backendType,
                    onClick = { onBackendTypeChange(backendType) },
                    enabled = !state.isSwitchingBackend,
                    label = { Text(backendType.displayName) }
                )
            }
        }
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("Server address") },
            placeholder = { Text("https://rss.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        if (state.backendType.requiresUsername) {
            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = state.secret,
            onValueChange = onSecretChange,
            label = { Text(state.backendType.secretLabel) },
            supportingText = { Text(state.backendType.secretHint) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onTestConnection,
            enabled = !state.isTesting && state.hasAllFields,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isTesting) "Testing…" else "Test connection")
        }
        if (onSignOut != null && state.hasAllFields) {
            TextButton(
                onClick = onSignOut,
                enabled = !state.isSwitchingBackend,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign out and clear downloaded articles")
            }
        }
        state.statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}
