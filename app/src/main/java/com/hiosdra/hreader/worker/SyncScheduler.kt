package com.hiosdra.hreader.worker

import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
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
private const val REQUESTED_SYNC_WORK = "RequestedSync"

private const val BACKOFF_DELAY_SECONDS = 30L
private const val CHAINED_SYNC_THROTTLE_MILLIS = 2 * 60 * 1000L

internal const val KEY_FORCE_FULL_SYNC = "force_full_sync"
internal const val KEY_PREFETCH_CHAINED = "prefetch_chained"
internal const val KEY_IGNORE_QUIET_HOURS = "ignore_quiet_hours"
internal const val KEY_DRAIN_REMAINING = "drain_remaining"
internal const val KEY_PROGRESS_DONE = "progress_done"
internal const val KEY_PROGRESS_TOTAL = "progress_total"

/** How far along a "prepare for offline" run is, for the screen that started it. */
data class OfflinePreparationProgress(
    val isRunning: Boolean = false,
    val done: Int = 0,
    val total: Int = 0
)

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

    /**
     * Built per enqueue rather than once: the reader can change the metered and roaming rules at
     * any time, and a cached Constraints would keep scheduling against the old ones.
     *
     * Roaming is expressed as a capability on a [NetworkRequest] because [NetworkType] has no term
     * for it. `NOT_ROAMING` needs API 28 and the app requires 29.
     */
    private fun networkConstraints(): Constraints {
        val unmeteredOnly = preferencesManager.getSyncOnUnmeteredOnly()
        val networkType = if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val builder = Constraints.Builder()

        if (preferencesManager.getSyncWhileRoaming()) {
            builder.setRequiredNetworkType(networkType)
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                .apply {
                    if (unmeteredOnly) addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
                .build()
            // The NetworkType is kept as the fallback for platforms that cannot honour the request,
            // so the constraint degrades to "any connection" rather than to none at all.
            builder.setRequiredNetworkRequest(request, networkType)
        }
        return builder.build()
    }

    /**
     * Registers the periodic sync, or cancels it when there is nothing to sync against. Every
     * caller — app start, a changed setting, signing out — goes through here, so signing out stops
     * the worker instead of leaving it waking the radio hourly to fail on a missing token, and
     * configuring an account again brings it back without waiting for the next launch.
     */
    fun schedulePeriodicSync() {
        workManager.cancelUniqueWork(LEGACY_ARTICLE_CONTENT_SYNC_WORK)

        if (!preferencesManager.hasBackendCredentials()) {
            workManager.cancelUniqueWork(CONTENT_SYNC_WORK)
            return
        }

        val workRequest = PeriodicWorkRequestBuilder<ContentSyncWorker>(
            preferencesManager.getSyncIntervalMinutes().toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        // UPDATE rather than KEEP so installs that registered this worker without constraints pick
        // the new ones up instead of keeping the old registration forever. It is also what applies
        // a changed interval or a newly turned-on Wi-Fi-only rule.
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

    /**
     * A sync the reader has just asked for by doing something — switching backend, finishing setup
     * — rather than one the clock asked for. Unthrottled, because it answers an action.
     */
    fun syncNow(forceFullSync: Boolean = false) {
        if (!preferencesManager.hasBackendCredentials()) return
        workManager
            .beginUniqueWork(REQUESTED_SYNC_WORK, ExistingWorkPolicy.REPLACE, syncRequest(forceFullSync))
            .then(prefetchRequest())
            .enqueue()
    }

    /** Stops everything in flight. What is queued has no account left to run against. */
    fun cancelAllSync() {
        listOf(
            CONTENT_SYNC_WORK,
            ARTICLE_CONTENT_SYNC_WORK,
            CHAINED_SYNC_WORK,
            OFFLINE_PREPARATION_WORK,
            REQUESTED_SYNC_WORK
        ).forEach(workManager::cancelUniqueWork)
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
     *
     * Expedited and exempt from quiet hours, because someone is standing in front of the screen
     * waiting for it. As ordinary background work it could sit queued past the train leaving.
     *
     * This is also the one caller that asks the prefetch to keep going until the queue is empty.
     * A run downloads a bounded number of articles so that it finishes inside the time WorkManager
     * allows; here the reader has asked for the whole cache, so what a run leaves over is worth
     * another run rather than the next hour's.
     */
    fun prepareForOffline() {
        workManager
            .beginUniqueWork(
                OFFLINE_PREPARATION_WORK,
                ExistingWorkPolicy.REPLACE,
                syncRequest(forceFullSync = true, expedited = true, ignoreQuietHours = true)
            )
            .then(prefetchRequest(expedited = true, ignoreQuietHours = true, drainRemaining = true))
            .enqueue()
    }

    /**
     * The prefetch stage reports how many articles it has stored, so the settings screen can show
     * progress rather than an indeterminate spinner of unknown length.
     */
    fun observeOfflinePreparation(): Flow<OfflinePreparationProgress> =
        workManager.getWorkInfosForUniqueWorkFlow(OFFLINE_PREPARATION_WORK).map { infos ->
            val progress = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.progress
            OfflinePreparationProgress(
                isRunning = infos.any { !it.state.isFinished },
                done = progress?.getInt(KEY_PROGRESS_DONE, 0) ?: 0,
                total = progress?.getInt(KEY_PROGRESS_TOTAL, 0) ?: 0
            )
        }

    private fun syncRequest(
        forceFullSync: Boolean,
        expedited: Boolean = false,
        ignoreQuietHours: Boolean = false
    ) = OneTimeWorkRequestBuilder<ContentSyncWorker>()
        .setConstraints(networkConstraints())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
        .setInputData(
            Data.Builder()
                .putBoolean(KEY_FORCE_FULL_SYNC, forceFullSync)
                .putBoolean(KEY_PREFETCH_CHAINED, true)
                .putBoolean(KEY_IGNORE_QUIET_HOURS, ignoreQuietHours)
                .build()
        )
        .apply { if (expedited) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
        .build()

    private fun prefetchRequest(
        expedited: Boolean = false,
        ignoreQuietHours: Boolean = false,
        drainRemaining: Boolean = false
    ) =
        OneTimeWorkRequestBuilder<ArticleContentSyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putBoolean(KEY_IGNORE_QUIET_HOURS, ignoreQuietHours)
                    .putBoolean(KEY_DRAIN_REMAINING, drainRemaining)
                    .build()
            )
            .apply { if (expedited) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .build()
}
