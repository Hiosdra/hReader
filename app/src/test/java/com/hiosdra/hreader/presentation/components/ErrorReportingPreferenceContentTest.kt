package com.hiosdra.hreader.presentation.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ErrorReportingPreferenceContentTestApplication::class, sdk = [35])
class ErrorReportingPreferenceContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `preference exposes privacy link and forwards switch changes`() {
        val context = RuntimeEnvironment.getApplication()
        val changedValue = AtomicReference<Boolean>()

        composeTestRule.setContent {
            HReaderTheme {
                ErrorReportingPreferenceContent(
                    enabled = false,
                    onEnabledChange = changedValue::set
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.error_reporting_privacy))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.error_reporting_title))
            .performClick()

        assertEquals(true, changedValue.get())
    }
}

private class ErrorReportingPreferenceContentTestApplication : Application()
