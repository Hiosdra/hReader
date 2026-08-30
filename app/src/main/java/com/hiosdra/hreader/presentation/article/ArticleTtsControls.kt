package com.hiosdra.hreader.presentation.article

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.ArticleTtsState
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
import com.hiosdra.hreader.presentation.theme.MotionDuration

@Composable
internal fun ArticleTtsMiniPlayer(
    state: ArticleTtsState,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onSizeChanged: (Int) -> Unit = {}
) {
    val playbackAction = stringResource(
        if (state.isPaused) R.string.article_resume else R.string.article_pause
    )
    val openPlayerAction = stringResource(R.string.article_open_tts_player)
    val stopAction = stringResource(R.string.article_stop_reading)
    val playbackEnabled = state.isPaused || state.isPlaying
    AnimatedVisibility(
        visible = state.articleId != null,
        modifier = modifier.fillMaxWidth(),
        enter = slideInVertically(
            animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD)),
            initialOffsetY = { it / 2 }
        ) + fadeIn(animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))),
        exit = slideOutVertically(
            animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT)),
            targetOffsetY = { it / 2 }
        ) + fadeOut(animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .onSizeChanged { onSizeChanged(it.height) }
                .clickable(onClickLabel = openPlayerAction, onClick = onOpen)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.title.ifBlank {
                                    stringResource(R.string.article_read_aloud)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            ArticleTtsStatusText(state = state, maxLines = 1)
                        }
                        IconButton(
                            onClick = if (state.isPaused) onResume else onPause,
                            enabled = playbackEnabled,
                            modifier = Modifier.semantics {
                                contentDescription = playbackAction
                            }
                        ) {
                            if (state.isPaused) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            } else {
                                Text("Ⅱ")
                            }
                        }
                        IconButton(
                            onClick = onStop,
                            modifier = Modifier.semantics {
                                contentDescription = stopAction
                            }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArticleTtsPlayerSheet(
    state: ArticleTtsState,
    temporaryModel: TtsModel?,
    configuredModel: TtsModel,
    modelStatuses: Map<TtsModel, TtsModelStatus>,
    contentState: ArticleTtsContentState?,
    onTemporaryModelChange: (TtsModel?) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val playbackAction = stringResource(
        if (state.isPaused) R.string.article_resume else R.string.article_pause
    )
    val playbackEnabled = state.isPaused || state.isPlaying
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.article_read_aloud),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = state.title.ifBlank { stringResource(R.string.article_read_aloud) },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            ArticleTtsStatusText(
                state = state,
                maxLines = 2,
                color = if (state.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.article_stop_reading))
                }
                Button(
                    onClick = if (state.isPaused) onResume else onPause,
                    enabled = playbackEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = playbackAction }
                ) {
                    Text(playbackAction)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            ArticleTtsModelSelector(
                temporaryModel = temporaryModel,
                configuredModel = configuredModel,
                modelStatuses = modelStatuses,
                onTemporaryModelChange = onTemporaryModelChange
            )
            if (!state.isPreparing && state.error != null) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_retry))
                }
            } else if (contentState == ArticleTtsContentState.LOADING) {
                Text(
                    text = stringResource(R.string.article_missing_content),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (contentState == ArticleTtsContentState.UNAVAILABLE) {
                Text(
                    text = stringResource(R.string.article_read_aloud_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

@Composable
private fun ArticleTtsStatusText(
    state: ArticleTtsState,
    maxLines: Int,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val status = when {
        state.error != null -> state.error
        state.isPreparing -> stringResource(R.string.article_preparing_voice)
        state.totalChunks > 0 -> stringResource(
            R.string.tts_chunk_progress,
            (state.currentChunk + 1).coerceAtMost(state.totalChunks),
            state.totalChunks
        )
        else -> stringResource(R.string.article_read_aloud)
    }
    Text(
        text = status,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ArticleTtsModelSelector(
    temporaryModel: TtsModel?,
    configuredModel: TtsModel,
    modelStatuses: Map<TtsModel, TtsModelStatus>,
    onTemporaryModelChange: (TtsModel?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = temporaryModel ?: configuredModel
    val availableModels = TtsModelCatalog.models.filter {
        modelStatuses[it] == TtsModelStatus.Available
    }
    val selectedModelName = stringResource(selectedModel.displayNameRes)
    Box(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.article_tts_model, selectedModelName))
            },
            supportingContent = if (temporaryModel != null) {
                {
                    Text(
                        stringResource(
                            R.string.article_tts_model_use_settings,
                            stringResource(configuredModel.displayNameRes)
                        )
                    )
                }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val settingsLabel = stringResource(
                R.string.article_tts_model_use_settings,
                stringResource(configuredModel.displayNameRes)
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (temporaryModel == null) {
                            stringResource(R.string.tts_voice_selected, settingsLabel)
                        } else {
                            settingsLabel
                        }
                    )
                },
                onClick = {
                    onTemporaryModelChange(null)
                    expanded = false
                }
            )
            if (availableModels.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            availableModels.forEach { model ->
                val modelName = stringResource(model.displayNameRes)
                DropdownMenuItem(
                    text = {
                        Text(
                            if (temporaryModel == model) {
                                stringResource(R.string.tts_voice_selected, modelName)
                            } else {
                                modelName
                            }
                        )
                    },
                    onClick = {
                        onTemporaryModelChange(model)
                        expanded = false
                    }
                )
            }
        }
    }
}
