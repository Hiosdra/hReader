package com.hiosdra.hreader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hiosdra.hreader.data.remote.isRetryable
import com.hiosdra.hreader.data.tts.TtsModel
import com.hiosdra.hreader.data.tts.TtsModelManager
import com.hiosdra.hreader.data.tts.TtsModelStatus
import com.hiosdra.hreader.notification.AppNotificationFactory
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
    private val modelManager: TtsModelManager
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
            modelName = model?.displayName ?: "Voice model",
            progress = modelProgress()
        )

    override suspend fun doWork(): Result = coroutineScope {
        val selectedModel = model
            ?: return@coroutineScope Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Unknown voice model."))
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
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Could not install the voice model."))
            }
        } catch (e: CancellationException) {
            modelManager.markDownloadCancelled(selectedModel)
            throw e
        } catch (e: Exception) {
            modelManager.markDownloadFailed(
                selectedModel,
                e.message ?: "Voice model download failed."
            )
            if (e.isRetryable() && runAttemptCount < MAX_RUN_ATTEMPTS) {
                modelManager.markDownloadRetrying(selectedModel)
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Voice model download failed."))
                )
            }
        } finally {
            reporter?.cancel()
        }
    }

    private suspend fun updateForeground() {
        if (foregroundUnavailable) return
        if (!setForegroundIfAllowed { setForeground(getForegroundInfo()) }) {
            foregroundUnavailable = true
            Log.w(TAG, "Foreground notification unavailable; continuing without it")
        }
    }

    private fun modelProgress(): Float =
        (model?.let { modelManager.statuses.value[it] } as? TtsModelStatus.Downloading)?.progress ?: 0f
}
