package com.hiosdra.hreader.entrypoint.worker

import android.app.Application
import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result.Failure
import androidx.work.ListenableWorker.Result.Retry
import androidx.work.ListenableWorker.Result.Success
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.hiosdra.hreader.core.application.observability.ArticleSyncStats
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.port.out.ArticleContentStore
import com.hiosdra.hreader.core.application.port.out.ArticleMaintenanceStore
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = CacheMaintenanceWorkerRobolectricTestApplication::class, sdk = [35])
class CacheMaintenanceWorkerRobolectricTest {

    @Test
    fun `cleanup runs before preview backfill`() = runBlocking {
        val events = mutableListOf<String>()
        val contentStore = mockk<ArticleContentStore>(relaxed = true)
        val maintenanceStore = mockk<ArticleMaintenanceStore>(relaxed = true)
        coEvery { contentStore.cleanupOrphanedContent() } coAnswers { events += "cleanup" }
        coEvery { maintenanceStore.backfillMissingPreviews(any()) } coAnswers {
            events += "preview_backfill"
            4
        }

        val result = createWorker(contentStore, maintenanceStore).doWork()

        assertTrue(result is Success)
        assertEquals(listOf("cleanup", "preview_backfill"), events)
        coVerify(exactly = 1) { maintenanceStore.backfillMissingPreviews(250) }
    }

    @Test
    fun `transient maintenance failure is retried without reporting`() = runBlocking {
        val contentStore = mockk<ArticleContentStore>(relaxed = true)
        val maintenanceStore = mockk<ArticleMaintenanceStore>(relaxed = true)
        coEvery { contentStore.cleanupOrphanedContent() } throws IllegalStateException("busy")
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            contentStore = contentStore,
            maintenanceStore = maintenanceStore,
            errorReporter = errorReporter,
            runAttemptCount = 0
        ).doWork()

        assertTrue(result is Retry)
        verify(exactly = 0) { errorReporter.captureException(any(), any()) }
    }

    @Test
    fun `maintenance failure at the attempt cap returns failure and reports`() = runBlocking {
        val failure = IllegalStateException("broken database")
        val contentStore = mockk<ArticleContentStore>(relaxed = true)
        val maintenanceStore = mockk<ArticleMaintenanceStore>(relaxed = true)
        coEvery { maintenanceStore.backfillMissingPreviews(any()) } throws failure
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            contentStore = contentStore,
            maintenanceStore = maintenanceStore,
            errorReporter = errorReporter,
            runAttemptCount = 3
        ).doWork()

        assertTrue(result is Failure)
        verify(exactly = 1) { errorReporter.captureException(failure, "cache_maintenance") }
    }

    private fun createWorker(
        contentStore: ArticleContentStore,
        maintenanceStore: ArticleMaintenanceStore,
        errorReporter: ErrorReporter = mockk(relaxed = true),
        inputData: Data = Data.Builder().build(),
        runAttemptCount: Int = 0
    ): CacheMaintenanceWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? = if (workerClassName == CacheMaintenanceWorker::class.java.name) {
                CacheMaintenanceWorker(
                    appContext = appContext,
                    params = workerParameters,
                    articleRepository = maintenanceStore,
                    articleContentRepository = contentStore,
                    syncPerformanceLogger = CacheMaintenancePerformanceTracker(),
                    errorReportingManager = errorReporter
                )
            } else {
                null
            }
        }
        return TestListenableWorkerBuilder.from(
            RuntimeEnvironment.getApplication(),
            CacheMaintenanceWorker::class.java
        )
            .setWorkerFactory(factory)
            .setInputData(inputData)
            .setRunAttemptCount(runAttemptCount)
            .build()
    }
}

private class CacheMaintenancePerformanceTracker : SyncPerformanceTracker {
    override suspend fun <T> measureSyncTime(
        operation: SyncPerformanceOperation,
        block: suspend () -> T
    ): T = block()

    override fun logBatchInfo(batchSize: Int, totalArticles: Int) = Unit

    override fun logArticleSyncStats(stats: ArticleSyncStats) = Unit

    override fun logSyncMode(isIncremental: Boolean, lastSyncTime: Long?) = Unit
}

private class CacheMaintenanceWorkerRobolectricTestApplication : Application()
