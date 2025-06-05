package com.hiosdra.hreader

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hiosdra.hreader.worker.ArticleContentSyncWorker
import com.hiosdra.hreader.worker.ContentSyncWorker
import java.util.concurrent.TimeUnit

fun setupContentSyncWorker(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<ContentSyncWorker>(1, TimeUnit.HOURS)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "ContentSyncWorker",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}

fun setupArticleContentSyncWorker(context: Context) {
    val workRequest =
        PeriodicWorkRequestBuilder<ArticleContentSyncWorker>(1, TimeUnit.HOURS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "ArticleContentSyncWorker",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}
