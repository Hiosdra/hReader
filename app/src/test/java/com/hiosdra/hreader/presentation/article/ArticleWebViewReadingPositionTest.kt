package com.hiosdra.hreader.presentation.article

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `restores persisted progress before writing after screen recreation`() {
        val restorationTester = StateRestorationTester(composeTestRule)
        val persistedProgress = AtomicReference(0.5f)
        val writes = mutableListOf<Float>()
        val visibleProgress = AtomicReference(0f)

        restorationTester.setContent {
            var scrollProgress by remember { mutableStateOf(0f) }
            visibleProgress.set(scrollProgress)
            ArticleReadingPositionTracker(
                entryId = 1L,
                contentKey = 1,
                effectKey = 1,
                readingPositionLoaded = true,
                savedReadingProgress = persistedProgress.get(),
                positionReady = true,
                currentProgress = { scrollProgress to true },
                restorePosition = { scrollProgress = it },
                onReadingProgressChanged = { _, progress ->
                    writes.add(progress)
                    persistedProgress.set(progress)
                },
                onReadingCompleted = {}
            )
        }

        composeTestRule.waitForIdle()
        assertEquals(0.5f, visibleProgress.get(), 0.001f)
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(READING_POSITION_SAMPLE_MILLIS + 1)
        composeTestRule.waitForIdle()

        assertEquals(0.5f, visibleProgress.get(), 0.001f)
        assertEquals(0.5f, persistedProgress.get(), 0.001f)
        assertTrue(writes.isNotEmpty())
        assertTrue(writes.all { it == 0.5f })
    }

    @Test
    fun `does not write progress while restoration is suspended`() {
        val restorationAllowed = CompletableDeferred<Unit>()
        val writes = mutableListOf<Float>()
        var scrollProgress by mutableStateOf(0f)
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            ArticleReadingPositionTracker(
                entryId = 1L,
                contentKey = 1,
                effectKey = 1,
                readingPositionLoaded = true,
                savedReadingProgress = 0.5f,
                positionReady = true,
                currentProgress = { scrollProgress to true },
                restorePosition = {
                    restorationAllowed.await()
                    scrollProgress = it
                },
                onReadingProgressChanged = { _, progress -> writes.add(progress) },
                onReadingCompleted = {}
            )
        }

        composeTestRule.mainClock.advanceTimeBy(READING_POSITION_SAMPLE_MILLIS * 2)
        composeTestRule.waitForIdle()
        assertTrue(writes.isEmpty())

        composeTestRule.runOnIdle { restorationAllowed.complete(Unit) }
        composeTestRule.mainClock.advanceTimeBy(READING_POSITION_SAMPLE_MILLIS * 2)
        composeTestRule.waitForIdle()
        assertTrue(writes.isNotEmpty())
        assertTrue(writes.all { it == 0.5f })
    }

    @Test
    fun `does not flush an unrestored position when leaving the screen`() {
        val restorationAllowed = CompletableDeferred<Unit>()
        val writes = mutableListOf<Float>()
        var showReader by mutableStateOf(true)

        composeTestRule.setContent {
            if (showReader) {
                ArticleReadingPositionTracker(
                    entryId = 1L,
                    contentKey = 1,
                    effectKey = 1,
                    readingPositionLoaded = true,
                    savedReadingProgress = 0.5f,
                    positionReady = true,
                    currentProgress = { 0f to true },
                    restorePosition = { restorationAllowed.await() },
                    onReadingProgressChanged = { _, progress -> writes.add(progress) },
                    onReadingCompleted = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { showReader = false }
        composeTestRule.waitForIdle()
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `persists scroll changes after the effect starts`() {
        val persistedProgress = AtomicReference<Float>()
        var scrollY by mutableIntStateOf(0)

        composeTestRule.setContent {
            ArticleReadingPositionTracker(
                entryId = 1L,
                contentKey = 1,
                effectKey = listOf(1, 2_000, 1_000),
                readingPositionLoaded = true,
                savedReadingProgress = null,
                positionReady = true,
                currentProgress = {
                    articleScrollProgress(
                        scrollY,
                        readerWebViewMaxScrollPx(contentHeightPx = 2_000, viewportHeightPx = 1_000)
                    ) to true
                },
                restorePosition = {},
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

internal class ArticleWebViewReadingPositionTestApplication : Application()
