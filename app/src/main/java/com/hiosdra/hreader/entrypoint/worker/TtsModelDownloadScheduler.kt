package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.port.out.TtsModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import java.util.concurrent.TimeUnit

class TtsModelDownloadScheduler(
    context: Context,
    private val modelManager: TtsModelGateway,
    private val workManagerProvider: (Context) -> WorkManager = { appContext ->
        WorkManager.getInstance(appContext)
    }
) : TtsModelDownloadRequester {
    private val appContext = context.applicationContext

    override fun enqueueDownload(model: TtsModel) {
        if (model.bundled) return
        modelManager.markDownloadEnqueued(model)
        val request = OneTimeWorkRequestBuilder<TtsModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(TtsModelDownloadWorker.KEY_MODEL to model.name))
            .build()
        workManagerProvider(appContext).enqueueUniqueWork(
            downloadWorkName(model),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    override fun cancelDownload(model: TtsModel) {
        if (model.bundled) return
        workManagerProvider(appContext).cancelUniqueWork(downloadWorkName(model))
        modelManager.markDownloadCancelled(model)
    }
}

private fun downloadWorkName(model: TtsModel): String = "DownloadTtsModel:${model.name}"
