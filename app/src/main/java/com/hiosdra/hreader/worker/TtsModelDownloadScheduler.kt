package com.hiosdra.hreader.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hiosdra.hreader.data.tts.TtsModel
import com.hiosdra.hreader.data.tts.TtsModelManager
import java.util.concurrent.TimeUnit

class TtsModelDownloadScheduler(
    context: Context,
    private val modelManager: TtsModelManager
) {
    private val appContext = context.applicationContext

    fun enqueueDownload(model: TtsModel) {
        if (model.bundled) return
        modelManager.markDownloadEnqueued(model)
        val request = OneTimeWorkRequestBuilder<TtsModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(TtsModelDownloadWorker.KEY_MODEL to model.name))
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            downloadWorkName(model),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelDownload(model: TtsModel) {
        if (model.bundled) return
        WorkManager.getInstance(appContext).cancelUniqueWork(downloadWorkName(model))
        modelManager.markDownloadCancelled(model)
    }
}

private fun downloadWorkName(model: TtsModel): String = "DownloadTtsModel:${model.name}"
