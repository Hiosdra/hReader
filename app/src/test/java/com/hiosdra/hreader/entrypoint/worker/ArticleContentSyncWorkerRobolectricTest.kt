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
import com.hiosdra.hreader.core.application.port.out.ArticleContentStore
import com.hiosdra.hreader.core.application.port.out.ArticleMaintenanceStore
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.sync.PrefetchTarget
import com.hiosdra.hreader.core.domain.model.Enclosure
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
@Config(application = ArticleContentSyncWorkerRobolectricTestApplication::class, sdk = [35])
class ArticleContentSyncWorkerRobolectricTest {

    @Test
    fun `empty target list completes without touching content storage`() = runBlocking {
        val contentStore = ArticleContentStoreFake()
        val repository = ArticleContentMaintenanceStore(emptyList())

        val result = createWorker(repository, contentStore).doWork()

        assertTrue(result is Success)
        assertEquals(0, contentStore.missingContentCalls)
        assertEquals(0, contentStore.prefetchCalls)
        assertEquals(0, contentStore.imageDownloadCalls)
    }

    @Test
    fun `background prefetch downloads only the first image enclosure`() = runBlocking {
        val target = target(
            enclosures = listOf(
                Enclosure("https://example.com/one.jpg", "image/jpeg"),
                Enclosure("https://example.com/two.png", "image/png"),
                Enclosure("https://example.com/audio.mp3", "audio/mpeg")
            )
        )
        val contentStore = ArticleContentStoreFake(missingContent = listOf(target.id to target.url))
        val repository = ArticleContentMaintenanceStore(listOf(target))

        val result = createWorker(repository, contentStore).doWork()

        assertTrue(result is Success)
        assertEquals(
            listOf(target.id to listOf("https://example.com/one.jpg")),
            contentStore.imageBatches.single()
        )
        assertEquals(1, contentStore.missingContentCalls)
        assertEquals(0, contentStore.missingFullPreparationCalls)
        assertEquals(false, contentStore.lastDownloadAllImages)
    }

    @Test
    fun `full offline preparation downloads every image and uses full-preparation query`() = runBlocking {
        val target = target(
            enclosures = listOf(
                Enclosure("https://example.com/one.jpg", "image/jpeg"),
                Enclosure("https://example.com/two.png", "image/png")
            )
        )
        val contentStore = ArticleContentStoreFake(
            missingFullPreparation = listOf(target.id to target.url)
        )
        val repository = ArticleContentMaintenanceStore(listOf(target))

        val result = createWorker(
            repository = repository,
            contentStore = contentStore,
            inputData = Data.Builder()
                .putBoolean(KEY_DOWNLOAD_ALL_IMAGES, true)
                .build()
        ).doWork()

        assertTrue(result is Success)
        assertEquals(
            listOf(
                listOf(
                    target.id to listOf(
                        "https://example.com/one.jpg",
                        "https://example.com/two.png"
                    )
                )
            ),
            contentStore.imageBatches
        )
        assertEquals(0, contentStore.missingContentCalls)
        assertEquals(1, contentStore.missingFullPreparationCalls)
        assertEquals(true, contentStore.lastDownloadAllImages)
    }

    @Test
    fun `draining a bounded content batch retries when work remains`() = runBlocking {
        val outstanding = pairs(501)
        val contentStore = ArticleContentStoreFake(missingContent = outstanding)
        val repository = ArticleContentMaintenanceStore(targets(501))

        val result = createWorker(
            repository = repository,
            contentStore = contentStore,
            inputData = Data.Builder()
                .putBoolean(KEY_DRAIN_REMAINING, true)
                .build()
        ).doWork()

        assertTrue(result is Retry)
        assertEquals(500, contentStore.prefetchedEntries.size)
        assertEquals(outstanding.take(500), contentStore.prefetchedEntries)
    }

    @Test
    fun `background prefetch leaves a remaining batch for the next sync`() = runBlocking {
        val contentStore = ArticleContentStoreFake(missingContent = pairs(501))
        val repository = ArticleContentMaintenanceStore(targets(501))

        val result = createWorker(repository, contentStore).doWork()

        assertTrue(result is Success)
        assertEquals(500, contentStore.prefetchedEntries.size)
    }

    @Test
    fun `transient content prefetch failure retries without reporting`() = runBlocking {
        val contentStore = ArticleContentStoreFake(
            missingContent = pairs(1),
            prefetchFailure = IOException("connection lost")
        )
        val repository = ArticleContentMaintenanceStore(targets(1))
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            repository = repository,
            contentStore = contentStore,
            errorReporter = errorReporter
        ).doWork()

        assertTrue(result is Retry)
        verify(exactly = 0) { errorReporter.captureException(any(), any()) }
    }

    @Test
    fun `nonretryable content failure returns a localized failure`() = runBlocking {
        val failure = IllegalArgumentException("bad content")
        val contentStore = ArticleContentStoreFake(
            missingContent = pairs(1),
            prefetchFailure = failure
        )
        val repository = ArticleContentMaintenanceStore(targets(1))
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            repository = repository,
            contentStore = contentStore,
            errorReporter = errorReporter
        ).doWork()
        val capturedFailure = slot<Throwable>()

        assertTrue(result is Failure)
        assertEquals(
            RuntimeEnvironment.getApplication().getString(R.string.sync_article_content_failed),
            result.outputData.getString(KEY_ERROR_MESSAGE)
        )
        verify(exactly = 1) { errorReporter.captureException(capture(capturedFailure), "article_content_sync") }
        assertTrue(capturedFailure.captured is IllegalArgumentException)
        assertEquals("bad content", capturedFailure.captured.message)
    }

    @Test
    fun `quiet hours skip the content and image stages`() = runBlocking {
        val preferences = mockk<SyncPreferences>(relaxed = true)
        every { preferences.getQuietHoursEnabled() } returns true
        every { preferences.getQuietHoursStartHour() } returns 22
        every { preferences.getQuietHoursEndHour() } returns 7
        val contentStore = ArticleContentStoreFake(missingContent = pairs(1))
        val repository = ArticleContentMaintenanceStore(targets(1))

        val result = createWorker(
            repository = repository,
            contentStore = contentStore,
            preferences = preferences
        ).doWork()

        assertTrue(result is Success)
        assertEquals(0, repository.targetCalls)
        assertEquals(0, contentStore.prefetchCalls)
        assertEquals(0, contentStore.imageDownloadCalls)
    }

    private fun createWorker(
        repository: ArticleContentMaintenanceStore,
        contentStore: ArticleContentStoreFake,
        preferences: SyncPreferences = mockk(relaxed = true),
        errorReporter: ErrorReporter = mockk(relaxed = true),
        inputData: Data = Data.Builder().build(),
        runAttemptCount: Int = 0
    ): ArticleContentSyncWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? = if (workerClassName == ArticleContentSyncWorker::class.java.name) {
                ArticleContentSyncWorker(
                    appContext = appContext,
                    params = workerParameters,
                    articleRepository = repository,
                    articleContentRepository = contentStore,
                    syncPerformanceLogger = ArticleContentPerformanceTracker(),
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
            ArticleContentSyncWorker::class.java
        )
            .setWorkerFactory(factory)
            .setInputData(inputData)
            .setRunAttemptCount(runAttemptCount)
            .build()
    }

    private fun target(
        id: Long = 1L,
        enclosures: List<Enclosure> = emptyList()
    ) = PrefetchTarget(
        id = id,
        url = "https://example.com/article/$id",
        enclosures = enclosures
    )

    private fun targets(count: Int): List<PrefetchTarget> = (1..count).map { target(it.toLong()) }

    private fun pairs(count: Int): List<Pair<Long, String>> = targets(count).map { it.id to it.url }

    private companion object {
        val TEST_CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-08-30T23:00:00Z"),
            ZoneOffset.UTC
        )
    }
}

private class ArticleContentMaintenanceStore(
    private val prefetchTargets: List<PrefetchTarget>
) : ArticleMaintenanceStore {
    var targetCalls = 0

    override suspend fun getPrefetchTargets(): List<PrefetchTarget> {
        targetCalls += 1
        return prefetchTargets
    }

    override suspend fun backfillMissingPreviews(limit: Int): Int = 0
}

private class ArticleContentStoreFake(
    private val missingContent: List<Pair<Long, String>> = emptyList(),
    private val missingFullPreparation: List<Pair<Long, String>> = emptyList(),
    private val prefetchFailure: Throwable? = null
) : ArticleContentStore {
    var missingContentCalls = 0
    var missingFullPreparationCalls = 0
    var prefetchCalls = 0
    var imageDownloadCalls = 0
    var lastDownloadAllImages: Boolean? = null
    var prefetchedEntries: List<Pair<Long, String>> = emptyList()
    val imageBatches = mutableListOf<List<Pair<Long, List<String>>>>()

    override suspend fun getArticleContent(entryId: Long, url: String, allowNetwork: Boolean) =
        error("unused in worker test")

    override suspend fun entriesMissingContent(entries: List<Pair<Long, String>>): List<Pair<Long, String>> {
        missingContentCalls += 1
        return missingContent
    }

    override suspend fun entriesMissingFullOfflinePreparation(
        entries: List<Pair<Long, String>>
    ): List<Pair<Long, String>> {
        missingFullPreparationCalls += 1
        return missingFullPreparation
    }

    override suspend fun prefetchArticleContent(
        entries: List<Pair<Long, String>>,
        limit: Int?,
        downloadAllImages: Boolean,
        onProgress: (done: Int, total: Int) -> Unit
    ) {
        prefetchCalls += 1
        prefetchedEntries = entries
        lastDownloadAllImages = downloadAllImages
        prefetchFailure?.let { throw it }
        onProgress(entries.size, entries.size)
    }

    override suspend fun downloadEnclosureImages(entries: List<Pair<Long, List<String>>>) {
        imageDownloadCalls += 1
        imageBatches += entries
    }

    override suspend fun cleanupOrphanedContent() = Unit
}

private class ArticleContentPerformanceTracker : SyncPerformanceTracker {
    override suspend fun <T> measureSyncTime(
        operation: SyncPerformanceOperation,
        block: suspend () -> T
    ): T = block()

    override fun logBatchInfo(batchSize: Int, totalArticles: Int) = Unit

    override fun logArticleSyncStats(stats: ArticleSyncStats) = Unit

    override fun logSyncMode(isIncremental: Boolean, lastSyncTime: Long?) = Unit
}

private class ArticleContentSyncWorkerRobolectricTestApplication : Application()
