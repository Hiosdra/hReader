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
import com.hiosdra.hreader.core.application.port.out.ArticleSyncStore
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ContentSyncWorkerTestApplication::class, sdk = [35])
class ContentSyncWorkerTest {

    @Test
    fun `quiet hours skip the refresh and report success`() = runBlocking {
        val repository = RecordingArticleSyncStore()
        val preferences = quietHoursPreferences()
        val scheduler = mockk<SyncRequester>(relaxed = true)
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            repository = repository,
            preferences = preferences,
            scheduler = scheduler,
            errorReporter = errorReporter
        ).doWork()

        assertTrue(result is Success)
        assertEquals(0, repository.refreshCount)
        verify(exactly = 0) { scheduler.enqueuePrefetch() }
        verify(exactly = 0) { errorReporter.captureException(any(), any()) }
    }

    @Test
    fun `explicit sync ignores quiet hours`() = runBlocking {
        val repository = RecordingArticleSyncStore()
        val preferences = quietHoursPreferences()

        val result = createWorker(
            repository = repository,
            preferences = preferences,
            inputData = Data.Builder()
                .putBoolean(KEY_IGNORE_QUIET_HOURS, true)
                .build()
        ).doWork()

        assertTrue(result is Success)
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `force refresh completes and enqueues prefetch when requested`() = runBlocking {
        val repository = RecordingArticleSyncStore()
        val scheduler = mockk<SyncRequester>(relaxed = true)

        val result = createWorker(
            repository = repository,
            scheduler = scheduler,
            inputData = Data.Builder()
                .putBoolean(KEY_FORCE_FULL_SYNC, true)
                .putBoolean(KEY_ENQUEUE_PREFETCH, true)
                .build()
        ).doWork()

        assertTrue(result is Success)
        assertEquals(1, repository.refreshCount)
        assertEquals(true, repository.lastForceFullSync)
        verify(exactly = 1) { scheduler.enqueuePrefetch() }
    }

    @Test
    fun `transient failure retries without reporting an error`() = runBlocking {
        val repository = RecordingArticleSyncStore(IOException("connection lost"))
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            repository = repository,
            errorReporter = errorReporter,
            runAttemptCount = 4
        ).doWork()

        assertTrue(result is Retry)
        verify(exactly = 0) { errorReporter.captureException(any(), any()) }
    }

    @Test
    fun `nonretryable failure reports and returns localized failure`() = runBlocking {
        val failure = IllegalArgumentException("invalid configuration")
        val repository = RecordingArticleSyncStore(failure)
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            repository = repository,
            errorReporter = errorReporter
        ).doWork()

        assertTrue(result is Failure)
        assertEquals(
            RuntimeEnvironment.getApplication().getString(com.hiosdra.hreader.R.string.sync_content_failed),
            result.outputData.getString(KEY_ERROR_MESSAGE)
        )
        verify(exactly = 1) { errorReporter.captureException(failure, "content_sync") }
    }

    private fun createWorker(
        repository: RecordingArticleSyncStore,
        preferences: SyncPreferences = mockk(relaxed = true),
        scheduler: SyncRequester = mockk(relaxed = true),
        errorReporter: ErrorReporter = mockk(relaxed = true),
        inputData: Data = Data.Builder().build(),
        runAttemptCount: Int = 0
    ): ContentSyncWorker {
        val performanceTracker = RecordingPerformanceTracker()
        val factory = ContentSyncWorkerFactory(
            repository = repository,
            performanceTracker = performanceTracker,
            scheduler = scheduler,
            preferences = preferences,
            errorReporter = errorReporter
        )
        return TestListenableWorkerBuilder.from(
            RuntimeEnvironment.getApplication(),
            ContentSyncWorker::class.java
        )
            .setWorkerFactory(factory)
            .setInputData(inputData)
            .setRunAttemptCount(runAttemptCount)
            .build()
    }

    private fun quietHoursPreferences(): SyncPreferences = mockk<SyncPreferences>(relaxed = true).also {
        every { it.getQuietHoursEnabled() } returns true
        every { it.getQuietHoursStartHour() } returns 22
        every { it.getQuietHoursEndHour() } returns 7
    }
}

private class RecordingArticleSyncStore(
    private val failure: Throwable? = null
) : ArticleSyncStore {
    var refreshCount = 0
    var lastForceFullSync: Boolean? = null

    override suspend fun refreshArticles(forceFullSync: Boolean) {
        refreshCount += 1
        lastForceFullSync = forceFullSync
        failure?.let { throw it }
    }
}

private class RecordingPerformanceTracker : SyncPerformanceTracker {
    override suspend fun <T> measureSyncTime(
        operation: SyncPerformanceOperation,
        block: suspend () -> T
    ): T = block()

    override fun logBatchInfo(batchSize: Int, totalArticles: Int) = Unit

    override fun logArticleSyncStats(stats: ArticleSyncStats) = Unit

    override fun logSyncMode(isIncremental: Boolean, lastSyncTime: Long?) = Unit
}

private class ContentSyncWorkerFactory(
    private val repository: ArticleSyncStore,
    private val performanceTracker: SyncPerformanceTracker,
    private val scheduler: SyncRequester,
    private val preferences: SyncPreferences,
    private val errorReporter: ErrorReporter
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        if (workerClassName != ContentSyncWorker::class.java.name) return null
        return ContentSyncWorker(
            appContext = appContext,
            params = workerParameters,
            repository = repository,
            syncPerformanceLogger = performanceTracker,
            syncScheduler = scheduler,
            preferencesManager = preferences,
            errorReportingManager = errorReporter,
            clock = Clock.fixed(
                Instant.parse("2026-08-30T23:00:00Z"),
                ZoneOffset.UTC
            )
        )
    }
}

private class ContentSyncWorkerTestApplication : Application()
