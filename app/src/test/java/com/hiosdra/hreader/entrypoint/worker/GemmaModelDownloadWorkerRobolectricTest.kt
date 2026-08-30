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
import androidx.work.testing.WorkManagerTestInitHelper
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.GemmaModelInsufficientStorageException
import com.hiosdra.hreader.core.application.ai.GemmaModelStatus
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = GemmaModelDownloadWorkerRobolectricTestApplication::class, sdk = [35])
class GemmaModelDownloadWorkerRobolectricTest {

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `available model download completes successfully`() = runBlocking {
        val gateway = RecordingGemmaModelGateway(downloadStatus = GemmaModelStatus.Available)

        val result = createWorker(gateway).doWork()

        assertTrue(result is Success)
        assertEquals(1, gateway.downloadCalls)
        assertEquals(0, gateway.markFailedCalls)
    }

    @Test
    fun `download that ends in failed status returns the gateway message`() = runBlocking {
        val gateway = RecordingGemmaModelGateway(
            downloadStatus = GemmaModelStatus.Failed("checksum mismatch")
        )
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(gateway, errorReporter).doWork()

        assertTrue(result is Failure)
        assertEquals("checksum mismatch", result.outputData.getString(KEY_ERROR_MESSAGE))
        assertEquals(1, gateway.markFailedCalls)
        verify(exactly = 1) { errorReporter.captureMessage("checksum mismatch", "gemma_model_download") }
    }

    @Test
    fun `transient download failure retries and marks the gateway retrying`() = runBlocking {
        val gateway = RecordingGemmaModelGateway(downloadFailure = IOException("connection lost"))
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(gateway, errorReporter).doWork()

        assertTrue(result is Retry)
        assertEquals(1, gateway.markRetryingCalls)
        verify(exactly = 0) { errorReporter.captureException(any(), any()) }
    }

    @Test
    fun `terminal failure reports and returns a localized failure`() = runBlocking {
        val failure = IllegalArgumentException("invalid model")
        val gateway = RecordingGemmaModelGateway(downloadFailure = failure)
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            gateway = gateway,
            errorReporter = errorReporter,
            runAttemptCount = 5
        ).doWork()

        assertTrue(result is Failure)
        assertEquals(
            RuntimeEnvironment.getApplication().getString(R.string.ai_model_install_failed),
            result.outputData.getString(KEY_ERROR_MESSAGE)
        )
        assertEquals(1, gateway.markFailedCalls)
        verify(exactly = 1) { errorReporter.captureException(failure, "gemma_model_download") }
    }

    @Test
    fun `insufficient storage failure is not sent to error reporting`() = runBlocking {
        val failure = GemmaModelInsufficientStorageException(
            requiredBytes = 2_000L,
            availableBytes = 1_000L
        )
        val gateway = RecordingGemmaModelGateway(downloadFailure = failure)
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            gateway = gateway,
            errorReporter = errorReporter,
            runAttemptCount = 5
        ).doWork()

        assertTrue(result is Failure)
        assertEquals(1, gateway.markFailedCalls)
        verify(exactly = 0) { errorReporter.captureException(any(), any()) }
    }

    private fun createWorker(
        gateway: RecordingGemmaModelGateway,
        errorReporter: ErrorReporter = mockk(relaxed = true),
        inputData: Data = Data.Builder().build(),
        runAttemptCount: Int = 0
    ): GemmaModelDownloadWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? = if (workerClassName == GemmaModelDownloadWorker::class.java.name) {
                GemmaModelDownloadWorker(
                    appContext = appContext,
                    params = workerParameters,
                    modelManager = gateway,
                    errorReporter = errorReporter
                )
            } else {
                null
            }
        }
        return TestListenableWorkerBuilder.from(
            RuntimeEnvironment.getApplication(),
            GemmaModelDownloadWorker::class.java
        )
            .setWorkerFactory(factory)
            .setInputData(inputData)
            .setRunAttemptCount(runAttemptCount)
            .build()
    }
}

private class RecordingGemmaModelGateway(
    private val downloadStatus: GemmaModelStatus = GemmaModelStatus.NotInstalled,
    private val downloadFailure: Throwable? = null
) : GemmaModelGateway {
    private val _status = MutableStateFlow<GemmaModelStatus>(GemmaModelStatus.NotInstalled)
    override val status: StateFlow<GemmaModelStatus> = _status
    override val modelSizeBytes: Long = 1_000L
    var downloadCalls = 0
    var markFailedCalls = 0
    var markRetryingCalls = 0

    override fun downloadPreflight() = error("unused in worker test")

    override fun markDownloadEnqueued() = Unit

    override fun markDownloadCancelled() = Unit

    override fun markDownloadFailed(message: String) {
        markFailedCalls += 1
        _status.value = GemmaModelStatus.Failed(message)
    }

    override fun markDownloadRetrying() {
        markRetryingCalls += 1
    }

    override suspend fun download() {
        downloadCalls += 1
        downloadFailure?.let { throw it }
        _status.value = downloadStatus
    }

    override suspend fun remove() = Unit
}

private class GemmaModelDownloadWorkerRobolectricTestApplication : Application()
