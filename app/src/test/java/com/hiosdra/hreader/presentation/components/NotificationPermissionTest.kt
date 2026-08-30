package com.hiosdra.hreader.presentation.components

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = NotificationPermissionTestApplication::class, sdk = [32])
class NotificationPermissionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `pre Android 13 invokes the action without requesting a permission`() {
        val invocations = AtomicInteger()
        var requestPermission: ((() -> Unit) -> Unit)? = null

        composeTestRule.setContent {
            HReaderTheme {
                requestPermission = rememberNotificationPermissionRequest()
            }
        }
        composeTestRule.runOnIdle {
            requestPermission?.invoke { invocations.incrementAndGet() }
        }

        assertEquals(1, invocations.get())
    }
}

private class NotificationPermissionTestApplication : Application()
