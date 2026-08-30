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
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.observability.ArticleSyncStats
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.port.out.ArticleMaintenanceStore
import com.hiosdra.hreader.core.application.port.out.ArticlePageStore
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.sync.PrefetchTarget
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(application = FullPageSyncWorkerRobolectricTestApplication::class, sdk = [35])
class FullPageSyncWorkerRobolectricTest {

    @Test
    fun `empty page backlog completes without prefetching`() = runBlocking {
        val pageStore = FullPagePageStore(mutableListOf(emptyList()))
        val repository = FullPageMaintenanceStore(targets(2))

        val result = createWorker(repository, pageStore).doWork()

        assertTrue(result is Success)
        assertEquals(0, pageStore.prefetchCalls)
    }

    @Test
    fun `page prefetch is limited to one hundred entries`() = runBlocking {
        val outstanding = pairs(150)
        val pageStore = FullPagePageStore(mutableListOf(outstanding, emptyList()))
        val repository = FullPageMaintenanceStore(targets(150))

        val result = createWorker(repository, pageStore).doWork()

        assertTrue(result is Success)
        assertEquals(1, pageStore.prefetchCalls)
        assertEquals(100, pageStore.prefetchedEntries.size)
        assertEquals(outstanding.take(100), pageStore.prefetchedEntries)
    }

    @Test
    fun `transient page prefetch failure retries without reporting`() = runBlocking {
        val pageStore = FullPagePageStore(
            missingPagesResults = mutableListOf(pairs(1)),
            prefetchFailure = IOException("connection lost")
        )
        val repository = FullPageMaintenanceStore(targets(1))
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            repository = repository,
            pageStore = pageStore,
            errorReporter = errorReporter
        ).doWork()

        assertTrue(result is Retry)
        verify(exactly = 0) { errorReporter.captureException(any(), any()) }
    }

    @Test
    fun `remaining pages retry after a batch made progress even at the attempt cap`() = runBlocking {
        val pageStore = FullPagePageStore(
            mutableListOf(
                pairs(2),
                pairs(1)
            )
        )
        val repository = FullPageMaintenanceStore(targets(2))

        val result = createWorker(
            repository = repository,
            pageStore = pageStore,
            runAttemptCount = 5
        ).doWork()

        assertTrue(result is Retry)
    }

    @Test
    fun `unchanged pages at the attempt cap return a localized failure`() = runBlocking {
        val outstanding = pairs(2)
        val pageStore = FullPagePageStore(mutableListOf(outstanding, outstanding))
        val repository = FullPageMaintenanceStore(targets(2))
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            repository = repository,
            pageStore = pageStore,
            errorReporter = errorReporter,
            runAttemptCount = 5
        ).doWork()
        val expectedMessage = RuntimeEnvironment.getApplication().resources.getQuantityString(
            R.plurals.offline_original_pages_failed_count,
            outstanding.size,
            outstanding.size
        )

        assertTrue(result is Failure)
        assertEquals(expectedMessage, result.outputData.getString(KEY_ERROR_MESSAGE))
        verify(exactly = 1) { errorReporter.captureMessage(expectedMessage, "full_page_sync") }
    }

    @Test
    fun `quiet hours skip page prefetch`() = runBlocking {
        val preferences = mockk<SyncPreferences>(relaxed = true)
        every { preferences.getQuietHoursEnabled() } returns true
        every { preferences.getQuietHoursStartHour() } returns 22
        every { preferences.getQuietHoursEndHour() } returns 7
        val pageStore = FullPagePageStore(mutableListOf(pairs(1)))
        val repository = FullPageMaintenanceStore(targets(1))

        val result = createWorker(
            repository = repository,
            pageStore = pageStore,
            preferences = preferences
        ).doWork()

        assertTrue(result is Success)
        assertEquals(0, repository.targetCalls)
        assertEquals(0, pageStore.missingPageCalls)
    }

    private fun createWorker(
        repository: FullPageMaintenanceStore,
        pageStore: FullPagePageStore,
        preferences: SyncPreferences = mockk(relaxed = true),
        errorReporter: ErrorReporter = mockk(relaxed = true),
        inputData: Data = Data.Builder().build(),
        runAttemptCount: Int = 0
    ): FullPageSyncWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? = if (workerClassName == FullPageSyncWorker::class.java.name) {
                FullPageSyncWorker(
                    appContext = appContext,
                    params = workerParameters,
                    articleRepository = repository,
                    articlePageRepository = pageStore,
                    syncPerformanceLogger = FullPagePerformanceTracker(),
                    preferencesManager = preferences,
                    errorReportingManager = errorReporter,
                    clock = TEST_CLOCK
                )
            } else {
                null
            }
        }
        return TestListenableWorkerBuilder.from(
            RuntimeEnvironment.getApplication(),
            FullPageSyncWorker::class.java
        )
            .setWorkerFactory(factory)
            .setInputData(inputData)
            .setRunAttemptCount(runAttemptCount)
            .build()
    }

    private fun targets(count: Int): List<PrefetchTarget> = (1..count).map { id ->
        PrefetchTarget(
            id = id.toLong(),
            url = "https://example.com/article/$id",
            enclosures = emptyList()
        )
    }

    private fun pairs(count: Int): List<Pair<Long, String>> = targets(count).map { it.id to it.url }

    private companion object {
        val TEST_CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-08-30T23:00:00Z"),
            ZoneOffset.UTC
        )
    }
}

private class FullPageMaintenanceStore(
    private val prefetchTargets: List<PrefetchTarget>
) : ArticleMaintenanceStore {
    var targetCalls = 0

    override suspend fun getPrefetchTargets(): List<PrefetchTarget> {
        targetCalls += 1
        return prefetchTargets
    }

    override suspend fun backfillMissingPreviews(limit: Int): Int = 0
}

private class FullPagePageStore(
    private val missingPagesResults: MutableList<List<Pair<Long, String>>>,
    private val prefetchFailure: Throwable? = null
) : ArticlePageStore {
    var missingPageCalls = 0
    var prefetchCalls = 0
    var prefetchedEntries: List<Pair<Long, String>> = emptyList()

    override suspend fun entriesMissingPages(entries: List<Pair<Long, String>>): List<Pair<Long, String>> {
        missingPageCalls += 1
        return missingPagesResults.removeAt(0)
    }

    override suspend fun prefetchPages(
        entries: List<Pair<Long, String>>,
        limit: Int?,
        onProgress: (done: Int, total: Int) -> Unit
    ) {
        prefetchCalls += 1
        prefetchedEntries = entries
        prefetchFailure?.let { throw it }
        onProgress(entries.size, entries.size)
    }

    override suspend fun getOfflinePage(entryId: Long, originalUrl: String) = null

    override suspend fun cleanupOrphanedPages() = Unit

    override suspend fun clearAll() = Unit
}

private class FullPagePerformanceTracker : SyncPerformanceTracker {
    override suspend fun <T> measureSyncTime(
        operation: SyncPerformanceOperation,
        block: suspend () -> T
    ): T = block()

    override fun logBatchInfo(batchSize: Int, totalArticles: Int) = Unit

    override fun logArticleSyncStats(stats: ArticleSyncStats) = Unit

    override fun logSyncMode(isIncremental: Boolean, lastSyncTime: Long?) = Unit
}

private class FullPageSyncWorkerRobolectricTestApplication : Application()
