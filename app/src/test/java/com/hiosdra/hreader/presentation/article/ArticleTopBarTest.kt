package com.hiosdra.hreader.presentation.article

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hiosdra.hreader.core.application.port.out.ArticleTtsState
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
import com.hiosdra.hreader.R
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
@Config(application = ArticleTopBarTestApplication::class, sdk = [35])
class ArticleTopBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `top bar displays feed identity and position`() {
        val context = RuntimeEnvironment.getApplication()
        setContent()

        composeTestRule.onNodeWithText("Inbox").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.article_position, 2, 10))
            .assertIsDisplayed()
    }

    @Test
    fun `offline web mode is exposed as disabled`() {
        val context = RuntimeEnvironment.getApplication()
        setContent(canUseWebView = false)

        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_web_unavailable_offline)
        ).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun `available web mode invokes the toggle action`() {
        val toggles = AtomicInteger()
        setContent(
            canUseWebView = true,
            onToggleWebView = { toggles.incrementAndGet() }
        )

        composeTestRule.onNodeWithContentDescription(
            RuntimeEnvironment.getApplication().getString(R.string.article_show_original_web_page)
        ).performClick()

        assertEquals(1, toggles.get())
    }

    @Test
    fun `overflow menu exposes text size action`() {
        val increases = AtomicInteger()
        val context = RuntimeEnvironment.getApplication()
        setContent(onIncreaseTextScale = { increases.incrementAndGet() })

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_more))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.article_increase_text_size))
            .performClick()

        assertEquals(1, increases.get())
    }

    @Test
    fun `overflow menu exposes read aloud action`() {
        val starts = AtomicInteger()
        val context = RuntimeEnvironment.getApplication()
        setContent(
            ttsContentState = ArticleTtsContentState.AVAILABLE,
            onToggleSpeech = { starts.incrementAndGet() }
        )

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_more))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.article_read_aloud))
            .performClick()

        assertEquals(1, starts.get())
    }

    @Test
    fun `overflow menu keeps read aloud disabled without article text`() {
        val retries = AtomicInteger()
        val context = RuntimeEnvironment.getApplication()
        setContent(
            ttsContentState = ArticleTtsContentState.UNAVAILABLE,
            onRetryContent = { retries.incrementAndGet() }
        )

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_more))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.article_read_aloud_unavailable))
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText(context.getString(R.string.action_retry))
            .performClick()

        assertEquals(1, retries.get())
    }

    @Test
    fun `overflow menu allows selecting a temporary voice`() {
        val selected = AtomicReference<TtsModel?>()
        val context = RuntimeEnvironment.getApplication()
        val systemVoice = context.getString(R.string.tts_model_android_name)
        setContent(
            ttsContentState = ArticleTtsContentState.AVAILABLE,
            ttsModelStatuses = mapOf(TtsModel.ANDROID to TtsModelStatus.Available),
            onTemporaryTtsModelChange = { selected.set(it) }
        )

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_more))
            .performClick()
        composeTestRule.onNodeWithText(
            context.getString(R.string.article_tts_model, systemVoice)
        ).performClick()
        composeTestRule.onNodeWithText(systemVoice).performClick()

        assertEquals(TtsModel.ANDROID, selected.get())
    }

    private fun setContent(
        canUseWebView: Boolean = false,
        onToggleWebView: () -> Unit = {},
        onIncreaseTextScale: () -> Unit = {},
        ttsContentState: ArticleTtsContentState? = null,
        onToggleSpeech: () -> Unit = {},
        ttsState: ArticleTtsState = ArticleTtsState(),
        ttsModelStatuses: Map<TtsModel, TtsModelStatus> = emptyMap(),
        onTemporaryTtsModelChange: (TtsModel?) -> Unit = {},
        onRetryContent: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            HReaderTheme {
                ArticleTopBar(
                    entryUrl = "https://example.com/article",
                    feedTitle = "Inbox",
                    listPosition = 2,
                    listSize = 10,
                    isWebViewMode = false,
                    canUseWebView = canUseWebView,
                    isRead = false,
                    textScale = 1f,
                    onDecreaseTextScale = {},
                    onResetTextScale = {},
                    onIncreaseTextScale = onIncreaseTextScale,
                    onToggleRead = {},
                    onBack = {},
                    onToggleWebView = onToggleWebView,
                    onShare = {},
                    ttsState = ttsState,
                    ttsModelStatuses = ttsModelStatuses,
                    ttsContentState = ttsContentState,
                    onTemporaryTtsModelChange = onTemporaryTtsModelChange,
                    onToggleSpeech = onToggleSpeech,
                    onRetryContent = onRetryContent
                )
            }
        }
    }
}

private class ArticleTopBarTestApplication : Application()
