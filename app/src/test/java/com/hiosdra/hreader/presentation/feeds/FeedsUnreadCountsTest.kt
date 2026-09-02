package com.hiosdra.hreader.presentation.feeds

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiosdra.hreader.core.application.port.out.FeedStore
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.application.usecase.feeds.FeedUseCase
import com.hiosdra.hreader.core.domain.model.Feed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = FeedsUnreadCountsTestApplication::class, sdk = [35])
class FeedsUnreadCountsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `view-model unread count follows local changes while the drawer stays open`() {
        val localCounts = MutableStateFlow(mapOf(7L to 4))
        val feed = Feed(
            id = 7L,
            title = "Example feed",
            siteUrl = "https://example.com",
            feedUrl = "https://example.com/feed"
        )
        val feedStore = mockk<FeedStore>(relaxed = true)
        every { feedStore.observeUnreadCounts() } returns localCounts
        coEvery { feedStore.getCachedFeeds() } returns listOf(feed)
        coEvery { feedStore.getCachedUnreadCounts() } returns mapOf(7L to 4)
        coEvery { feedStore.getUnreadCounts() } returns mapOf(7L to 58)
        coEvery { feedStore.refreshFeeds() } returns listOf(feed)
        val network = object : NetworkStatus {
            override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
        }
        val viewModel = FeedsViewModel(FeedUseCase(feedStore, network))

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            Text((state.unreadCounts[7L] ?: 0).toString())
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("4").assertIsDisplayed()
        coVerify(exactly = 0) { feedStore.getUnreadCounts() }

        localCounts.value = emptyMap()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("0").assertIsDisplayed()
    }
}

private class FeedsUnreadCountsTestApplication : Application()
