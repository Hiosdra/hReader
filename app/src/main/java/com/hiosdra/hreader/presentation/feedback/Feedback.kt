package com.hiosdra.hreader.presentation.feedback

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals

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
    val supportingText: String? = null,
    val onAction: () -> Unit = {}
)

internal suspend fun SnackbarHostState.showFeedback(request: FeedbackRequest): SnackbarResult {
    val presentation = FeedbackPolicy.presentation(request.kind)
    check(presentation.surface == FeedbackSurface.SNACKBAR)
    val result = showSnackbar(
        visuals = FeedbackSnackbarVisuals(
            request = request,
            duration = requireNotNull(presentation.duration),
            withDismissAction = presentation.withDismissAction
        )
    )
    if (result == SnackbarResult.ActionPerformed) request.onAction()
    return result
}

internal data class FeedbackSnackbarVisuals(
    val request: FeedbackRequest,
    override val duration: SnackbarDuration,
    override val withDismissAction: Boolean
) : SnackbarVisuals {
    override val message: String get() = request.message
    override val actionLabel: String? get() = request.actionLabel
}
