package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
import com.hiosdra.hreader.entrypoint.notification.AppNotificationFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MAX_RUN_ATTEMPTS = 5
private const val TAG = "TtsModelDownloadWorker"

class TtsModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val modelManager: TtsModelGateway,
    private val errorReportingManager: ErrorReporter
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_MODEL = "model"
        private const val KEY_PROGRESS = "progress"
        private const val REPORT_INTERVAL_MILLIS = 500L
    }

    private val model: TtsModel?
        get() = inputData.getString(KEY_MODEL)?.let { name ->
            TtsModel.entries.firstOrNull { it.name == name }
        }

    private var foregroundUnavailable = false

    override suspend fun getForegroundInfo(): ForegroundInfo =
        AppNotificationFactory.modelDownloadForegroundInfo(
            context = applicationContext,
            workerId = id,
            modelName = model?.let { applicationContext.getString(it.displayNameRes) }
                ?: applicationContext.getString(R.string.tts_voice_model_default),
            progress = modelProgress()
        )

    override suspend fun doWork(): Result = coroutineScope {
        val selectedModel = model
        if (selectedModel == null) {
            val message = applicationContext.getString(R.string.tts_voice_download_unknown)
            errorReportingManager.captureMessage(message, "tts_model_download")
            return@coroutineScope Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
        }
        if (selectedModel.bundled) return@coroutineScope Result.success()

        var reporter: Job? = null
        try {
            updateForeground()
            reporter = launch {
                while (isActive) {
                    val progress = modelProgress()
                    setProgress(workDataOf(KEY_PROGRESS to progress))
                    updateForeground()
                    delay(REPORT_INTERVAL_MILLIS)
                }
            }
            modelManager.download(selectedModel)
            if (modelManager.statuses.value[selectedModel] == TtsModelStatus.Available) {
                Result.success()
            } else {
                val message = applicationContext.getString(R.string.tts_voice_download_failed)
                errorReportingManager.captureMessage(message, "tts_model_download")
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
            }
        } catch (e: CancellationException) {
            modelManager.markDownloadCancelled(selectedModel)
            throw e
        } catch (e: Exception) {
            modelManager.markDownloadFailed(
                selectedModel,
                applicationContext.getString(R.string.tts_voice_download_failed)
            )
            val shouldRetry = e.isRetryable() && runAttemptCount < MAX_RUN_ATTEMPTS
            if (!shouldRetry) errorReportingManager.captureException(e, "tts_model_download")
            if (shouldRetry) {
                modelManager.markDownloadRetrying(selectedModel)
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        KEY_ERROR_MESSAGE to applicationContext.getString(R.string.tts_voice_download_failed)
                    )
                )
            }
        } finally {
            reporter?.cancel()
        }
    }

    private suspend fun updateForeground() {
        if (foregroundUnavailable) return
        if (!setForegroundIfAllowed({ getForegroundInfo() }, { setForeground(it) })) {
            foregroundUnavailable = true
            Log.w(TAG, "Foreground notification unavailable; continuing without it")
        }
    }

    private fun modelProgress(): Float =
        (model?.let { modelManager.statuses.value[it] } as? TtsModelStatus.Downloading)?.progress ?: 0f
}
