package com.hiosdra.hreader.presentation.onboarding

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.presentation.settings.ServerSettingsUiState
import com.hiosdra.hreader.presentation.settings.SettingsViewModel
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ServerSetupScreenTestApplication::class, sdk = [32])
class ServerSetupScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `setup stays disabled until all server fields are present`() {
        val context = RuntimeEnvironment.getApplication()

        setContent(ServerSettingsUiState())

        composeTestRule.onNodeWithText(
            context.getString(R.string.onboarding_continue_without_testing)
        ).assertIsNotEnabled()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `complete setup invokes the finish callback`() {
        val context = RuntimeEnvironment.getApplication()
        val finished = AtomicInteger()
        val viewModel = viewModel(
            ServerSettingsUiState(
                backendType = BackendType.FRESHRSS,
                serverUrl = "https://reader.example",
                username = "reader",
                secret = "token"
            )
        )
        every { viewModel.onSetupFinished(any()) } answers {
            (args[0] as () -> Unit).invoke()
        }

        setContent(
            state = ServerSettingsUiState(
                backendType = BackendType.FRESHRSS,
                serverUrl = "https://reader.example",
                username = "reader",
                secret = "token"
            ),
            settingsViewModel = viewModel,
            onSetupFinished = { finished.incrementAndGet() }
        )

        composeTestRule.onNodeWithText(
            context.getString(R.string.onboarding_continue_without_testing)
        ).performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, finished.get())
    }

    private fun viewModel(state: ServerSettingsUiState): SettingsViewModel =
        mockk<SettingsViewModel>(relaxed = true).also {
            every { it.uiState } returns MutableStateFlow(state)
            every { it.openRouterApiKey } returns MutableStateFlow("")
        }

    private fun setContent(
        state: ServerSettingsUiState,
        settingsViewModel: SettingsViewModel = viewModel(state),
        onSetupFinished: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            HReaderTheme {
                ServerSetupScreen(
                    onSetupFinished = onSetupFinished,
                    settingsViewModel = settingsViewModel,
                    errorReportingManager = mockk<ErrorReporter>(relaxed = true)
                )
            }
        }
    }
}

private class ServerSetupScreenTestApplication : Application()
