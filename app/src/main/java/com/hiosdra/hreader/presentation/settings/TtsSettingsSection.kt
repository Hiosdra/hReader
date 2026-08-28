package com.hiosdra.hreader.presentation.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.TtsModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.port.out.TtsPreferences
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsEngineFamily
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
import com.hiosdra.hreader.core.application.tts.TtsLanguages
import com.hiosdra.hreader.presentation.theme.sectionCardColors
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun TtsSettingsSection(
    preferences: TtsPreferences,
    modelManager: TtsModelGateway,
    downloadScheduler: TtsModelDownloadRequester,
    onRequestNotifications: (() -> Unit) -> Unit
) {
    val statuses by modelManager.statuses.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var speed by remember { mutableFloatStateOf(preferences.getTtsSpeed()) }
    var selectedModel by remember { mutableStateOf(preferences.getTtsModel()) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(preferences.getTtsAdvancedSettings()) }
    var languageOverrides by remember { mutableStateOf(preferences.getTtsLanguageOverrides()) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val models = TtsModelCatalog.models

    Text(
        text = stringResource(R.string.tts_read_aloud),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = sectionCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            models.forEachIndexed { index, model ->
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
                        val modelName = stringResource(model.displayNameRes)
                        Text(
                            text = if (selected) {
                                stringResource(R.string.tts_voice_selected, modelName)
                            } else {
                                modelName
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(model.descriptionRes),
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
                                downloadScheduler.cancelDownload(model)
                                scope.launch { modelManager.remove(model) }
                            }) {
                                Text(stringResource(R.string.tts_remove_voice))
                            }
                        }
                        TtsModelStatus.NotInstalled, is TtsModelStatus.Failed -> {
                            Button(onClick = {
                                onRequestNotifications { downloadScheduler.enqueueDownload(model) }
                            }) {
                                Text(stringResource(R.string.tts_download_voice))
                            }
                        }
                        is TtsModelStatus.Downloading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    progress = { status.progress },
                                    modifier = Modifier.padding(8.dp)
                                )
                                TextButton(onClick = { downloadScheduler.cancelDownload(model) }) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            }
                        }
                    }
                }
                if (index != models.lastIndex) HorizontalDivider()
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Text(
                text = stringResource(R.string.tts_reading_speed, "%.1f".format(speed)),
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
                Text(stringResource(if (advancedExpanded) R.string.tts_advanced_hide else R.string.tts_advanced_show))
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
            Text(stringResource(R.string.tts_voices_by_language), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.tts_language_description),
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
                    Text(stringResource(R.string.tts_choose_voice))
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
                text = stringResource(R.string.tts_missing_voice_description),
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
                Text(stringResource(model.displayNameRes))
            }
            DropdownMenu(
                expanded = modelMenuExpanded,
                onDismissRequest = { modelMenuExpanded = false }
            ) {
                TtsLanguages.compatibleModels(language).forEach { option ->
                    val available = statuses[option] == TtsModelStatus.Available
                    val optionName = stringResource(option.displayNameRes)
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (available) optionName
                                else stringResource(R.string.tts_not_downloaded, optionName)
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
            Text(stringResource(R.string.tts_remove_voice))
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
        label = stringResource(R.string.tts_cpu_threads),
        value = settings.numThreads.toFloat(),
        displayValue = settings.numThreads.toString(),
        valueRange = 1f..4f,
        steps = 2,
        onValueChange = { onSettingsChange(settings.copy(numThreads = it.roundToInt())) }
    )
    AdvancedSlider(
        label = stringResource(R.string.tts_pause_between_sentences),
        value = settings.silenceScale,
        displayValue = "%.2f".format(settings.silenceScale),
        valueRange = 0f..1f,
        steps = 9,
        onValueChange = { onSettingsChange(settings.copy(silenceScale = it)) }
    )
    when (model.family) {
        TtsEngineFamily.SUPERTONIC -> {
            IntegerSetting(
                label = stringResource(R.string.tts_speaker_id),
                value = settings.supertonicSpeaker,
                range = 0..9,
                onValueChange = { onSettingsChange(settings.copy(supertonicSpeaker = it)) }
            )
            AdvancedSlider(
                label = stringResource(R.string.tts_quality_steps),
                value = settings.supertonicSteps.toFloat(),
                displayValue = settings.supertonicSteps.toString(),
                valueRange = 4f..12f,
                steps = 7,
                onValueChange = {
                    onSettingsChange(settings.copy(supertonicSteps = it.roundToInt()))
                }
            )
        }
        TtsEngineFamily.KOKORO -> IntegerSetting(
            label = stringResource(R.string.tts_voice_id),
            value = settings.kokoroSpeaker,
            range = 0..102,
            onValueChange = { onSettingsChange(settings.copy(kokoroSpeaker = it)) }
        )
        TtsEngineFamily.KITTEN -> IntegerSetting(
            label = stringResource(R.string.tts_voice_id),
            value = settings.kittenSpeaker,
            range = 0..7,
            onValueChange = { onSettingsChange(settings.copy(kittenSpeaker = it)) }
        )
        TtsEngineFamily.VITS -> {
            AdvancedSlider(
                label = stringResource(R.string.tts_voice_variation),
                value = settings.vitsNoiseScale,
                displayValue = "%.2f".format(settings.vitsNoiseScale),
                valueRange = 0f..1f,
                steps = 19,
                onValueChange = { onSettingsChange(settings.copy(vitsNoiseScale = it)) }
            )
            AdvancedSlider(
                label = stringResource(R.string.tts_duration_variation),
                value = settings.vitsDurationNoiseScale,
                displayValue = "%.2f".format(settings.vitsDurationNoiseScale),
                valueRange = 0f..1f,
                steps = 19,
                onValueChange = {
                    onSettingsChange(settings.copy(vitsDurationNoiseScale = it))
                }
            )
        }
        TtsEngineFamily.MATCHA -> Unit
        TtsEngineFamily.ANDROID -> Text(
            text = stringResource(R.string.tts_advanced_not_system),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        text = stringResource(R.string.tts_changes_next_start),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    TextButton(onClick = onReset) {
        Text(stringResource(R.string.tts_reset_settings))
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
        text = stringResource(R.string.tts_setting_value, label, displayValue),
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
        Text(stringResource(R.string.tts_integer_value, label, value), style = MaterialTheme.typography.bodyMedium)
        Row {
            TextButton(
                onClick = { onValueChange(value - 1) },
                enabled = value > range.first
            ) {
                Text(stringResource(R.string.tts_decrease))
            }
            TextButton(
                onClick = { onValueChange(value + 1) },
                enabled = value < range.last
            ) {
                Text(stringResource(R.string.tts_increase))
            }
        }
    }
}
