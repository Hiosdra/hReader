package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.TtsModelCacheGateway
import com.hiosdra.hreader.core.application.port.out.TtsModelCachePreparer
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.tts.MnnTtsBackend
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsEngineFamily
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
import com.hiosdra.hreader.entrypoint.notification.AppNotificationFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "TtsCachePreparationWorker"

internal class TtsModelCachePreparationWorker(
    appContext: Context,
    params: WorkerParameters,
    private val modelManager: TtsModelGateway,
    private val cacheGateway: TtsModelCacheGateway,
    private val cachePreparer: TtsModelCachePreparer,
    private val errorReporter: ErrorReporter
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_MODEL = "model"
        const val KEY_BACKEND = "backend"
        const val KEY_THREADS = "threads"
        const val KEY_FORCE_REFRESH = "force_refresh"
        const val KEY_PROGRESS = "progress"
    }

    private var progress = 0f
    private var foregroundUnavailable = false

    private val model: TtsModel?
        get() = inputData.getString(KEY_MODEL)?.let { name ->
            TtsModelCatalog.models.firstOrNull { it.name == name }
        }

    private val backend: MnnTtsBackend?
        get() = MnnTtsBackend.entries.firstOrNull {
            it.wireName == inputData.getString(KEY_BACKEND)
        }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val selectedModel = model
        val selectedBackend = backend
        return AppNotificationFactory.ttsCachePreparationForegroundInfo(
            context = applicationContext,
            workerId = id,
            modelName = selectedModel?.let { applicationContext.getString(it.displayNameRes) }
                ?: applicationContext.getString(R.string.tts_voice_model_default),
            backendName = selectedBackend?.let { applicationContext.getString(it.displayNameRes) }
                ?: applicationContext.getString(R.string.tts_mnn_backend_cpu),
            progress = progress
        )
    }

    override suspend fun doWork(): Result {
        val selectedModel = model
        val selectedBackend = backend
        if (selectedModel == null || selectedBackend == null) {
            return failure(R.string.tts_cache_prepare_invalid)
        }
        if (selectedModel.family != TtsEngineFamily.MNN ||
            modelManager.statuses.value[selectedModel] != TtsModelStatus.Available
        ) {
            return failure(R.string.tts_cache_prepare_model_unavailable)
        }

        return try {
            updateProgress(0.05f)
            updateProgress(0.1f)
            updateForeground()
            val settings = TtsAdvancedSettings(
                numThreads = inputData.getInt(KEY_THREADS, 4).coerceIn(1, 4),
                mnnBackend = selectedBackend
            )
            val effectiveBackend = withContext(Dispatchers.Default) {
                cachePreparer.prepareCache(
                    model = selectedModel,
                    settings = settings,
                    forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)
                )
            }
            updateProgress(0.95f)
            updateForeground()
            check(cacheGateway.isReady(selectedModel, effectiveBackend)) {
                "MNN runtime did not produce a complete cache"
            }
            if (effectiveBackend != selectedBackend) {
                Result.failure(
                    workDataOf(
                        KEY_ERROR_MESSAGE to applicationContext.getString(
                            R.string.tts_cache_prepare_backend_fallback,
                            applicationContext.getString(selectedBackend.displayNameRes),
                            applicationContext.getString(effectiveBackend.displayNameRes)
                        )
                    )
                )
            } else {
                updateProgress(1f)
                updateForeground()
                Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "TTS cache preparation failed", error)
            errorReporter.captureException(error, "tts_cache_prepare")
            Result.failure(
                workDataOf(
                    KEY_ERROR_MESSAGE to applicationContext.getString(R.string.tts_cache_prepare_failed)
                )
            )
        }
    }

    private suspend fun updateProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        setProgress(workDataOf(KEY_PROGRESS to progress))
    }

    private suspend fun updateForeground() {
        if (foregroundUnavailable) return
        if (!setForegroundIfAllowed({ getForegroundInfo() }, { setForeground(it) })) {
            foregroundUnavailable = true
            Log.w(TAG, "Foreground notification unavailable; continuing without it")
        }
    }

    private fun failure(messageRes: Int): Result =
        Result.failure(workDataOf(KEY_ERROR_MESSAGE to applicationContext.getString(messageRes)))
}

private val MnnTtsBackend.displayNameRes: Int
    get() = when (this) {
        MnnTtsBackend.CPU -> R.string.tts_mnn_backend_cpu
        MnnTtsBackend.OPENCL -> R.string.tts_mnn_backend_opencl
        MnnTtsBackend.VULKAN -> R.string.tts_mnn_backend_vulkan
    }
