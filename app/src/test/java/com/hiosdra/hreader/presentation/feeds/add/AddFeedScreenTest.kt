package com.hiosdra.hreader.presentation.feeds.add

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavController
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.FeedStore
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.application.usecase.feeds.FeedUseCase
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import io.mockk.coEvery
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = AddFeedScreenTestApplication::class, sdk = [35])
class AddFeedScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shared initial url is visible in the form`() {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = createViewModel()

        setContent(viewModel, initialUrl = "https://example.com/feed")

        composeTestRule.onNodeWithText("https://example.com/feed").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_subscribe)).assertIsDisplayed()
    }

    @Test
    fun `submitting a valid url calls both completion callbacks`() {
        val feedStore = mockk<FeedStore>(relaxed = true)
        coEvery { feedStore.getCachedFeeds() } returns emptyList()
        val viewModel = createViewModel(feedStore)
        val added = AtomicInteger()
        val navigatedBack = AtomicInteger()
        val context = RuntimeEnvironment.getApplication()

        setContent(
            viewModel = viewModel,
            initialUrl = "https://example.com/feed",
            onFeedAdded = { added.incrementAndGet() },
            onNavigateBack = { navigatedBack.incrementAndGet() }
        )
        composeTestRule.onNodeWithText(context.getString(R.string.action_subscribe)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, added.get())
        assertEquals(1, navigatedBack.get())
    }

    private fun createViewModel(feedStore: FeedStore = mockk(relaxed = true)): AddFeedViewModel {
        val network = object : NetworkStatus {
            override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
        }
        return AddFeedViewModel(FeedUseCase(feedStore, network))
    }

    private fun setContent(
        viewModel: AddFeedViewModel,
        initialUrl: String,
        onFeedAdded: () -> Unit = {},
        onNavigateBack: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            HReaderTheme {
                AddFeedScreen(
                    navController = mockk<NavController>(relaxed = true),
                    initialUrl = initialUrl,
                    addFeedViewModel = viewModel,
                    onFeedAdded = onFeedAdded,
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

private class AddFeedScreenTestApplication : Application()
