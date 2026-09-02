package com.hiosdra.hreader.entrypoint.worker

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewPrefetchStore
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewStore
import com.hiosdra.hreader.core.application.port.out.ArticleContentStore
import com.hiosdra.hreader.core.application.port.out.AiOverviewPrefetchTarget
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import com.hiosdra.hreader.core.domain.model.ArticleText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ArticleAiOverviewPreloadWorkerRobolectricTestApplication::class, sdk = [35])
class ArticleAiOverviewPreloadWorkerRobolectricTest {
    @Test
    fun gemmaPreloadUsesOnlyLocalArticleContent() = runBlocking {
        setBattery(level = 90, status = android.os.BatteryManager.BATTERY_STATUS_DISCHARGING)
        val targetStore = mockk<ArticleAiOverviewPrefetchStore>()
        val contentStore = mockk<ArticleContentStore>()
        val aiGateway = mockk<ArticleAiGateway>()
        val overviewStore = mockk<ArticleAiOverviewStore>()
        val aiPreferences = mockk<AiPreferences>()
        val errorReporter = mockk<ErrorReporter>(relaxed = true)
        val target = AiOverviewPrefetchTarget(1L, "Title", "https://example.com/article")

        every { aiPreferences.getAiModelId() } returns AiModel.GEMMA_4_E2B_ID
        coEvery { targetStore.getAiOverviewPrefetchTargets(any(), any()) } returns listOf(target)
        coEvery {
            contentStore.getArticleContent(target.id, target.url, allowNetwork = false)
        } returns ArticleText("<p>Body</p>", null, ArticleContentSource.FULL)
        coEvery { overviewStore.get(target.id, "<p>Body</p>", AiModel.GEMMA_4_E2B_ID) } returns null
        coEvery {
            aiGateway.generateArticleOverview(
                target.title,
                "<p>Body</p>",
                AiModel.GEMMA_4_E2B_ID,
                any()
            )
        } returns Result.success("Summary")
        coEvery {
            overviewStore.save(target.id, "<p>Body</p>", AiModel.GEMMA_4_E2B_ID, "Summary")
        } just runs

        val result = createWorker(
            targetStore,
            contentStore,
            aiGateway,
            overviewStore,
            aiPreferences,
            errorReporter
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify { contentStore.getArticleContent(target.id, target.url, allowNetwork = false) }
        coVerify { overviewStore.save(target.id, "<p>Body</p>", AiModel.GEMMA_4_E2B_ID, "Summary") }
        verify(exactly = 0) { errorReporter.captureException(any(), any()) }
    }

    @Test
    fun lowBatteryDefersWithoutLoadingArticles() = runBlocking {
        setBattery(level = 80, status = android.os.BatteryManager.BATTERY_STATUS_DISCHARGING)
        val targetStore = mockk<ArticleAiOverviewPrefetchStore>(relaxed = true)
        val contentStore = mockk<ArticleContentStore>(relaxed = true)
        val aiPreferences = mockk<AiPreferences>(relaxed = true)

        val result = createWorker(
            targetStore = targetStore,
            contentStore = contentStore,
            aiGateway = mockk(relaxed = true),
            overviewStore = mockk(relaxed = true),
            aiPreferences = aiPreferences,
            errorReporter = mockk(relaxed = true)
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        coVerify(exactly = 0) { targetStore.getAiOverviewPrefetchTargets(any(), any()) }
        coVerify(exactly = 0) { contentStore.getArticleContent(any(), any(), any()) }
    }

    @Test
    fun generationBudgetFinishesSuccessfullyAfterEightOverviews() = runBlocking {
        setBattery(level = 90, status = android.os.BatteryManager.BATTERY_STATUS_DISCHARGING)
        val targets = (1L..9L).map { id ->
            AiOverviewPrefetchTarget(id, "Title $id", "https://example.com/article/$id")
        }
        val targetStore = mockk<ArticleAiOverviewPrefetchStore>()
        val contentStore = mockk<ArticleContentStore>()
        val aiGateway = mockk<ArticleAiGateway>()
        val overviewStore = mockk<ArticleAiOverviewStore>()
        val aiPreferences = mockk<AiPreferences>()

        every { aiPreferences.getAiModelId() } returns AiModel.GEMMA_4_E2B_ID
        coEvery { targetStore.getAiOverviewPrefetchTargets(any(), any()) } returns targets
        coEvery { contentStore.getArticleContent(any(), any(), allowNetwork = false) } returns
            ArticleText("<p>Body</p>", null, ArticleContentSource.FULL)
        coEvery { overviewStore.get(any(), any(), any()) } returns null
        coEvery { aiGateway.generateArticleOverview(any(), any(), any(), any()) } returns
            Result.success("Summary")
        coEvery { overviewStore.save(any(), any(), any(), any()) } just runs

        val result = createWorker(
            targetStore = targetStore,
            contentStore = contentStore,
            aiGateway = aiGateway,
            overviewStore = overviewStore,
            aiPreferences = aiPreferences,
            errorReporter = mockk(relaxed = true)
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 8) { aiGateway.generateArticleOverview(any(), any(), any(), any()) }
        coVerify(exactly = 1) { targetStore.getAiOverviewPrefetchTargets(16, 0) }
    }

    private fun createWorker(
        targetStore: ArticleAiOverviewPrefetchStore,
        contentStore: ArticleContentStore,
        aiGateway: ArticleAiGateway,
        overviewStore: ArticleAiOverviewStore,
        aiPreferences: AiPreferences,
        errorReporter: ErrorReporter
    ): ArticleAiOverviewPreloadWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? = if (workerClassName == ArticleAiOverviewPreloadWorker::class.java.name) {
                ArticleAiOverviewPreloadWorker(
                    appContext,
                    workerParameters,
                    targetStore,
                    contentStore,
                    aiGateway,
                    overviewStore,
                    aiPreferences,
                    errorReporter
                )
            } else {
                null
            }
        }
        return TestListenableWorkerBuilder.from(
            RuntimeEnvironment.getApplication(),
            ArticleAiOverviewPreloadWorker::class.java
        )
            .setWorkerFactory(factory)
            .build()
    }

    private fun setBattery(level: Int, status: Int) {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(android.os.BatteryManager.EXTRA_LEVEL, level)
                .putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                .putExtra(android.os.BatteryManager.EXTRA_STATUS, status)
        )
    }
}

private class ArticleAiOverviewPreloadWorkerRobolectricTestApplication : Application()
