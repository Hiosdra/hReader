package com.hiosdra.hreader.presentation.main

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.hiosdra.hreader.core.domain.model.ArticleListEntry
import com.hiosdra.hreader.core.domain.model.ArticleListItem
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.Feed
import com.hiosdra.hreader.presentation.article.ArticleImageDependencies
import com.hiosdra.hreader.presentation.navigation.Routes
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
        val viewModel = viewModel(MainUiState(showReadArticles = false, readCount = 1))
        setContent(viewModel)
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            context.getString(R.string.main_all_caught_up)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.main_show_all_from_empty)
        ).performClick()

        verify(exactly = 1) { viewModel.setShowReadArticles(true) }
        verify(exactly = 1) { viewModel.updateSearchQuery("") }
    }

    @Test
    fun `completed sync does not show first sync state`() {
        val viewModel = viewModel(
            MainUiState(
                readCount = 1,
                syncState = SyncOperationState.RUNNING,
                hasCompletedSync = true
            )
        )
        setContent(viewModel)
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            context.getString(R.string.main_all_caught_up)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.notification_sync_text)
        ).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(
            context.getString(R.string.main_syncing_first_time)
        ).assertCountEquals(0)
    }

    @Test
    fun `running sync before first success shows first sync state`() {
        val viewModel = viewModel(MainUiState(syncState = SyncOperationState.RUNNING))
        setContent(viewModel)
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            context.getString(R.string.main_syncing_first_time)
        ).assertIsDisplayed()
    }

    @Test
    fun `failed sync shows retry action`() {
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
            context.getString(R.string.error_refresh_articles)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()

        assertEquals(1, retries.get())
    }

    @Test
    fun `offline banner can be dismissed without changing offline state`() {
        val viewModel = viewModel(MainUiState(isOnline = false))
        setContent(viewModel)
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            context.getString(R.string.main_offline_banner)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.action_dismiss)
        ).performClick()

        composeTestRule.onAllNodesWithText(
            context.getString(R.string.main_offline_banner)
        ).assertCountEquals(0)
    }

    @Test
    fun `article click uses requested feed while query update is pending`() {
        val articleId = 42L
        val oldQuery = ArticleListQuery(
            feedId = 1L,
            sessionStart = Instant.ofEpochMilli(123L)
        )
        val viewModel = viewModel(
            state = MainUiState(),
            articles = PagingData.from(
                listOf(
                    ArticleListItem.Article(
                        ArticleListEntry(
                            id = articleId,
                            title = "Probe article",
                            preview = null,
                            author = null,
                            publishedAt = Instant.ofEpochSecond(articleId),
                            feed = Feed(1L, "Feed", null, "https://example.com/feed"),
                            imageUrl = null
                        )
                    )
                )
            )
        )
        every { viewModel.currentQuery() } returns oldQuery
        val navController = mockk<NavController>(relaxed = true)
        setContent(viewModel, feedId = 2L, navController = navController)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Probe article").performClick()

        val route = io.mockk.slot<String>()
        verify(exactly = 1) { navController.navigate(capture(route)) }
        assertTrue(route.captured.startsWith("article?feedId=2&startId=42&includeRead=false&session="))
        assertNotEquals(
            Routes.article(
                feedId = oldQuery.feedId,
                startArticleId = articleId,
                includeRead = oldQuery.includeRead,
                sessionStartMillis = oldQuery.sessionStart.toEpochMilli()
            ),
            route.captured
        )
    }

    private fun viewModel(
        state: MainUiState,
        articles: PagingData<ArticleListItem> = PagingData.from(
            data = emptyList(),
            sourceLoadStates = LoadStates(
                refresh = LoadState.NotLoading(endOfPaginationReached = true),
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                append = LoadState.NotLoading(endOfPaginationReached = true)
            )
        )
    ): MainViewModel =
        mockk<MainViewModel>(relaxed = true).also {
            every { it.uiState } returns MutableStateFlow(state)
            every { it.articles } returns flowOf(articles)
        }

    private fun setContent(
        viewModel: MainViewModel,
        feedId: Long? = null,
        navController: NavController = mockk(relaxed = true)
    ) {
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.setContent {
            HReaderTheme {
                MainScreen(
                    navController = navController,
                    onOpenSubscriptions = {},
                    feedId = feedId,
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

internal class MainScreenTestApplication : Application()
