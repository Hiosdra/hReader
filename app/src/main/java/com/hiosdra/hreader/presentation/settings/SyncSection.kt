package com.hiosdra.hreader.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hiosdra.hreader.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
    modifier: Modifier = Modifier
) {
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showQuietHoursDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        SettingRow(
            title = stringResource(R.string.sync_automatic),
            value = formatInterval(state.intervalMinutes),
            supportingText = stringResource(R.string.sync_frequency_description),
            onClick = { showIntervalDialog = true }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ToggleSettingRow(
            title = stringResource(R.string.sync_wifi_only),
            description = stringResource(R.string.sync_wifi_description),
            checked = state.unmeteredOnly,
            onCheckedChange = onUnmeteredOnlyChange
        )
        ToggleSettingRow(
            title = stringResource(R.string.sync_roaming),
            description = stringResource(R.string.sync_roaming_description),
            checked = state.syncWhileRoaming,
            onCheckedChange = onSyncWhileRoamingChange
        )
        ToggleSettingRow(
            title = stringResource(R.string.sync_quiet_hours),
            description = if (state.quietHoursEnabled) {
                stringResource(
                    R.string.sync_quiet_hours_range,
                    formatHour(state.quietHoursStart),
                    formatHour(state.quietHoursEnd)
                )
            } else {
                stringResource(R.string.sync_quiet_hours_enabled)
            },
            checked = state.quietHoursEnabled,
            onCheckedChange = onQuietHoursEnabledChange
        )
        if (state.quietHoursEnabled) {
            SettingRow(
                title = stringResource(R.string.sync_quiet_from),
                value = stringResource(
                    R.string.sync_quiet_hours_range,
                    formatHour(state.quietHoursStart),
                    formatHour(state.quietHoursEnd)
                ),
                onClick = { showQuietHoursDialog = true }
            )
        }
    }

    if (showIntervalDialog) {
        ChoiceDialog(
            title = stringResource(R.string.sync_automatic),
            options = SYNC_INTERVAL_CHOICES,
            selected = state.intervalMinutes,
            label = { formatInterval(it) },
            onSelect = {
                onIntervalChange(it)
                showIntervalDialog = false
            },
            onDismiss = { showIntervalDialog = false }
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

/**
 * A lazy, height-capped list rather than a plain column: the hour picker offers twenty-four
 * options, which laid out in full runs off the bottom of the dialog with no way to scroll to it.
 */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
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
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(text = label(option), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
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
        title = { Text(stringResource(R.string.sync_quiet_hours)) },
        text = {
            Column {
                HourPicker(label = stringResource(R.string.sync_from), hours = hours, selected = start, onSelect = { start = it })
                HourPicker(label = stringResource(R.string.sync_to), hours = hours, selected = end, onSelect = { end = it })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(start, end) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
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
            title = stringResource(R.string.sync_hour),
            options = hours,
            selected = selected,
            label = { formatHour(it) },
            onSelect = {
                onSelect(it)
                expanded = false
            },
            onDismiss = { expanded = false }
        )
    }
}

@Composable
private fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> pluralStringResource(R.plurals.sync_minutes, minutes, minutes)
    minutes == 60 -> pluralStringResource(R.plurals.sync_hours, 1, 1)
    minutes % 60 == 0 && minutes < 1440 -> {
        val hours = minutes / 60
        pluralStringResource(R.plurals.sync_hours, hours, hours)
    }
    else -> stringResource(R.string.sync_one_day)
}

@Composable
private fun formatHour(hour: Int): String =
    LocalTime.of(hour, 0)
        .format(
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(LocalLocale.current.platformLocale)
        )
