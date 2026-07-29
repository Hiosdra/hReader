package com.hiosdra.hreader.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hiosdra.hreader.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

private const val CONTENT_SYNC_WORK = "ContentSyncWorker"
private const val ARTICLE_CONTENT_SYNC_WORK = "ArticleContentSync"
private const val LEGACY_ARTICLE_CONTENT_SYNC_WORK = "ArticleContentSyncWorker"
private const val CHAINED_SYNC_WORK = "OnExitChainedSync"
private const val OFFLINE_PREPARATION_WORK = "PrepareForOffline"

private const val BACKOFF_DELAY_SECONDS = 30L
private const val CHAINED_SYNC_THROTTLE_MILLIS = 2 * 60 * 1000L

internal const val KEY_FORCE_FULL_SYNC = "force_full_sync"
internal const val KEY_PREFETCH_CHAINED = "prefetch_chained"

/**
 * Every enqueue point in the app, so they cannot drift apart on constraints. They did: the periodic
 * registration required a network while the one the activity enqueued on exit did not, which meant
 * a week offline spent five retries and a fistful of radio wakeups every time the app was closed.
 */
class SyncScheduler(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodicSync() {
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
     * Prefetching runs after a sync rather than on its own schedule: on an independent timer it
     * regularly fired against the previous article set and re-fetched nothing useful.
     */
    fun enqueuePrefetch() {
        // REPLACE: a prefetch still queued from the previous sync is working off a stale article set.
        workManager.enqueueUniqueWork(
            ARTICLE_CONTENT_SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            prefetchRequest()
        )
    }

    /** Sync then prefetch when the app goes to the background, at most once every two minutes. */
    fun enqueueBackgroundSyncChain() {
        val now = System.currentTimeMillis()
        // Held in preferences rather than in memory: the throttle used to live in a static field,
        // which reset on every process death and let the chain run far more often than intended.
        if (now - preferencesManager.getLastChainedSyncTimestamp() < CHAINED_SYNC_THROTTLE_MILLIS) return
        preferencesManager.setLastChainedSyncTimestamp(now)

        workManager
            .beginUniqueWork(CHAINED_SYNC_WORK, ExistingWorkPolicy.REPLACE, syncRequest(forceFullSync = false))
            .then(prefetchRequest())
            .enqueue()
    }

    /**
     * A full sync followed by a prefetch of everything, for the reader about to lose connectivity.
     * Forced full rather than incremental: an incremental run right after a recent sync would
     * fetch nothing, which is the opposite of what "prepare for offline" is asked to do.
     */
    fun prepareForOffline() {
        workManager
            .beginUniqueWork(
                OFFLINE_PREPARATION_WORK,
                ExistingWorkPolicy.REPLACE,
                syncRequest(forceFullSync = true)
            )
            .then(prefetchRequest())
            .enqueue()
    }

    fun isPreparingForOffline(): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(OFFLINE_PREPARATION_WORK)
            .map { infos -> infos.any { !it.state.isFinished } }

    private fun syncRequest(forceFullSync: Boolean) =
        OneTimeWorkRequestBuilder<ContentSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putBoolean(KEY_FORCE_FULL_SYNC, forceFullSync)
                    .putBoolean(KEY_PREFETCH_CHAINED, true)
                    .build()
            )
            .build()

    private fun prefetchRequest() =
        OneTimeWorkRequestBuilder<ArticleContentSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
}
