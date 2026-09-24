package com.hiosdra.hreader.presentation.article

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.domain.model.ArticleContentDelivery
import com.hiosdra.hreader.core.domain.model.ArticleContentKind
import com.hiosdra.hreader.core.domain.model.ArticleContentProvenance
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ArticleContentProvenanceStatusTestApplication::class, sdk = [35])
class ArticleContentProvenanceStatusTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `fallback status explains the visible copy and offers recovery`() {
        val context = RuntimeEnvironment.getApplication()
        val retries = AtomicInteger()
        val openedOriginal = AtomicInteger()
        composeTestRule.setContent {
            HReaderTheme {
                ArticleContentProvenanceStatus(
                    provenance = ArticleContentProvenance(
                        kind = ArticleContentKind.FEED_FALLBACK,
                        sourceUrl = "https://example.com/articles/one",
                        fetchedAt = Instant.parse("2026-09-01T12:34:56Z"),
                        delivery = ArticleContentDelivery.LOCAL_STORAGE,
                        isComplete = false
                    ),
                    contentState = ArticleContentLoadState.FALLBACK,
                    onRetry = { retries.incrementAndGet() },
                    onOpenOriginal = { openedOriginal.incrementAndGet() }
                )
            }
        }

        composeTestRule.onNodeWithText(
            context.getString(R.string.article_content_source_feed_fallback)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_content_show_details)
        ).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.article_content_details))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.article_content_detail_host, "example.com")
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(
                R.string.article_content_detail_delivery,
                context.getString(R.string.article_content_delivery_local)
            )
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.article_content_open_original))
            .performClick()
        assertEquals(1, openedOriginal.get())
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_content_show_details)
        ).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.article_content_retry_full))
            .performClick()

        assertEquals(1, retries.get())
    }

    @Test
    fun `saved page status exposes its offline copy and feed switch`() {
        val context = RuntimeEnvironment.getApplication()
        val switchedToFeed = AtomicInteger()
        composeTestRule.setContent {
            HReaderTheme {
                ArticleContentProvenanceStatus(
                    provenance = ArticleContentProvenance(
                        kind = ArticleContentKind.SAVED_WEB_PAGE,
                        sourceUrl = "https://example.com/articles/one",
                        fetchedAt = Instant.parse("2026-09-01T12:34:56Z"),
                        delivery = ArticleContentDelivery.LOCAL_STORAGE,
                        isComplete = false
                    ),
                    onSwitchToFeed = { switchedToFeed.incrementAndGet() }
                )
            }
        }

        composeTestRule.onNodeWithText(
            context.getString(R.string.article_content_source_saved_page)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_content_show_details)
        ).performClick()
        composeTestRule.onNodeWithText(
            context.getString(R.string.article_content_saved_page_incomplete)
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.article_show_feed_content))
            .performClick()

        assertEquals(1, switchedToFeed.get())
    }

    @Test
    fun `unavailable status explains the missing copy`() {
        val context = RuntimeEnvironment.getApplication()
        val retries = AtomicInteger()
        composeTestRule.setContent {
            HReaderTheme {
                ArticleContentProvenanceStatus(
                    provenance = ArticleContentProvenance(
                        kind = ArticleContentKind.UNKNOWN,
                        sourceUrl = "https://example.com/articles/one"
                    ),
                    contentState = ArticleContentLoadState.UNAVAILABLE,
                    onRetry = { retries.incrementAndGet() }
                )
            }
        }

        composeTestRule.onNodeWithText(
            context.getString(R.string.article_content_source_unavailable)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_content_show_details)
        ).performClick()
        composeTestRule.onNodeWithText(
            context.getString(R.string.article_content_source_unavailable_description)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.article_content_retry_full))
            .performClick()

        assertEquals(1, retries.get())
    }
}

internal class ArticleContentProvenanceStatusTestApplication : Application()
