package com.hiosdra.hreader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Keeps a long list of options inside the dialog instead of pushing its buttons off screen. */
private val CHOICE_LIST_MAX_HEIGHT = 320.dp

/** The intervals WorkManager can express cleanly, above its own fifteen-minute floor. */
private val SYNC_INTERVAL_CHOICES = listOf(15, 30, 60, 180, 360, 720, 1440)

@Composable
fun SyncSection(
    state: SyncUiState,
    onIntervalChange: (Int) -> Unit,
    onUnmeteredOnlyChange: (Boolean) -> Unit,
    onSyncWhileRoamingChange: (Boolean) -> Unit,
    onQuietHoursEnabledChange: (Boolean) -> Unit,
    onQuietHoursChange: (Int, Int) -> Unit,
    onResyncFromScratch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showQuietHoursDialog by remember { mutableStateOf(false) }
    var showResyncDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        SettingRow(
            title = "Sync every",
            value = formatInterval(state.intervalMinutes),
            supportingText = "How often articles are fetched in the background",
            onClick = { showIntervalDialog = true }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ToggleRow(
            title = "Wi-Fi only",
            description = "Article bodies and images are the bulk of a sync. Holding them for " +
                "Wi-Fi keeps a large backlog off the mobile bill.",
            checked = state.unmeteredOnly,
            onCheckedChange = onUnmeteredOnlyChange
        )
        ToggleRow(
            title = "Sync while roaming",
            description = "Off keeps background syncing paused until the device is back on its " +
                "own network.",
            checked = state.syncWhileRoaming,
            onCheckedChange = onSyncWhileRoamingChange
        )
        ToggleRow(
            title = "Quiet hours",
            description = if (state.quietHoursEnabled) {
                "No background syncing between ${formatHour(state.quietHoursStart)} and " +
                    "${formatHour(state.quietHoursEnd)}. Preparing for offline still runs."
            } else {
                "Pause background syncing overnight"
            },
            checked = state.quietHoursEnabled,
            onCheckedChange = onQuietHoursEnabledChange
        )
        if (state.quietHoursEnabled) {
            SettingRow(
                title = "Quiet from",
                value = "${formatHour(state.quietHoursStart)} – ${formatHour(state.quietHoursEnd)}",
                onClick = { showQuietHoursDialog = true }
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = "Rebuild from the server",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Deletes every downloaded article, feed and image, then fetches the account " +
                "again from nothing. For a local copy that no longer matches the server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )
        TextButton(
            onClick = { showResyncDialog = true },
            enabled = !state.isResyncing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (state.isResyncing) "Rebuilding…" else "Delete local data and resync",
                color = if (state.isResyncing) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }

    if (showIntervalDialog) {
        ChoiceDialog(
            title = "Sync every",
            options = SYNC_INTERVAL_CHOICES,
            selected = state.intervalMinutes,
            label = ::formatInterval,
            onSelect = {
                onIntervalChange(it)
                showIntervalDialog = false
            },
            onDismiss = { showIntervalDialog = false }
        )
    }

    if (showResyncDialog) {
        AlertDialog(
            onDismissRequest = { showResyncDialog = false },
            title = { Text("Delete local data and resync?") },
            text = {
                Text(
                    "Every downloaded article, feed and image goes, including anything kept for " +
                        "reading offline. The account is then fetched again from the server, " +
                        "which needs a connection and can take a while on a large backlog. " +
                        "Nothing on the server is touched."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResyncDialog = false
                    onResyncFromScratch()
                }) {
                    Text("Delete and resync", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResyncDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showQuietHoursDialog) {
        QuietHoursDialog(
            startHour = state.quietHoursStart,
            endHour = state.quietHoursEnd,
            onConfirm = { start, end ->
                onQuietHoursChange(start, end)
                showQuietHoursDialog = false
            },
            onDismiss = { showQuietHoursDialog = false }
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A lazy, height-capped list rather than a plain column: the hour picker offers twenty-four
 * options, which laid out in full runs off the bottom of the dialog with no way to scroll to it.
 */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = CHOICE_LIST_MAX_HEIGHT)) {
                items(options) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Text(text = label(option), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun QuietHoursDialog(
    startHour: Int,
    endHour: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var start by remember { mutableIntStateOf(startHour) }
    var end by remember { mutableIntStateOf(endHour) }
    val hours = (0..23).toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quiet hours") },
        text = {
            Column {
                HourPicker(label = "From", hours = hours, selected = start, onSelect = { start = it })
                HourPicker(label = "To", hours = hours, selected = end, onSelect = { end = it })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(start, end) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun HourPicker(label: String, hours: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SettingRow(
        title = label,
        value = formatHour(selected),
        onClick = { expanded = true }
    )
    if (expanded) {
        ChoiceDialog(
            title = "Hour",
            options = hours,
            selected = selected,
            label = ::formatHour,
            onSelect = {
                onSelect(it)
                expanded = false
            },
            onDismiss = { expanded = false }
        )
    }
}

private fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> "$minutes minutes"
    minutes == 60 -> "1 hour"
    minutes % 60 == 0 && minutes < 1440 -> "${minutes / 60} hours"
    else -> "1 day"
}

private fun formatHour(hour: Int): String = "%02d:00".format(hour)
