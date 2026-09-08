package com.hiosdra.hreader.presentation.sync

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavController
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.sync.SyncFailureReason
import com.hiosdra.hreader.core.application.sync.SyncFailureStage
import com.hiosdra.hreader.core.application.sync.SyncFreshnessState
import com.hiosdra.hreader.core.application.sync.SyncHealthSnapshot
import com.hiosdra.hreader.core.application.sync.SyncRunState
import com.hiosdra.hreader.core.application.sync.SyncRunSummary
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = SyncHealthScreenTestApplication::class, sdk = [35])
class SyncHealthScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `partial status shows recovery action`() {
        val viewModel = mockk<SyncHealthViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            SyncHealthUiState(
                freshness = SyncFreshnessState.PARTIALLY_SUCCESSFUL,
                snapshot = SyncHealthSnapshot(
                    lastSuccessfulSyncAt = 1_000L,
                    lastAttemptedSyncAt = 2_000L,
                    lastRun = SyncRunSummary(
                        startedAt = 2_000L,
                        completedAt = 2_100L,
                        state = SyncRunState.PARTIALLY_SUCCESSFUL,
                        updatedFeedIds = listOf(1L),
                        failedFeedIds = listOf(2L),
                        newArticles = 4,
                        failureStage = SyncFailureStage.ARTICLE_SYNC,
                        failureReason = SyncFailureReason.NETWORK
                    )
                ),
                isOnline = true,
                now = 2_100L
            )
        )
        val context = RuntimeEnvironment.getApplication()

        composeTestRule.setContent {
            HReaderTheme {
                SyncHealthScreen(
                    navController = mockk<NavController>(relaxed = true),
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            context.getString(R.string.sync_health_status_partial)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.sync_health_sync_now)
        ).performClick()

        verify(exactly = 1) { viewModel.retry() }
    }

    @Test
    fun `offline status shows connection guidance while work is queued`() {
        val viewModel = mockk<SyncHealthViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            SyncHealthUiState(
                freshness = SyncFreshnessState.OFFLINE,
                isOnline = false,
                isSyncing = true
            )
        )
        val context = RuntimeEnvironment.getApplication()

        composeTestRule.setContent {
            HReaderTheme {
                SyncHealthScreen(
                    navController = mockk<NavController>(relaxed = true),
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            context.getString(R.string.sync_health_waiting_for_connection)
        ).assertIsDisplayed()
    }
}

private class SyncHealthScreenTestApplication : Application()
