package com.hiosdra.hreader.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.components.ErrorReportingPreferenceContent
import com.hiosdra.hreader.presentation.theme.sectionCardColors

@Composable
internal fun DiagnosticsSettingsSection(
    errorReportingEnabled: Boolean,
    onErrorReportingChange: (Boolean) -> Unit,
    onShowPerformance: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
    Text(
        text = stringResource(R.string.settings_privacy_diagnostics),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = sectionCardColors()
    ) {
        Column {
            ErrorReportingPreferenceContent(
                enabled = errorReportingEnabled,
                onEnabledChange = onErrorReportingChange,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider()
            TextButton(
                onClick = { uriHandler.openUri(privacyPolicyUrl) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.privacy_policy_title))
            }
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.settings_sync_performance),
                value = stringResource(R.string.settings_show_performance),
                onClick = onShowPerformance
            )
        }
    }
}
