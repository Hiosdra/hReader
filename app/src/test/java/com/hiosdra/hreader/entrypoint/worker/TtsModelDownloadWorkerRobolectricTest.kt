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
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
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
@Config(application = TtsModelDownloadWorkerRobolectricTestApplication::class, sdk = [35])
class TtsModelDownloadWorkerRobolectricTest {

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `unknown model returns a localized failure without downloading`() = runBlocking {
        val gateway = RecordingTtsModelGateway()
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            gateway = gateway,
            errorReporter = errorReporter,
            inputData = Data.Builder().putString(TtsModelDownloadWorker.KEY_MODEL, "missing").build()
        ).doWork()

        assertTrue(result is Failure)
        assertEquals(
            RuntimeEnvironment.getApplication().getString(R.string.tts_voice_download_unknown),
            result.outputData.getString(KEY_ERROR_MESSAGE)
        )
        assertEquals(0, gateway.downloadCalls)
        verify(exactly = 1) {
            errorReporter.captureMessage(
                RuntimeEnvironment.getApplication().getString(R.string.tts_voice_download_unknown),
                "tts_model_download"
            )
        }
    }

    @Test
    fun `bundled Android model completes without starting a download`() = runBlocking {
        val gateway = RecordingTtsModelGateway()

        val result = createWorker(
            gateway = gateway,
            inputData = Data.Builder()
                .putString(TtsModelDownloadWorker.KEY_MODEL, TtsModel.ANDROID.name)
                .build()
        ).doWork()

        assertTrue(result is Success)
        assertEquals(0, gateway.downloadCalls)
    }

    @Test
    fun `available downloaded model returns success`() = runBlocking {
        val gateway = RecordingTtsModelGateway(downloadStatus = TtsModelStatus.Available)

        val result = createWorker(
            gateway = gateway,
            inputData = modelInput(TtsModel.SUPERTONIC)
        ).doWork()

        assertTrue(result is Success)
        assertEquals(TtsModel.SUPERTONIC, gateway.downloadedModel)
    }

    @Test
    fun `download that does not produce an available model returns a localized failure`() = runBlocking {
        val gateway = RecordingTtsModelGateway(downloadStatus = TtsModelStatus.NotInstalled)
        val errorReporter = mockk<ErrorReporter>(relaxed = true)
        val expectedMessage = RuntimeEnvironment.getApplication()
            .getString(R.string.tts_voice_download_failed)

        val result = createWorker(
            gateway = gateway,
            errorReporter = errorReporter,
            inputData = modelInput(TtsModel.SUPERTONIC)
        ).doWork()

        assertTrue(result is Failure)
        assertEquals(expectedMessage, result.outputData.getString(KEY_ERROR_MESSAGE))
        verify(exactly = 1) { errorReporter.captureMessage(expectedMessage, "tts_model_download") }
    }

    @Test
    fun `transient download failure retries and keeps the model retrying`() = runBlocking {
        val gateway = RecordingTtsModelGateway(downloadFailure = IOException("connection lost"))
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            gateway = gateway,
            errorReporter = errorReporter,
            inputData = modelInput(TtsModel.SUPERTONIC)
        ).doWork()

        assertTrue(result is Retry)
        assertEquals(1, gateway.markRetryingCalls)
        verify(exactly = 0) { errorReporter.captureException(any(), any()) }
    }

    @Test
    fun `terminal download failure returns a localized failure and reports`() = runBlocking {
        val failure = IllegalArgumentException("invalid archive")
        val gateway = RecordingTtsModelGateway(downloadFailure = failure)
        val errorReporter = mockk<ErrorReporter>(relaxed = true)

        val result = createWorker(
            gateway = gateway,
            errorReporter = errorReporter,
            inputData = modelInput(TtsModel.SUPERTONIC),
            runAttemptCount = 5
        ).doWork()

        assertTrue(result is Failure)
        assertEquals(
            RuntimeEnvironment.getApplication().getString(R.string.tts_voice_download_failed),
            result.outputData.getString(KEY_ERROR_MESSAGE)
        )
        assertEquals(1, gateway.markFailedCalls)
        verify(exactly = 1) { errorReporter.captureException(failure, "tts_model_download") }
    }

    private fun createWorker(
        gateway: RecordingTtsModelGateway,
        errorReporter: ErrorReporter = mockk(relaxed = true),
        inputData: Data = Data.Builder().build(),
        runAttemptCount: Int = 0
    ): TtsModelDownloadWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? = if (workerClassName == TtsModelDownloadWorker::class.java.name) {
                TtsModelDownloadWorker(
                    appContext = appContext,
                    params = workerParameters,
                    modelManager = gateway,
                    errorReportingManager = errorReporter
                )
            } else {
                null
            }
        }
        return TestListenableWorkerBuilder.from(
            RuntimeEnvironment.getApplication(),
            TtsModelDownloadWorker::class.java
        )
            .setWorkerFactory(factory)
            .setInputData(inputData)
            .setRunAttemptCount(runAttemptCount)
            .build()
    }

    private fun modelInput(model: TtsModel): Data = Data.Builder()
        .putString(TtsModelDownloadWorker.KEY_MODEL, model.name)
        .build()
}

private class RecordingTtsModelGateway(
    private val downloadStatus: TtsModelStatus = TtsModelStatus.NotInstalled,
    private val downloadFailure: Throwable? = null
) : TtsModelGateway {
    private val _statuses = MutableStateFlow(
        TtsModelCatalog.models.associateWith<TtsModel, TtsModelStatus> { TtsModelStatus.NotInstalled }
    )
    override val statuses: StateFlow<Map<TtsModel, TtsModelStatus>> = _statuses
    var downloadCalls = 0
    var downloadedModel: TtsModel? = null
    var markRetryingCalls = 0
    var markFailedCalls = 0

    override fun markDownloadEnqueued(model: TtsModel) {
        _statuses.value = _statuses.value + (model to TtsModelStatus.Downloading(0f))
    }

    override fun markDownloadCancelled(model: TtsModel) = Unit

    override fun markDownloadFailed(model: TtsModel, message: String) {
        markFailedCalls += 1
        _statuses.value = _statuses.value + (model to TtsModelStatus.Failed(message))
    }

    override fun markDownloadRetrying(model: TtsModel) {
        markRetryingCalls += 1
    }

    override suspend fun download(model: TtsModel) {
        downloadCalls += 1
        downloadedModel = model
        downloadFailure?.let { throw it }
        _statuses.value = _statuses.value + (model to downloadStatus)
    }

    override suspend fun remove(model: TtsModel) = Unit
}

private class TtsModelDownloadWorkerRobolectricTestApplication : Application()
