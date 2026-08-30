package com.hiosdra.hreader.presentation.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.domain.model.OfflineReadiness
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = SettingsSectionsTestApplication::class, sdk = [35])
class SettingsSectionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `sync section forwards interval and toggle changes`() {
        val context = RuntimeEnvironment.getApplication()
        val selectedInterval = AtomicInteger()
        val unmeteredOnly = AtomicReference<Boolean>()

        composeTestRule.setContent {
            HReaderTheme {
                SyncSection(
                    state = SyncUiState(intervalMinutes = 15),
                    onIntervalChange = selectedInterval::set,
                    onUnmeteredOnlyChange = unmeteredOnly::set,
                    onSyncWhileRoamingChange = {},
                    onQuietHoursEnabledChange = {},
                    onQuietHoursChange = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.sync_automatic)).performClick()
        composeTestRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.sync_hours, 1, 1)
        ).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.sync_wifi_only)).performClick()

        assertEquals(60, selectedInterval.get())
        assertEquals(true, unmeteredOnly.get())
    }

    @Test
    fun `offline section forwards preparation and cache choices`() {
        val prepareCalls = AtomicInteger()
        val imageSetting = AtomicReference<Boolean>()
        val cacheBudget = AtomicInteger()

        composeTestRule.setContent {
            HReaderTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OfflineReadinessSection(
                        state = OfflineUiState(
                            readiness = OfflineReadiness(offlineTargetCount = 2, storedContentCount = 1),
                            imageDownloadEnabled = true
                        ),
                        onPrepare = { prepareCalls.incrementAndGet() },
                        onFullOfflineSync = {},
                        onBacklogTargetChange = {},
                        onImageDownloadEnabledChange = imageSetting::set,
                        onImageCacheBudgetChange = cacheBudget::set
                    )
                }
            }
        }

        val context = RuntimeEnvironment.getApplication()
        composeTestRule.onNodeWithText(context.getString(R.string.offline_download_reading)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.offline_download_images)).performClick()
        composeTestRule.onNode(
            hasText(context.getString(R.string.offline_image_budget, 500)) and hasClickAction()
        ).performScrollTo().performClick()

        assertEquals(1, prepareCalls.get())
        assertEquals(false, imageSetting.get())
        assertEquals(500, cacheBudget.get())
    }

    @Test
    fun `AI credibility toggle reveals explanation and model row opens picker`() {
        val pickerCalls = AtomicInteger()

        composeTestRule.setContent {
            var credibilityEnabled by remember { mutableStateOf(false) }
            HReaderTheme {
                AiSettingsSection(
                    credibilityScoreEnabled = credibilityEnabled,
                    onCredibilityScoreChange = { credibilityEnabled = it },
                    openRouterApiKey = "",
                    onOpenRouterApiKeyChange = {},
                    aiModels = AiModelsUiState(selectedModelId = AiModel.DEFAULT_ID),
                    onOpenModelPicker = { pickerCalls.incrementAndGet() }
                )
            }
        }

        val context = RuntimeEnvironment.getApplication()
        composeTestRule.onNodeWithText(
            context.getString(R.string.settings_show_credibility_chip)
        ).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_rating_meaning))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_model)).performClick()

        assertEquals(1, pickerCalls.get())
    }

    @Test
    fun `local data section requires confirmation before resync`() {
        val resyncCalls = AtomicInteger()

        composeTestRule.setContent {
            HReaderTheme {
                LocalDataSection(
                    state = SyncUiState(),
                    canSignOut = true,
                    isBusy = false,
                    onResyncFromScratch = { resyncCalls.incrementAndGet() },
                    onSignOut = {}
                )
            }
        }

        val context = RuntimeEnvironment.getApplication()
        composeTestRule.onNodeWithText(context.getString(R.string.sync_clear_and_sync)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.sync_clear_confirm_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.sync_clear_data)).performClick()

        assertEquals(1, resyncCalls.get())
    }
}

private class SettingsSectionsTestApplication : Application()
