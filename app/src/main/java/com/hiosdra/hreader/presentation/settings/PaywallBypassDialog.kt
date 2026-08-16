package com.hiosdra.hreader.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod

/** A closed set of eight short options, so a dialog is enough; the host lives here, not in settings. */
@Composable
fun PaywallBypassDialog(
    selected: PaywallBypassMethod,
    onSelect: (PaywallBypassMethod) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paywall_bypass)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PaywallBypassMethod.entries.forEach { method ->
                    ListItem(
                        headlineContent = {
                            Text(stringResource(method.displayNameRes), style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = method.host,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            RadioButton(
                                selected = selected == method,
                                onClick = { onSelect(method) }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(method) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        }
    )
}
