package com.hiosdra.hreader.presentation.article

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ArticleSourceActionsTestApplication::class, sdk = [35])
class ArticleSourceActionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `main action opens through configured service`() {
        val selected = AtomicReference<PaywallBypassMethod>()
        val context = RuntimeEnvironment.getApplication()
        setContent(
            defaultMethod = PaywallBypassMethod.SMRY_AI,
            onBypassPaywall = { selected.set(it) }
        )

        composeTestRule.onNodeWithText(
            context.getString(
                R.string.article_open_through_paywall_service,
                context.getString(R.string.paywall_smry_ai)
            )
        ).performClick()

        assertEquals(PaywallBypassMethod.SMRY_AI, selected.get())
    }

    @Test
    fun `arrow opens service menu and selection is passed as an override`() {
        val selected = AtomicReference<PaywallBypassMethod>()
        val context = RuntimeEnvironment.getApplication()
        setContent(
            defaultMethod = PaywallBypassMethod.SMRY_AI,
            onBypassPaywall = { selected.set(it) }
        )

        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_choose_paywall_service)
        ).performClick()
        composeTestRule.onNodeWithText(
            context.getString(R.string.paywall_wayback_machine)
        ).performClick()

        assertEquals(PaywallBypassMethod.WAYBACK_MACHINE, selected.get())
    }

    @Test
    fun `external actions are disabled while offline`() {
        val context = RuntimeEnvironment.getApplication()
        setContent(isOnline = false)

        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_open_original_in_chrome)
        ).assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription(
            context.getString(
                R.string.article_open_through_paywall_service,
                context.getString(R.string.paywall_smry_ai)
            )
        ).assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_choose_paywall_service)
        ).assertIsNotEnabled()
    }

    @Test
    fun `paywall action is hidden when the current url is already a bypass url`() {
        val context = RuntimeEnvironment.getApplication()
        setContent(canUsePaywallBypass = false)

        composeTestRule.onAllNodesWithContentDescription(
            context.getString(
                R.string.article_open_through_paywall_service,
                context.getString(R.string.paywall_smry_ai)
            )
        ).assertCountEquals(0)
    }

    private fun setContent(
        isOnline: Boolean = true,
        defaultMethod: PaywallBypassMethod = PaywallBypassMethod.SMRY_AI,
        canUsePaywallBypass: Boolean = true,
        onBypassPaywall: (PaywallBypassMethod) -> Unit = {}
    ) {
        composeTestRule.setContent {
            HReaderTheme {
                ArticleSourceActions(
                    defaultPaywallBypassMethod = defaultMethod,
                    isOnline = isOnline,
                    canUsePaywallBypass = canUsePaywallBypass,
                    onOpenInChrome = {},
                    onBypassPaywall = onBypassPaywall
                )
            }
        }
    }
}

private class ArticleSourceActionsTestApplication : Application()
