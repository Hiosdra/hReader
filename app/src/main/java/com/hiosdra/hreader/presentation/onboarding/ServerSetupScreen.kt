package com.hiosdra.hreader.presentation.onboarding

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.components.ErrorReportingPreferenceCard
import com.hiosdra.hreader.presentation.components.rememberNotificationPermissionRequest
import com.hiosdra.hreader.presentation.settings.BackendServerFields
import com.hiosdra.hreader.presentation.settings.OpenRouterKeyField
import com.hiosdra.hreader.presentation.settings.SettingsViewModel
import com.hiosdra.hreader.presentation.settings.secretHintRes
import com.hiosdra.hreader.presentation.settings.secretLabelRes
import com.hiosdra.hreader.presentation.theme.sectionCardColors
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupScreen(
    onSetupFinished: () -> Unit,
    settingsViewModel: SettingsViewModel = koinViewModel(),
    errorReportingManager: ErrorReporter
) {
    val serverSettings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val openRouterApiKey by settingsViewModel.openRouterApiKey.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    var sentryReportingEnabled by remember {
        mutableStateOf(errorReportingManager.isEnabled())
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.onboarding_connect_server), style = MaterialTheme.typography.titleMedium) },
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
                text = stringResource(
                    R.string.onboarding_description,
                    stringResource(serverSettings.backendType.secretLabelRes).lowercase(),
                    stringResource(serverSettings.backendType.secretHintRes)
                ),
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
                Text(
                    stringResource(
                        if (serverSettings.isConnected) R.string.onboarding_start_reading
                        else R.string.onboarding_continue_without_testing
                    )
                )
            }
        }
    }
}
