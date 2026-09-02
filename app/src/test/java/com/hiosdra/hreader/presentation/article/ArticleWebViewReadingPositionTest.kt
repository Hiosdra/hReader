package com.hiosdra.hreader.presentation.article

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ArticleWebViewReadingPositionTestApplication::class, sdk = [35])
class ArticleWebViewReadingPositionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `persists scroll changes after the effect starts`() {
        val persistedProgress = AtomicReference<Float>()
        var scrollY by mutableIntStateOf(0)

        composeTestRule.setContent {
            ArticleWebViewReadingPosition(
                entryId = 1L,
                contentKey = 1,
                readingPositionLoaded = true,
                savedReadingProgress = null,
                scrollY = scrollY,
                contentHeightPx = 2_000,
                viewportHeightPx = 1_000,
                contentHeightSettled = true,
                webViewReady = true,
                onRestoreScrollY = {},
                onReadingProgressChanged = { _, progress -> persistedProgress.set(progress) },
                onReadingCompleted = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { scrollY = 500 }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(READING_POSITION_SAMPLE_MILLIS + 1)
        composeTestRule.waitForIdle()

        assertEquals(0.5f, persistedProgress.get(), 0.001f)
    }
}

private class ArticleWebViewReadingPositionTestApplication : Application()
