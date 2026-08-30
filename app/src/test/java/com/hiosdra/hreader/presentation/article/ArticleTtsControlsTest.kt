package com.hiosdra.hreader.presentation.article

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.ArticleTtsState
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ArticleTtsControlsTestApplication::class, sdk = [35])
class ArticleTtsControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `mini player opens and exposes playback actions`() {
        val opens = AtomicInteger()
        val pauses = AtomicInteger()
        val stops = AtomicInteger()
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.setContent {
            HReaderTheme {
                ArticleTtsMiniPlayer(
                    state = ArticleTtsState(
                        articleId = 42L,
                        title = "Article title",
                        isPlaying = true,
                        totalChunks = 3,
                        currentChunk = 1
                    ),
                    onOpen = { opens.incrementAndGet() },
                    onPause = { pauses.incrementAndGet() },
                    onResume = {},
                    onStop = { stops.incrementAndGet() }
                )
            }
        }

        composeTestRule.onNodeWithText("Article title").performClick()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.article_pause))
            .performClick()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.article_stop_reading))
            .performClick()

        assertEquals(1, opens.get())
        assertEquals(1, pauses.get())
        assertEquals(1, stops.get())
    }

    @Test
    fun `player sheet shows controls and selected voice`() {
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.setContent {
            HReaderTheme {
                ArticleTtsPlayerSheet(
                    state = ArticleTtsState(
                        articleId = 42L,
                        title = "Article title",
                        model = TtsModel.ANDROID,
                        isPlaying = true,
                        totalChunks = 3,
                        currentChunk = 1
                    ),
                    temporaryModel = null,
                    configuredModel = TtsModel.ANDROID,
                    modelStatuses = emptyMap(),
                    contentState = ArticleTtsContentState.AVAILABLE,
                    onTemporaryModelChange = {},
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onRetry = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.article_pause))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.article_stop_reading))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(
                R.string.article_tts_model,
                context.getString(R.string.tts_model_android_name)
            )
        ).assertIsDisplayed()
    }

    @Test
    fun `player sheet lets user select a temporary voice`() {
        val selected = AtomicReference<TtsModel?>()
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.setContent {
            HReaderTheme {
                ArticleTtsPlayerSheet(
                    state = ArticleTtsState(articleId = 42L, isPlaying = true),
                    temporaryModel = null,
                    configuredModel = TtsModel.ANDROID,
                    modelStatuses = mapOf(TtsModel.ANDROID to TtsModelStatus.Available),
                    contentState = ArticleTtsContentState.AVAILABLE,
                    onTemporaryModelChange = { selected.set(it) },
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onRetry = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText(
            context.getString(
                R.string.article_tts_model,
                context.getString(R.string.tts_model_android_name)
            )
        ).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.tts_model_android_name))
            .performClick()

        assertEquals(TtsModel.ANDROID, selected.get())
    }

    @Test
    fun `player sheet exposes retry after playback error`() {
        val retries = AtomicInteger()
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.setContent {
            HReaderTheme {
                ArticleTtsPlayerSheet(
                    state = ArticleTtsState(
                        articleId = 42L,
                        error = "Playback failed"
                    ),
                    temporaryModel = null,
                    configuredModel = TtsModel.ANDROID,
                    modelStatuses = emptyMap(),
                    contentState = ArticleTtsContentState.AVAILABLE,
                    onTemporaryModelChange = {},
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onRetry = { retries.incrementAndGet() },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()

        assertEquals(1, retries.get())
    }
}

private class ArticleTtsControlsTestApplication : Application()
