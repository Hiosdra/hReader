package com.hiosdra.hreader.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.ui.components.ErrorReportingPreferenceCard
import com.hiosdra.hreader.ui.components.rememberNotificationPermissionRequest
import com.hiosdra.hreader.ui.settings.BackendServerFields
import com.hiosdra.hreader.ui.settings.OpenRouterKeyField
import com.hiosdra.hreader.ui.settings.SettingsViewModel
import com.hiosdra.hreader.ui.theme.sectionCardColors
import com.hiosdra.hreader.util.ErrorReportingManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupScreen(
    onSetupFinished: () -> Unit,
    settingsViewModel: SettingsViewModel = koinViewModel(),
    errorReportingManager: ErrorReportingManager = koinInject()
) {
    val serverSettings by settingsViewModel.uiState.collectAsState()
    val openRouterApiKey by settingsViewModel.openRouterApiKey.collectAsState()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    var sentryReportingEnabled by remember {
        mutableStateOf(errorReportingManager.isEnabled())
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect your feed server", style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "hReader syncs with your own FreshRSS or Miniflux instance. Pick the backend, " +
                    "enter its address, and paste the ${serverSettings.backendType.secretLabel.lowercase()} " +
                    "from ${serverSettings.backendType.secretHint}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ErrorReportingPreferenceCard(
                enabled = sentryReportingEnabled,
                onEnabledChange = { enabled ->
                    sentryReportingEnabled = enabled
                    errorReportingManager.setEnabled(enabled)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = sectionCardColors()
            ) {
                BackendServerFields(
                    state = serverSettings,
                    onBackendTypeChange = { backendType ->
                        requestNotificationPermission {
                            settingsViewModel.onBackendTypeRequested(backendType)
                        }
                    },
                    onServerUrlChange = settingsViewModel::onServerUrlChange,
                    onUsernameChange = settingsViewModel::onUsernameChange,
                    onSecretChange = settingsViewModel::onSecretChange,
                    onTestConnection = settingsViewModel::testConnection,
                    modifier = Modifier.padding(16.dp)
                )
            }
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
            TextButton(
                onClick = {
                    requestNotificationPermission {
                        settingsViewModel.onSetupFinished()
                        onSetupFinished()
                    }
                },
                enabled = serverSettings.hasAllFields,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (serverSettings.isConnected) "Start reading" else "Continue anyway")
            }
        }
    }
}
