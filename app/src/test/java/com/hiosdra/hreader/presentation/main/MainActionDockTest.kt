package com.hiosdra.hreader.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.feedback.FeedbackKind
import com.hiosdra.hreader.presentation.feedback.FeedbackRequest
import com.hiosdra.hreader.presentation.feedback.showFeedback
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = MainScreenTestApplication::class, sdk = [35])
class MainActionDockTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `feedback and count changes preserve action bounds and actions`() {
        val host = SnackbarHostState()
        val count = mutableStateOf(12)
        val busy = mutableStateOf(false)
        var marks = 0
        var retries = 0
        lateinit var scope: CoroutineScope
        compose.setContent {
            scope = rememberCoroutineScope()
            HReaderTheme {
                Box(Modifier.fillMaxSize()) {
                    MainActionDock(
                        unreadCount = count.value,
                        busy = busy.value,
                        snackbarHostState = host,
                        onMarkRead = { marks++ },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
        val context = RuntimeEnvironment.getApplication()
        fun action(number: Int) = compose.onNodeWithContentDescription(
            context.resources.getQuantityString(R.plurals.main_mark_articles_read, number, number)
        )
        val initialBounds = action(12).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            scope.launch {
                host.showFeedback(FeedbackRequest(
                    message = "Could not save changes to the server. Try again.",
                    kind = FeedbackKind.RECOVERABLE_ERROR,
                    actionLabel = "Retry operation",
                    onAction = { retries++ }
                ))
            }
        }
        compose.onNodeWithText("Could not save changes to the server. Try again.").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.main_feedback_error)).assertIsDisplayed()
        assertEquals(initialBounds, action(12).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Retry operation").performClick()
        compose.runOnIdle { assertEquals(1, retries); count.value = 128 }
        assertEquals(initialBounds, action(128).fetchSemanticsNode().boundsInRoot)
        action(128).performClick()
        compose.runOnIdle { assertEquals(1, marks); busy.value = true }
        assertEquals(initialBounds, action(128).fetchSemanticsNode().boundsInRoot)
    }
}
