package com.hiosdra.hreader.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val CONTENT_SYNC_WORK = "ContentSyncWorker"

/**
 * A new name on purpose: "ArticleContentSyncWorker" is still bound to the periodic registration
 * older installs enqueued, and reusing it would mean relying on how WorkManager reconciles a
 * one-time request against a periodic one under the same name. The old name is cancelled instead.
 */
private const val ARTICLE_CONTENT_SYNC_WORK = "ArticleContentSync"
private const val LEGACY_ARTICLE_CONTENT_SYNC_WORK = "ArticleContentSyncWorker"

private const val BACKOFF_DELAY_SECONDS = 30L

// Both workers only ever talk to the backend, so running them offline just burns a scheduled slot.
private val networkConstraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

fun setupContentSyncWorker(context: Context) {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelUniqueWork(LEGACY_ARTICLE_CONTENT_SYNC_WORK)

    val workRequest = PeriodicWorkRequestBuilder<ContentSyncWorker>(1, TimeUnit.HOURS)
        .setConstraints(networkConstraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
        .build()
    // UPDATE rather than KEEP so installs that registered this worker without constraints pick
    // the new ones up instead of keeping the old registration forever.
    workManager.enqueueUniquePeriodicWork(
        CONTENT_SYNC_WORK,
        ExistingPeriodicWorkPolicy.UPDATE,
        workRequest
    )
}

/**
 * Prefetching runs after a sync rather than on its own hourly schedule: on an independent timer it
 * regularly fired against the previous article set and re-fetched nothing useful.
 */
internal fun enqueueArticleContentSync(context: Context) {
    val workRequest = OneTimeWorkRequestBuilder<ArticleContentSyncWorker>()
        .setConstraints(networkConstraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
        .build()
    // REPLACE: a prefetch still queued from the previous sync is working off a stale article set.
    WorkManager.getInstance(context).enqueueUniqueWork(
        ARTICLE_CONTENT_SYNC_WORK,
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}
