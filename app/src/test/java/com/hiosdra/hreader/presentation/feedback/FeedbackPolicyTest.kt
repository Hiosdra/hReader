package com.hiosdra.hreader.presentation.feedback

import androidx.compose.material3.SnackbarDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackPolicyTest {

    @Test
    fun `confirmations use short dismissible snackbars`() {
        val presentation = FeedbackPolicy.presentation(FeedbackKind.CONFIRMATION)

        assertEquals(FeedbackSurface.SNACKBAR, presentation.surface)
        assertEquals(SnackbarDuration.Short, presentation.duration)
        assertTrue(presentation.withDismissAction)
    }

    @Test
    fun `recoverable failures use long dismissible snackbars`() {
        val presentation = FeedbackPolicy.presentation(FeedbackKind.RECOVERABLE_ERROR)

        assertEquals(FeedbackSurface.SNACKBAR, presentation.surface)
        assertEquals(SnackbarDuration.Long, presentation.duration)
        assertTrue(presentation.withDismissAction)
    }

    @Test
    fun `persistent and destructive states stay on their dedicated surfaces`() {
        assertEquals(
            FeedbackSurface.INLINE,
            FeedbackPolicy.presentation(FeedbackKind.PERSISTENT_STATE).surface
        )
        assertEquals(
            FeedbackSurface.DIALOG,
            FeedbackPolicy.presentation(FeedbackKind.DESTRUCTIVE_ACTION).surface
        )
    }
}
