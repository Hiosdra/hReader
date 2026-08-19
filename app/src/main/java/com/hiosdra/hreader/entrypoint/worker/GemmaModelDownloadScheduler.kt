package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hiosdra.hreader.core.application.port.out.GemmaModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
import java.util.concurrent.TimeUnit

class GemmaModelDownloadScheduler(
    context: Context,
    private val modelManager: GemmaModelGateway
) : GemmaModelDownloadRequester {
    private val appContext = context.applicationContext

    override fun enqueueDownload() {
        modelManager.markDownloadEnqueued()
        val request = OneTimeWorkRequestBuilder<GemmaModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            DOWNLOAD_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    override fun cancelDownload() {
        WorkManager.getInstance(appContext).cancelUniqueWork(DOWNLOAD_WORK_NAME)
        modelManager.markDownloadCancelled()
    }
}

private const val DOWNLOAD_WORK_NAME = "DownloadGemma4E2bModel"
