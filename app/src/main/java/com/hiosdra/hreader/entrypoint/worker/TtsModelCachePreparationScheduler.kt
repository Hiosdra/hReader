package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.TtsModelCacheGateway
import com.hiosdra.hreader.core.application.port.out.TtsModelCacheRequester
import com.hiosdra.hreader.core.application.tts.MnnTtsBackend
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsEngineFamily
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCacheStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class TtsModelCachePreparationScheduler(
    context: Context,
    private val cacheGateway: TtsModelCacheGateway,
    private val workManagerProvider: (Context) -> WorkManager = { appContext ->
        WorkManager.getInstance(appContext)
    }
) : TtsModelCacheRequester {
    private val appContext = context.applicationContext

    override fun observe(model: TtsModel, backend: MnnTtsBackend): Flow<TtsModelCacheStatus> =
        workManagerProvider(appContext)
            .getWorkInfosForUniqueWorkFlow(cacheWorkName(model, backend))
            .map { infos ->
                ttsModelCacheStatus(
                    infos = infos,
                    cacheReady = cacheGateway.isReady(model, backend),
                    fallbackErrorMessage = appContext.getString(R.string.tts_cache_prepare_failed)
                )
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    override fun enqueuePreparation(
        model: TtsModel,
        settings: TtsAdvancedSettings,
        forceRefresh: Boolean
    ) {
        if (model.family != TtsEngineFamily.MNN) return
        val request = OneTimeWorkRequestBuilder<TtsModelCachePreparationWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setInputData(
                workDataOf(
                    TtsModelCachePreparationWorker.KEY_MODEL to model.name,
                    TtsModelCachePreparationWorker.KEY_BACKEND to settings.mnnBackend.wireName,
                    TtsModelCachePreparationWorker.KEY_THREADS to settings.numThreads,
                    TtsModelCachePreparationWorker.KEY_FORCE_REFRESH to forceRefresh
                )
            )
            .build()
        workManagerProvider(appContext).enqueueUniqueWork(
            cacheWorkName(model, settings.mnnBackend),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancelPreparation(model: TtsModel, backend: MnnTtsBackend) {
        workManagerProvider(appContext).cancelUniqueWork(cacheWorkName(model, backend))
    }
}

internal fun ttsModelCacheStatus(
    infos: List<WorkInfo>,
    cacheReady: Boolean,
    fallbackErrorMessage: String = "Could not prepare the voice cache."
): TtsModelCacheStatus {
    val activeWork = infos.firstOrNull {
        it.state == WorkInfo.State.RUNNING ||
            it.state == WorkInfo.State.ENQUEUED ||
            it.state == WorkInfo.State.BLOCKED
    }
    if (activeWork != null) {
        return TtsModelCacheStatus.Preparing(
            activeWork.progress.getFloat(TtsModelCachePreparationWorker.KEY_PROGRESS, 0f)
        )
    }
    if (cacheReady) return TtsModelCacheStatus.Ready
    val failedWork = infos.lastOrNull { it.state == WorkInfo.State.FAILED }
    if (failedWork != null) {
        return TtsModelCacheStatus.Failed(
            failedWork.outputData.getString(KEY_ERROR_MESSAGE)
                ?: fallbackErrorMessage
        )
    }
    return TtsModelCacheStatus.NotPrepared
}

internal fun cacheWorkName(model: TtsModel, backend: MnnTtsBackend): String =
    "PrepareTtsCache:${model.name}:${backend.wireName}"
