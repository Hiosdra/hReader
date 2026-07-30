package com.hiosdra.hreader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.tts.TtsAdvancedSettings
import com.hiosdra.hreader.data.tts.TtsModel
import com.hiosdra.hreader.data.tts.TtsModelManager
import com.hiosdra.hreader.data.tts.TtsModelStatus
import com.hiosdra.hreader.data.tts.TtsLanguages
import com.hiosdra.hreader.ui.theme.sectionCardColors
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun TtsSettingsSection(
    preferences: PreferencesManager,
    modelManager: TtsModelManager
) {
    val statuses by modelManager.statuses.collectAsState()
    val scope = rememberCoroutineScope()
    var speed by remember { mutableFloatStateOf(preferences.getTtsSpeed()) }
    var selectedModel by remember { mutableStateOf(preferences.getTtsModel()) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(preferences.getTtsAdvancedSettings()) }
    var languageOverrides by remember { mutableStateOf(preferences.getTtsLanguageOverrides()) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    Text(
        text = "Read aloud",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = sectionCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TtsModel.entries.forEachIndexed { index, model ->
                val selected = selectedModel == model
                val status = statuses[model] ?: TtsModelStatus.NotInstalled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = status == TtsModelStatus.Available) {
                            preferences.setTtsModel(model)
                            selectedModel = model
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selected) "✓ ${model.displayName}" else model.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = model.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (status is TtsModelStatus.Failed) {
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    when (status) {
                        TtsModelStatus.Available -> if (!model.bundled) {
                            TextButton(onClick = {
                                if (selectedModel == model) {
                                    selectedModel = TtsModel.ANDROID
                                    preferences.setTtsModel(TtsModel.ANDROID)
                                }
                                scope.launch { modelManager.remove(model) }
                            }) {
                                Text("Remove")
                            }
                        }
                        TtsModelStatus.NotInstalled, is TtsModelStatus.Failed -> {
                            Button(onClick = { scope.launch { modelManager.download(model) } }) {
                                Text("Download")
                            }
                        }
                        is TtsModelStatus.Downloading -> {
                            CircularProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
                if (index != TtsModel.entries.lastIndex) HorizontalDivider()
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Text(
                text = "Speed · ${"%.1f".format(speed)}×",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            Slider(
                value = speed,
                onValueChange = {
                    speed = it
                    preferences.setTtsSpeed(it)
                },
                valueRange = 0.7f..1.4f,
                steps = 6
            )
            TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                Text(if (advancedExpanded) "Hide advanced settings" else "Advanced settings")
            }
            if (advancedExpanded) {
                AdvancedTtsSettings(
                    model = selectedModel,
                    settings = advanced,
                    onSettingsChange = {
                        advanced = it
                        preferences.setTtsAdvancedSettings(it)
                    },
                    onReset = {
                        val defaults = TtsAdvancedSettings()
                        advanced = defaults
                        preferences.setTtsAdvancedSettings(defaults)
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Voice by language", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Overrides use the selected default voice when no language rule matches.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            languageOverrides.toSortedMap().forEach { (language, model) ->
                LanguageOverrideRow(
                    language = language,
                    model = model,
                    statuses = statuses,
                    onModelChange = {
                        preferences.setTtsLanguageOverride(language, it)
                        languageOverrides = preferences.getTtsLanguageOverrides()
                    },
                    onRemove = {
                        preferences.setTtsLanguageOverride(language, null)
                        languageOverrides = preferences.getTtsLanguageOverrides()
                    }
                )
            }
            Box {
                TextButton(onClick = { languageMenuExpanded = true }) {
                    Text("Add language override")
                }
                DropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false }
                ) {
                    TtsLanguages.supported
                        .filterNot(languageOverrides::containsKey)
                        .sortedBy(::languageDisplayName)
                        .forEach { language ->
                            DropdownMenuItem(
                                text = { Text(languageDisplayName(language)) },
                                onClick = {
                                    val model = selectedModel.takeIf {
                                        it in TtsLanguages.compatibleModels(language) &&
                                            statuses[it] == TtsModelStatus.Available
                                    } ?: TtsLanguages.compatibleModels(language).firstOrNull {
                                        statuses[it] == TtsModelStatus.Available
                                    } ?: TtsModel.ANDROID
                                    preferences.setTtsLanguageOverride(language, model)
                                    languageOverrides = preferences.getTtsLanguageOverrides()
                                    languageMenuExpanded = false
                                }
                            )
                        }
                }
            }
            Text(
                text = "If a neural model is missing or cannot start, hReader uses Android TTS automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun languageDisplayName(language: String): String =
    Locale.forLanguageTag(language).getDisplayLanguage(Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
private fun LanguageOverrideRow(
    language: String,
    model: TtsModel,
    statuses: Map<TtsModel, TtsModelStatus>,
    onModelChange: (TtsModel) -> Unit,
    onRemove: () -> Unit
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = languageDisplayName(language),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Box {
            TextButton(onClick = { modelMenuExpanded = true }) {
                Text(model.displayName)
            }
            DropdownMenu(
                expanded = modelMenuExpanded,
                onDismissRequest = { modelMenuExpanded = false }
            ) {
                TtsLanguages.compatibleModels(language).forEach { option ->
                    val available = statuses[option] == TtsModelStatus.Available
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (available) option.displayName
                                else "${option.displayName} · not installed"
                            )
                        },
                        enabled = available,
                        onClick = {
                            onModelChange(option)
                            modelMenuExpanded = false
                        }
                    )
                }
            }
        }
        TextButton(onClick = onRemove) {
            Text("Remove")
        }
    }
}

@Composable
private fun AdvancedTtsSettings(
    model: TtsModel,
    settings: TtsAdvancedSettings,
    onSettingsChange: (TtsAdvancedSettings) -> Unit,
    onReset: () -> Unit
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    AdvancedSlider(
        label = "CPU threads",
        value = settings.numThreads.toFloat(),
        displayValue = settings.numThreads.toString(),
        valueRange = 1f..4f,
        steps = 2,
        onValueChange = { onSettingsChange(settings.copy(numThreads = it.roundToInt())) }
    )
    AdvancedSlider(
        label = "Pause between sentences",
        value = settings.silenceScale,
        displayValue = "%.2f".format(settings.silenceScale),
        valueRange = 0f..1f,
        steps = 9,
        onValueChange = { onSettingsChange(settings.copy(silenceScale = it)) }
    )
    when (model) {
        TtsModel.SUPERTONIC -> {
            IntegerSetting(
                label = "Speaker ID",
                value = settings.supertonicSpeaker,
                range = 0..9,
                onValueChange = { onSettingsChange(settings.copy(supertonicSpeaker = it)) }
            )
            AdvancedSlider(
                label = "Quality steps",
                value = settings.supertonicSteps.toFloat(),
                displayValue = settings.supertonicSteps.toString(),
                valueRange = 4f..12f,
                steps = 7,
                onValueChange = {
                    onSettingsChange(settings.copy(supertonicSteps = it.roundToInt()))
                }
            )
        }
        TtsModel.KOKORO -> IntegerSetting(
            label = "Voice ID",
            value = settings.kokoroSpeaker,
            range = 0..102,
            onValueChange = { onSettingsChange(settings.copy(kokoroSpeaker = it)) }
        )
        TtsModel.GOSIA -> {
            AdvancedSlider(
                label = "Voice variation",
                value = settings.gosiaNoiseScale,
                displayValue = "%.2f".format(settings.gosiaNoiseScale),
                valueRange = 0f..1f,
                steps = 19,
                onValueChange = { onSettingsChange(settings.copy(gosiaNoiseScale = it)) }
            )
            AdvancedSlider(
                label = "Duration variation",
                value = settings.gosiaDurationNoiseScale,
                displayValue = "%.2f".format(settings.gosiaDurationNoiseScale),
                valueRange = 0f..1f,
                steps = 19,
                onValueChange = {
                    onSettingsChange(settings.copy(gosiaDurationNoiseScale = it))
                }
            )
        }
        TtsModel.ANDROID -> Text(
            text = "Advanced neural settings do not affect Android TTS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        text = "Changes apply the next time reading starts.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    TextButton(onClick = onReset) {
        Text("Reset advanced settings")
    }
}

@Composable
private fun AdvancedSlider(
    label: String,
    value: Float,
    displayValue: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Text(
        text = "$label · $displayValue",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp)
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps
    )
}

@Composable
private fun IntegerSetting(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label · $value", style = MaterialTheme.typography.bodyMedium)
        Row {
            TextButton(
                onClick = { onValueChange(value - 1) },
                enabled = value > range.first
            ) {
                Text("−")
            }
            TextButton(
                onClick = { onValueChange(value + 1) },
                enabled = value < range.last
            ) {
                Text("+")
            }
        }
    }
}
