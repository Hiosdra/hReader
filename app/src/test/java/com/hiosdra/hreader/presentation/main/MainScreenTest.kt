package com.hiosdra.hreader.presentation.main

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import coil3.ImageLoader
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.core.domain.model.ArticleListItem
import com.hiosdra.hreader.presentation.article.ArticleImageDependencies
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = MainScreenTestApplication::class, sdk = [35])
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `empty unread list offers to show all articles`() {
        val viewModel = viewModel(MainUiState(showReadArticles = false))
        setContent(viewModel)
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            context.getString(R.string.main_no_unread_articles)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.main_show_all_from_empty)
        ).performClick()

        verify(exactly = 1) { viewModel.setShowReadArticles(true) }
        verify(exactly = 1) { viewModel.updateSearchQuery("") }
    }

    @Test
    fun `failed initial sync shows retry action`() {
        val retries = AtomicInteger()
        val viewModel = viewModel(
            MainUiState(
                isOnline = false,
                syncState = SyncOperationState.FAILED
            )
        )
        every { viewModel.refreshFromNetwork() } answers { retries.incrementAndGet() }
        setContent(viewModel)
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            context.getString(R.string.main_sync_failed)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()

        assertEquals(1, retries.get())
    }

    private fun viewModel(state: MainUiState): MainViewModel =
        mockk<MainViewModel>(relaxed = true).also {
            every { it.uiState } returns MutableStateFlow(state)
            every { it.articles } returns flowOf(
                PagingData.from(
                    data = emptyList<ArticleListItem>(),
                    sourceLoadStates = LoadStates(
                        refresh = LoadState.NotLoading(endOfPaginationReached = true),
                        prepend = LoadState.NotLoading(endOfPaginationReached = true),
                        append = LoadState.NotLoading(endOfPaginationReached = true)
                    )
                )
            )
        }

    private fun setContent(viewModel: MainViewModel) {
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.setContent {
            HReaderTheme {
                MainScreen(
                    navController = mockk<NavController>(relaxed = true),
                    onOpenSubscriptions = {},
                    viewModel = viewModel,
                    imageDependencies = ArticleImageDependencies(
                        articleImageLoader = mockk<ArticleImageLoader>(relaxed = true),
                        coilImageLoader = ImageLoader.Builder(context).build(),
                        remoteResourcePolicy = mockk<RemoteResourcePolicy>(relaxed = true)
                    )
                )
            }
        }
    }
}

private class MainScreenTestApplication : Application()
