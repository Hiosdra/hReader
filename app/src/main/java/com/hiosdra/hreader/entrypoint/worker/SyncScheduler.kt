package com.hiosdra.hreader.entrypoint.worker

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
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import com.hiosdra.hreader.core.application.port.out.BackendPreferences
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.OfflinePreparationStage
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val CONTENT_SYNC_WORK = "ContentSyncWorker"
private const val SYNC_PIPELINE_WORK = "SyncPipeline"
private const val OFFLINE_PREPARATION_TAG = "OfflinePreparation"
private const val FULL_OFFLINE_PREPARATION_TAG = "FullOfflinePreparation"
private const val OFFLINE_SYNC_STAGE_TAG = "OfflineSyncStage"
private const val OFFLINE_CONTENT_STAGE_TAG = "OfflineContentStage"
private const val OFFLINE_PAGES_STAGE_TAG = "OfflinePagesStage"

private const val BACKOFF_DELAY_SECONDS = 30L
private const val CHAINED_SYNC_THROTTLE_MILLIS = 2 * 60 * 1000L

internal const val KEY_FORCE_FULL_SYNC = "force_full_sync"
internal const val KEY_PREFETCH_CHAINED = "prefetch_chained"
internal const val KEY_IGNORE_QUIET_HOURS = "ignore_quiet_hours"
internal const val KEY_DRAIN_REMAINING = "drain_remaining"
internal const val KEY_PROGRESS_DONE = "progress_done"
internal const val KEY_PROGRESS_TOTAL = "progress_total"
internal const val KEY_USER_VISIBLE = "user_visible"
internal const val KEY_OPERATION_TITLE = "operation_title"
internal const val KEY_ERROR_MESSAGE = "error_message"

internal fun offlinePreparationStage(tags: Set<String>): OfflinePreparationStage = when {
    OFFLINE_SYNC_STAGE_TAG in tags -> OfflinePreparationStage.SYNCING
    OFFLINE_CONTENT_STAGE_TAG in tags -> OfflinePreparationStage.DOWNLOADING_CONTENT
    OFFLINE_PAGES_STAGE_TAG in tags -> OfflinePreparationStage.ARCHIVING_PAGES
    else -> OfflinePreparationStage.IDLE
}

/**
 * Every enqueue point in the app, so they cannot drift apart on constraints. They did: the periodic
 * registration required a network while the one the activity enqueued on exit did not, which meant
 * a week offline spent five retries and a fistful of radio wakeups every time the app was closed.
 */
class SyncScheduler(
    private val context: Context,
    private val backendPreferences: BackendPreferences,
    private val syncPreferences: SyncPreferences,
    private val networkMonitor: NetworkStatus,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val workManagerProvider: (Context) -> WorkManager = { appContext ->
        WorkManager.getInstance(appContext)
    }
) : SyncRequester {
    private var connectivityObservationStarted = false

    private val workManager: WorkManager
        get() = workManagerProvider(context)

    override fun start() {
        if (connectivityObservationStarted) return
        connectivityObservationStarted = true
        networkMonitor.isOnline
            .drop(1)
            .distinctUntilChanged()
            .filter { it }
            .onEach { if (backendPreferences.hasBackendCredentials()) syncNow() }
            .launchIn(scope)
    }

    /**
     * Built per enqueue rather than once: the reader can change the metered and roaming rules at
     * any time, and a cached Constraints would keep scheduling against the old ones.
     *
     * Roaming is expressed as a capability on a [NetworkRequest] because [NetworkType] has no term
     * for it. `NOT_ROAMING` needs API 28 and the app requires 29.
     */
    private fun networkConstraints(
        avoidLowStorage: Boolean = false,
        avoidLowBattery: Boolean = false
    ): Constraints {
        val unmeteredOnly = syncPreferences.getSyncOnUnmeteredOnly()
        val networkType = if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val builder = Constraints.Builder()

        if (syncPreferences.getSyncWhileRoaming()) {
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
        if (avoidLowStorage) {
            builder.setRequiresStorageNotLow(true)
        }
        if (avoidLowBattery) {
            builder.setRequiresBatteryNotLow(true)
        }
        return builder.build()
    }

    /**
     * Registers the periodic sync, or cancels it when there is nothing to sync against. Every
     * caller — app start, a changed setting, signing out — goes through here, so signing out stops
     * the worker instead of leaving it waking the radio hourly to fail on a missing token, and
     * configuring an account again brings it back without waiting for the next launch.
     */
    override fun schedulePeriodicSync() {
        if (!backendPreferences.hasBackendCredentials()) {
            workManager.cancelUniqueWork(CONTENT_SYNC_WORK)
            return
        }

        val workRequest = PeriodicWorkRequestBuilder<ContentSyncWorker>(
            syncPreferences.getSyncIntervalMinutes().toLong(),
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
    override fun enqueuePrefetch() {
        workManager.enqueueUniqueWork(
            SYNC_PIPELINE_WORK,
            ExistingWorkPolicy.KEEP,
            prefetchRequest()
        )
    }

    /**
     * A sync the reader has just asked for by doing something — switching backend, finishing setup
     * — rather than one the clock asked for. Unthrottled, because it answers an action.
     */
    override fun syncNow(
        forceFullSync: Boolean,
        userVisible: Boolean,
        operationTitle: String?
    ): UUID? {
        if (!backendPreferences.hasBackendCredentials()) return null
        val resolvedOperationTitle = operationTitle ?: context.getString(R.string.notification_sync_title)
        val syncWork = syncRequest(
            forceFullSync = forceFullSync,
            expedited = userVisible,
            ignoreQuietHours = userVisible,
            userVisible = userVisible,
            operationTitle = resolvedOperationTitle
        )
        workManager
            .beginUniqueWork(
                SYNC_PIPELINE_WORK,
                ExistingWorkPolicy.REPLACE,
                syncWork
            )
            .then(
                prefetchRequest(
                    expedited = userVisible,
                    ignoreQuietHours = userVisible,
                    userVisible = userVisible,
                    operationTitle = resolvedOperationTitle,
                    offlinePreparation = false
                )
            )
            .enqueue()
        return syncWork.id
    }

    override fun resyncNow(): UUID? = syncNow(
        forceFullSync = true,
        userVisible = true,
        operationTitle = context.getString(R.string.notification_resync_title)
    )

    override fun observeRequestedSync(): Flow<SyncOperationStatus> =
        observeSyncPipeline().map { it.status }

    /** Stops everything in flight. What is queued has no account left to run against. */
    override fun cancelAllSync() {
        listOf(
            CONTENT_SYNC_WORK,
            SYNC_PIPELINE_WORK
        ).forEach(workManager::cancelUniqueWork)
    }

    /** Sync then prefetch when the app goes to the background, at most once every two minutes. */
    override fun enqueueBackgroundSyncChain() {
        if (!backendPreferences.hasBackendCredentials()) return
        val now = System.currentTimeMillis()
        // Held in preferences rather than in memory: the throttle used to live in a static field,
        // which reset on every process death and let the chain run far more often than intended.
        if (now - syncPreferences.getLastChainedSyncTimestamp() < CHAINED_SYNC_THROTTLE_MILLIS) return
        syncPreferences.setLastChainedSyncTimestamp(now)

        workManager
            .beginUniqueWork(SYNC_PIPELINE_WORK, ExistingWorkPolicy.KEEP, syncRequest(forceFullSync = false))
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
    override fun prepareForOffline(): UUID? {
        if (!backendPreferences.hasBackendCredentials()) return null
        val syncWork = syncRequest(
            forceFullSync = true,
            expedited = true,
            ignoreQuietHours = true,
            userVisible = true,
            operationTitle = context.getString(R.string.notification_offline_title),
            offlinePreparation = true
        )
        workManager
            .beginUniqueWork(
                SYNC_PIPELINE_WORK,
                ExistingWorkPolicy.REPLACE,
                syncWork
            )
            .then(
                prefetchRequest(
                    expedited = true,
                    ignoreQuietHours = true,
                    drainRemaining = true,
                    userVisible = true,
                    operationTitle = context.getString(R.string.notification_offline_title),
                    offlinePreparation = true
                )
            )
            .enqueue()
        return syncWork.id
    }

    override fun prepareFullOffline(): UUID? {
        if (!backendPreferences.hasBackendCredentials()) return null
        val syncWork = syncRequest(
            forceFullSync = true,
            expedited = true,
            ignoreQuietHours = true,
            userVisible = true,
            operationTitle = context.getString(R.string.notification_full_offline_title),
            offlinePreparation = true,
            fullOfflinePreparation = true
        )
        workManager
            .beginUniqueWork(
                SYNC_PIPELINE_WORK,
                ExistingWorkPolicy.REPLACE,
                syncWork
            )
            .then(
                prefetchRequest(
                    expedited = true,
                    ignoreQuietHours = true,
                    drainRemaining = true,
                    userVisible = true,
                    operationTitle = context.getString(R.string.notification_full_offline_title),
                    offlinePreparation = true,
                    fullOfflinePreparation = true
                )
            )
            .then(
                fullPageRequest(
                    expedited = true,
                    ignoreQuietHours = true,
                    userVisible = true,
                    operationTitle = context.getString(R.string.notification_full_offline_title)
                )
            )
            .enqueue()
        return syncWork.id
    }

    /**
     * The prefetch stage reports how many articles it has stored, so the settings screen can show
     * progress rather than an indeterminate spinner of unknown length.
     */
    override fun observeOfflinePreparation(): Flow<OfflinePreparationProgress> =
        observeSyncPipeline(offlineOnly = true)

    private fun observeSyncPipeline(offlineOnly: Boolean = false): Flow<OfflinePreparationProgress> =
        workManager.getWorkInfosForUniqueWorkFlow(SYNC_PIPELINE_WORK).map { infos ->
            val operationInfos = if (offlineOnly) {
                infos.filter { OFFLINE_PREPARATION_TAG in it.tags }
            } else {
                infos
            }
            val activeWork = operationInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                ?: operationInfos.firstOrNull { it.state == WorkInfo.State.ENQUEUED }
                ?: operationInfos.firstOrNull { it.state == WorkInfo.State.BLOCKED }
                ?: operationInfos.lastOrNull()
            val progress = activeWork?.progress
            val status = operationStatus(operationInfos)
            val isFullOffline = activeWork?.let { FULL_OFFLINE_PREPARATION_TAG in it.tags }
                ?: operationInfos.any { FULL_OFFLINE_PREPARATION_TAG in it.tags }
            OfflinePreparationProgress(
                isRunning = status.isRunning,
                done = progress?.getInt(KEY_PROGRESS_DONE, 0) ?: 0,
                total = progress?.getInt(KEY_PROGRESS_TOTAL, 0) ?: 0,
                status = status,
                isFullOffline = isFullOffline,
                stage = offlinePreparationStage(activeWork?.tags.orEmpty())
            )
        }

    private fun syncRequest(
        forceFullSync: Boolean,
        expedited: Boolean = false,
        ignoreQuietHours: Boolean = false,
        userVisible: Boolean = false,
        operationTitle: String = context.getString(R.string.notification_sync_title),
        offlinePreparation: Boolean = false,
        fullOfflinePreparation: Boolean = false
    ) = OneTimeWorkRequestBuilder<ContentSyncWorker>()
        .setConstraints(networkConstraints())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
        .setInputData(
            Data.Builder()
                .putBoolean(KEY_FORCE_FULL_SYNC, forceFullSync)
                .putBoolean(KEY_PREFETCH_CHAINED, true)
                .putBoolean(KEY_IGNORE_QUIET_HOURS, ignoreQuietHours)
                .putBoolean(KEY_USER_VISIBLE, userVisible)
                .putString(KEY_OPERATION_TITLE, operationTitle)
                .build()
        )
        .apply {
            if (expedited) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            if (offlinePreparation) {
                addTag(OFFLINE_PREPARATION_TAG)
                addTag(OFFLINE_SYNC_STAGE_TAG)
            }
            if (fullOfflinePreparation) addTag(FULL_OFFLINE_PREPARATION_TAG)
        }
        .build()

    private fun prefetchRequest(
        expedited: Boolean = false,
        ignoreQuietHours: Boolean = false,
        drainRemaining: Boolean = false,
        userVisible: Boolean = false,
        operationTitle: String = context.getString(R.string.notification_sync_title),
        offlinePreparation: Boolean = false,
        fullOfflinePreparation: Boolean = false
    ) =
        OneTimeWorkRequestBuilder<ArticleContentSyncWorker>()
            .setConstraints(
                networkConstraints(
                    avoidLowStorage = true,
                    avoidLowBattery = !expedited
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putBoolean(KEY_IGNORE_QUIET_HOURS, ignoreQuietHours)
                    .putBoolean(KEY_DRAIN_REMAINING, drainRemaining)
                    .putBoolean(KEY_USER_VISIBLE, userVisible)
                    .putString(KEY_OPERATION_TITLE, operationTitle)
                    .build()
            )
            .apply {
                if (expedited) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                if (offlinePreparation) {
                    addTag(OFFLINE_PREPARATION_TAG)
                    addTag(OFFLINE_CONTENT_STAGE_TAG)
                }
                if (fullOfflinePreparation) addTag(FULL_OFFLINE_PREPARATION_TAG)
            }
            .build()

    private fun fullPageRequest(
        expedited: Boolean,
        ignoreQuietHours: Boolean,
        userVisible: Boolean,
        operationTitle: String
    ) = OneTimeWorkRequestBuilder<FullPageSyncWorker>()
        .setConstraints(
            networkConstraints(
                avoidLowStorage = true,
                avoidLowBattery = !expedited
            )
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
        .setInputData(
            Data.Builder()
                .putBoolean(KEY_IGNORE_QUIET_HOURS, ignoreQuietHours)
                .putBoolean(KEY_USER_VISIBLE, userVisible)
                .putString(KEY_OPERATION_TITLE, operationTitle)
                .build()
        )
        .apply {
            if (expedited) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            addTag(OFFLINE_PREPARATION_TAG)
            addTag(FULL_OFFLINE_PREPARATION_TAG)
            addTag(OFFLINE_PAGES_STAGE_TAG)
        }
        .build()
}

internal fun operationStatus(infos: List<WorkInfo>): SyncOperationStatus {
    val workIds = infos.map { it.id }.toSet()
    if (infos.isEmpty()) return SyncOperationStatus(workIds = workIds)
    val failed = infos.firstOrNull { it.state == WorkInfo.State.FAILED }
    if (failed != null) {
        return SyncOperationStatus(
            state = SyncOperationState.FAILED,
            errorMessage = failed.outputData.getString(KEY_ERROR_MESSAGE),
            workIds = workIds
        )
    }
    if (infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) {
        return SyncOperationStatus(state = SyncOperationState.RUNNING, workIds = workIds)
    }
    if (infos.any { it.state == WorkInfo.State.CANCELLED }) {
        return SyncOperationStatus(state = SyncOperationState.CANCELLED, workIds = workIds)
    }
    return SyncOperationStatus(state = SyncOperationState.SUCCEEDED, workIds = workIds)
}
