package com.hiosdra.hreader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.hiosdra.hreader.data.tts.TtsModel
import com.hiosdra.hreader.data.tts.TtsModelManager
import com.hiosdra.hreader.data.tts.TtsModelStatus
import com.hiosdra.hreader.ui.theme.sectionCardColors
import kotlinx.coroutines.launch

@Composable
internal fun TtsSettingsSection(
    preferences: PreferencesManager,
    modelManager: TtsModelManager
) {
    val statuses by modelManager.statuses.collectAsState()
    val scope = rememberCoroutineScope()
    var speed by remember { mutableFloatStateOf(preferences.getTtsSpeed()) }
    var selectedModel by remember { mutableStateOf(preferences.getTtsModel()) }

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
            Text(
                text = "If a neural model is missing or cannot start, hReader uses Android TTS automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
