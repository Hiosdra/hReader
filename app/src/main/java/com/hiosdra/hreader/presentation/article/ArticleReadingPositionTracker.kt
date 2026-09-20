package com.hiosdra.hreader.presentation.article

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.sample

@OptIn(FlowPreview::class)
@Composable
internal fun ArticleReadingPositionTracker(
    entryId: Long,
    contentKey: Int,
    effectKey: Any,
    readingPositionLoaded: Boolean,
    savedReadingProgress: Float?,
    positionReady: Boolean,
    currentProgress: () -> Pair<Float, Boolean>,
    restorePosition: suspend (Float) -> Unit,
    onReadingProgressChanged: (Long, Float) -> Unit,
    onReadingCompleted: (Long) -> Unit
) {
    var restoredContentPositionKey by rememberSaveable(entryId) { mutableStateOf<Int?>(null) }
    var readingCompletionReported by rememberSaveable(entryId) { mutableStateOf(false) }
    val latestPositionReady = rememberUpdatedState(positionReady)
    val latestCurrentProgress = rememberUpdatedState(currentProgress)
    val latestReadingPositionLoaded = rememberUpdatedState(readingPositionLoaded)
    val latestRestorePosition = rememberUpdatedState(restorePosition)
    val latestOnReadingProgressChanged = rememberUpdatedState(onReadingProgressChanged)
    val latestOnReadingCompleted = rememberUpdatedState(onReadingCompleted)

    LaunchedEffect(entryId, contentKey, effectKey, readingPositionLoaded, savedReadingProgress, positionReady) {
        if (
            restoredContentPositionKey == contentKey ||
            !readingPositionLoaded ||
            !positionReady
        ) {
            return@LaunchedEffect
        }
        val progress = savedReadingProgress
        if (progress == null) {
            restoredContentPositionKey = contentKey
            return@LaunchedEffect
        }
        latestRestorePosition.value(progress)
        restoredContentPositionKey = contentKey
    }

    LaunchedEffect(entryId, contentKey, effectKey, readingPositionLoaded, positionReady) {
        if (!readingPositionLoaded || !positionReady) return@LaunchedEffect
        readingCompletionReported = false
        snapshotFlow { latestCurrentProgress.value() }
            .filter { (_, ready) -> ready }
            .sample(READING_POSITION_SAMPLE_MILLIS)
            .collect { (progress, _) ->
                if (progress >= READING_POSITION_COMPLETE_THRESHOLD) {
                    if (!readingCompletionReported) {
                        readingCompletionReported = true
                        latestOnReadingCompleted.value(entryId)
                    }
                } else {
                    readingCompletionReported = false
                    latestOnReadingProgressChanged.value(entryId, progress)
                }
            }
    }

    DisposableEffect(entryId, contentKey, effectKey, positionReady) {
        onDispose {
            if (!latestReadingPositionLoaded.value || !latestPositionReady.value) return@onDispose
            val (progress, ready) = latestCurrentProgress.value()
            if (!ready) return@onDispose
            if (progress >= READING_POSITION_COMPLETE_THRESHOLD) {
                latestOnReadingCompleted.value(entryId)
            } else {
                latestOnReadingProgressChanged.value(entryId, progress)
            }
        }
    }
}
