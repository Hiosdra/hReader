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
        val viewModel = FeedsViewModel(FeedUseCase(feedStore, network), MutableStateFlow(false))

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

    @Test
    fun `sync order is existing unread then fresh unread then read and resets next sync`() {
        val localCounts = MutableStateFlow(mapOf(1L to 5, 2L to 2))
        val syncActivity = MutableStateFlow(false)
        val subscriptions = listOf(
            Feed(1L, "Z Existing", null, "https://existing-z.example.com/feed"),
            Feed(2L, "A Existing", null, "https://existing-a.example.com/feed"),
            Feed(3L, "Z Fresh", null, "https://fresh-z.example.com/feed"),
            Feed(4L, "B Fresh Next", null, "https://fresh-next.example.com/feed"),
            Feed(5L, "A Read", null, "https://read.example.com/feed")
        )
        val feedStore = mockk<FeedStore>(relaxed = true)
        every { feedStore.observeUnreadCounts() } returns localCounts
        coEvery { feedStore.getCachedFeeds() } returns subscriptions
        coEvery { feedStore.getCachedUnreadCounts() } returns localCounts.value
        coEvery { feedStore.refreshFeeds() } returns subscriptions
        val network = object : NetworkStatus {
            override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
        }
        val viewModel = FeedsViewModel(FeedUseCase(feedStore, network), syncActivity)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            Text(state.feeds.joinToString(",") { it.id.toString() })
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1,2,5,4,3").assertIsDisplayed()

        syncActivity.value = true
        composeTestRule.waitForIdle()
        localCounts.value = mapOf(1L to 1, 2L to 50, 3L to 80)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1,2,3,5,4").assertIsDisplayed()

        syncActivity.value = false
        composeTestRule.waitForIdle()
        localCounts.value = mapOf(1L to 1, 2L to 60, 3L to 100)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1,2,3,5,4").assertIsDisplayed()

        syncActivity.value = true
        composeTestRule.waitForIdle()
        localCounts.value = mapOf(1L to 1, 2L to 60, 3L to 100, 4L to 7)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1,2,3,4,5").assertIsDisplayed()
    }
}

private class FeedsUnreadCountsTestApplication : Application()
