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
internal fun ArticleWebViewReadingPosition(
    entryId: Long,
    contentKey: Int,
    readingPositionLoaded: Boolean,
    savedReadingProgress: Float?,
    scrollY: Int,
    contentHeightPx: Int,
    viewportHeightPx: Int,
    contentHeightSettled: Boolean,
    webViewReady: Boolean,
    onRestoreScrollY: (Int) -> Unit,
    onReadingProgressChanged: (Long, Float) -> Unit,
    onReadingCompleted: (Long) -> Unit
) {
    var restoredContentPositionKey by rememberSaveable(entryId) { mutableStateOf<Int?>(null) }
    var readingCompletionReported by rememberSaveable(entryId) { mutableStateOf(false) }
    val latestOnRestoreScrollY = rememberUpdatedState(onRestoreScrollY)
    val latestReadingPositionLoaded = rememberUpdatedState(readingPositionLoaded)
    val latestScrollY = rememberUpdatedState(scrollY)
    val latestContentHeightPx = rememberUpdatedState(contentHeightPx)
    val latestViewportHeightPx = rememberUpdatedState(viewportHeightPx)
    val latestContentHeightSettled = rememberUpdatedState(contentHeightSettled)
    val latestWebViewReady = rememberUpdatedState(webViewReady)
    val latestOnReadingProgressChanged = rememberUpdatedState(onReadingProgressChanged)
    val latestOnReadingCompleted = rememberUpdatedState(onReadingCompleted)

    LaunchedEffect(
        entryId,
        contentKey,
        readingPositionLoaded,
        savedReadingProgress,
        contentHeightPx,
        viewportHeightPx,
        contentHeightSettled,
        webViewReady
    ) {
        if (
            restoredContentPositionKey == contentKey ||
            !readingPositionLoaded ||
            !webViewReady ||
            contentHeightPx <= 0 ||
            viewportHeightPx <= 0 ||
            !contentHeightSettled
        ) {
            return@LaunchedEffect
        }
        val maxScrollPx = readerWebViewMaxScrollPx(contentHeightPx, viewportHeightPx)
        val progress = savedReadingProgress
        if (progress == null || maxScrollPx == 0) {
            restoredContentPositionKey = contentKey
            return@LaunchedEffect
        }

        latestOnRestoreScrollY.value(articleScrollOffset(progress, maxScrollPx))
        restoredContentPositionKey = contentKey
    }

    LaunchedEffect(
        entryId,
        contentKey,
        readingPositionLoaded,
        contentHeightSettled,
        webViewReady,
        readerWebViewMaxScrollPx(contentHeightPx, viewportHeightPx)
    ) {
        if (!readingPositionLoaded || !contentHeightSettled || !webViewReady) {
            return@LaunchedEffect
        }
        readingCompletionReported = false
        snapshotFlow {
            val maxScrollPx = readerWebViewMaxScrollPx(
                latestContentHeightPx.value,
                latestViewportHeightPx.value
            )
            articleScrollProgress(latestScrollY.value, maxScrollPx) to (maxScrollPx > 0)
        }
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

    DisposableEffect(entryId, contentKey, contentHeightSettled, webViewReady) {
        onDispose {
            if (
                !latestReadingPositionLoaded.value ||
                !latestContentHeightSettled.value ||
                !latestWebViewReady.value
            ) {
                return@onDispose
            }
            val maxScrollPx = readerWebViewMaxScrollPx(
                latestContentHeightPx.value,
                latestViewportHeightPx.value
            )
            if (maxScrollPx <= 0) return@onDispose
            val progress = articleScrollProgress(latestScrollY.value, maxScrollPx)
            if (progress >= READING_POSITION_COMPLETE_THRESHOLD) {
                latestOnReadingCompleted.value(entryId)
            } else {
                latestOnReadingProgressChanged.value(entryId, progress)
            }
        }
    }
}
