package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.GemmaModelInsufficientStorageException
import com.hiosdra.hreader.core.application.ai.GemmaModelStatus
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
import com.hiosdra.hreader.entrypoint.notification.AppNotificationFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MAX_RUN_ATTEMPTS = 5
private const val TAG = "GemmaModelDownload"

class GemmaModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val modelManager: GemmaModelGateway,
    private val errorReporter: ErrorReporter
) : CoroutineWorker(appContext, params) {
    companion object {
        private const val KEY_PROGRESS = "progress"
        private const val REPORT_INTERVAL_MILLIS = 500L
    }

    private var foregroundUnavailable = false

    override suspend fun getForegroundInfo(): ForegroundInfo =
        AppNotificationFactory.aiModelDownloadForegroundInfo(
            context = applicationContext,
            workerId = id,
            modelName = applicationContext.getString(R.string.ai_gemma_model_name),
            progress = modelProgress()
        )

    override suspend fun doWork(): Result = coroutineScope {
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
            modelManager.download()
            if (modelManager.status.value == GemmaModelStatus.Available) {
                Result.success()
            } else {
                val message = modelFailureMessage()
                modelManager.markDownloadFailed(message)
                errorReporter.captureMessage(message, "gemma_model_download")
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
            }
        } catch (e: CancellationException) {
            modelManager.markDownloadCancelled()
            throw e
        } catch (e: Exception) {
            val shouldRetry = e.isRetryable() && runAttemptCount < MAX_RUN_ATTEMPTS
            if (!shouldRetry) {
                modelManager.markDownloadFailed(modelFailureMessage())
                if (e !is GemmaModelInsufficientStorageException) {
                    errorReporter.captureException(e, "gemma_model_download")
                }
            } else {
                modelManager.markDownloadRetrying()
            }
            Log.w(TAG, "Gemma model download failed", e)
            if (shouldRetry) Result.retry() else Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to modelFailureMessage())
            )
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
        (modelManager.status.value as? GemmaModelStatus.Downloading)?.progress ?: 0f

    private fun modelFailureMessage(): String =
        (modelManager.status.value as? GemmaModelStatus.Failed)?.message
            ?: applicationContext.getString(R.string.ai_model_install_failed)
}
