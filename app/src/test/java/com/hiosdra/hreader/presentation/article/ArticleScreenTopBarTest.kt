package com.hiosdra.hreader.presentation.article

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.domain.model.ArticleContentKind
import com.hiosdra.hreader.core.domain.model.ArticleContentProvenance
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ArticleTopBarTestApplication::class, sdk = [35])
class ArticleScreenTopBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `web mode shows Chrome action beside provenance and opens the original`() {
        val context = RuntimeEnvironment.getApplication()
        val opens = AtomicInteger()
        setContent(onOpenInChrome = { opens.incrementAndGet() })

        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_content_show_details)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_open_original_in_chrome)
        ).assertIsDisplayed().performClick()

        assertEquals(1, opens.get())
    }

    @Test
    fun `Chrome action is disabled offline in web mode`() {
        val context = RuntimeEnvironment.getApplication()
        setContent(isOnline = false)

        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_open_original_in_chrome)
        ).assertIsNotEnabled()
    }

    @Test
    fun `feed mode does not show Chrome action in provenance row`() {
        val context = RuntimeEnvironment.getApplication()
        setContent(isWebViewMode = false)

        val chromeActions = composeTestRule
            .onAllNodesWithContentDescription(context.getString(R.string.article_open_original_in_chrome))
            .fetchSemanticsNodes()
        assertEquals(0, chromeActions.size)
    }

    private fun setContent(
        isWebViewMode: Boolean = true,
        isOnline: Boolean = true,
        onOpenInChrome: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            HReaderTheme {
                ArticleScreenTopBar(
                    state = ArticleScreenTopBarState(
                        entryUrl = "https://example.com/article",
                        feedTitle = "Inbox",
                        listPosition = 1,
                        listSize = 1,
                        isWebViewMode = isWebViewMode,
                        canUseWebView = true,
                        isRead = false,
                        textScale = 1f,
                        ttsContentState = null,
                        isTtsActive = false,
                        isOnline = isOnline,
                        defaultPaywallBypassMethod = null,
                        canUsePaywallBypass = false,
                        displayedProvenance = ArticleContentProvenance(
                            kind = ArticleContentKind.EXTERNAL_WEB_PAGE,
                            sourceUrl = "https://example.com/article"
                        )
                    ),
                    actions = ArticleScreenTopBarActions(
                        onDecreaseTextScale = {},
                        onResetTextScale = {},
                        onIncreaseTextScale = {},
                        onToggleRead = {},
                        onBack = {},
                        onToggleWebView = {},
                        onShare = {},
                        onOpenInChrome = onOpenInChrome,
                        onInvokeTts = {},
                        onBypassPaywall = {},
                        onOpenPaywallMethodPicker = {},
                        onSwitchToFeed = {}
                    )
                )
            }
        }
    }
}
