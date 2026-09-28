package com.hiosdra.hreader.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.presentation.theme.sectionCardColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentryConsentScreen(
    errorReporter: ErrorReporter,
    onChoiceSaved: () -> Unit
) {
    var selectedChoice by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val uriHandler = LocalUriHandler.current
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.sentry_consent_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    selectedChoice?.let { enabled ->
                        errorReporter.setEnabled(enabled)
                        onChoiceSaved()
                    }
                },
                enabled = selectedChoice != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(stringResource(R.string.sentry_consent_continue))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.sentry_consent_encouragement),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.sentry_consent_data_details),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = sectionCardColors()
            ) {
                Column {
                    SentryChoiceRow(
                        label = stringResource(R.string.sentry_consent_send),
                        selected = selectedChoice == true,
                        onClick = { selectedChoice = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SentryChoiceRow(
                        label = stringResource(R.string.sentry_consent_dont_send),
                        selected = selectedChoice == false,
                        onClick = { selectedChoice = false }
                    )
                }
            }
            TextButton(
                onClick = { uriHandler.openUri(privacyPolicyUrl) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.error_reporting_privacy))
            }
        }
    }
}

@Composable
private fun SentryChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
