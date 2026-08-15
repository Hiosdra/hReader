package com.hiosdra.hreader.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.ui.theme.sectionCardColors
import com.hiosdra.hreader.util.ErrorReportingManager

@Composable
fun ErrorReportingPreferenceCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = sectionCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = enabled,
                        role = Role.Switch,
                        onValueChange = onEnabledChange
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Send crash reports and diagnostics",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Share crash reports and technical diagnostics with Sentry. You can change this later in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = null
                )
            }
            TextButton(
                onClick = { uriHandler.openUri(ErrorReportingManager.SENTRY_PRIVACY_POLICY_URL) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View privacy policy")
            }
        }
    }
}
