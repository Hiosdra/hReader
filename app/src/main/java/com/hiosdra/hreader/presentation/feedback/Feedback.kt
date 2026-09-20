package com.hiosdra.hreader.presentation.feedback

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

internal enum class FeedbackSurface {
    INLINE,
    SNACKBAR,
    DIALOG
}

internal enum class FeedbackKind {
    CONFIRMATION,
    UNDO,
    PROGRESS,
    RECOVERABLE_ERROR,
    PERSISTENT_STATE,
    DESTRUCTIVE_ACTION
}

internal data class FeedbackPresentation(
    val surface: FeedbackSurface,
    val duration: SnackbarDuration? = null,
    val withDismissAction: Boolean = false
)

internal object FeedbackPolicy {
    fun presentation(kind: FeedbackKind): FeedbackPresentation = when (kind) {
        FeedbackKind.CONFIRMATION -> FeedbackPresentation(
            surface = FeedbackSurface.SNACKBAR,
            duration = SnackbarDuration.Short,
            withDismissAction = true
        )
        FeedbackKind.UNDO -> FeedbackPresentation(
            surface = FeedbackSurface.SNACKBAR,
            duration = SnackbarDuration.Long,
            withDismissAction = true
        )
        FeedbackKind.PROGRESS -> FeedbackPresentation(surface = FeedbackSurface.INLINE)
        FeedbackKind.RECOVERABLE_ERROR -> FeedbackPresentation(
            surface = FeedbackSurface.SNACKBAR,
            duration = SnackbarDuration.Long,
            withDismissAction = true
        )
        FeedbackKind.PERSISTENT_STATE -> FeedbackPresentation(
            surface = FeedbackSurface.INLINE,
            withDismissAction = true
        )
        FeedbackKind.DESTRUCTIVE_ACTION -> FeedbackPresentation(
            surface = FeedbackSurface.DIALOG,
            withDismissAction = true
        )
    }
}

internal data class FeedbackRequest(
    val message: String,
    val kind: FeedbackKind = FeedbackKind.CONFIRMATION,
    val actionLabel: String? = null,
    val onAction: () -> Unit = {}
)

internal suspend fun SnackbarHostState.showFeedback(request: FeedbackRequest): SnackbarResult {
    val presentation = FeedbackPolicy.presentation(request.kind)
    check(presentation.surface == FeedbackSurface.SNACKBAR)
    val result = showSnackbar(
        message = request.message,
        actionLabel = request.actionLabel,
        withDismissAction = presentation.withDismissAction,
        duration = requireNotNull(presentation.duration)
    )
    if (result == SnackbarResult.ActionPerformed) request.onAction()
    return result
}
