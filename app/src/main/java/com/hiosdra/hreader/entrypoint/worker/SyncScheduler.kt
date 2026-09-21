package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.AiProvider
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import com.hiosdra.hreader.core.application.port.out.BackendPreferences
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.OfflinePreparationStage
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import com.hiosdra.hreader.core.application.sync.SyncCoordinator
import com.hiosdra.hreader.core.application.sync.SyncIntent
import com.hiosdra.hreader.core.application.sync.SyncOperationId
import com.hiosdra.hreader.core.application.sync.SyncPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.UUID

private const val CONTENT_SYNC_WORK = "ContentSyncWorker"
private const val SYNC_PIPELINE_WORK = "SyncPipeline"
private const val MAINTENANCE_WORK = "SyncMaintenance"
private const val OFFLINE_PREPARATION_TAG = "OfflinePreparation"
private const val FULL_OFFLINE_PREPARATION_TAG = "FullOfflinePreparation"
private const val OFFLINE_SYNC_STAGE_TAG = "OfflineSyncStage"
private const val OFFLINE_CONTENT_STAGE_TAG = "OfflineContentStage"
private const val OFFLINE_PAGES_STAGE_TAG = "OfflinePagesStage"

private const val BACKOFF_DELAY_SECONDS = 30L
private const val CHAINED_SYNC_THROTTLE_MILLIS = 2 * 60 * 1000L

internal const val KEY_FORCE_FULL_SYNC = "force_full_sync"
internal const val KEY_ENQUEUE_PREFETCH = "enqueue_prefetch"
internal const val KEY_DOWNLOAD_ALL_IMAGES = "download_all_images"
internal const val KEY_IGNORE_QUIET_HOURS = "ignore_quiet_hours"
internal const val KEY_DRAIN_REMAINING = "drain_remaining"
internal const val KEY_PROGRESS_DONE = "progress_done"
internal const val KEY_PROGRESS_TOTAL = "progress_total"
internal const val KEY_USER_VISIBLE = "user_visible"
internal const val KEY_OPERATION_TITLE = "operation_title"
internal const val KEY_ERROR_MESSAGE = "error_message"
internal const val KEY_AI_MODEL_ID = "ai_model_id"
internal const val KEY_SYNC_RUN_ID = "sync_run_id"

internal fun offlinePreparationStage(tags: Set<String>): OfflinePreparationStage = when {
    OFFLINE_SYNC_STAGE_TAG in tags -> OfflinePreparationStage.SYNCING
    OFFLINE_CONTENT_STAGE_TAG in tags -> OfflinePreparationStage.DOWNLOADING_CONTENT
    OFFLINE_PAGES_STAGE_TAG in tags -> OfflinePreparationStage.ARCHIVING_PAGES
    else -> OfflinePreparationStage.IDLE
}

class SyncScheduler(
    private val context: Context,
    private val backendPreferences: BackendPreferences,
    private val syncPreferences: SyncPreferences,
    private val networkMonitor: NetworkStatus,
    private val aiPreferences: AiPreferences,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val workManagerProvider: (Context) -> WorkManager = { appContext ->
        WorkManager.getInstance(appContext)
    },
    private val syncCoordinator: SyncCoordinator = SyncCoordinator()
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

    override fun schedulePeriodicSync() {
        enqueueMaintenance()
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
            .setInputData(
                Data.Builder()
                    .putBoolean(KEY_ENQUEUE_PREFETCH, true)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            CONTENT_SYNC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    override fun enqueuePrefetch(runId: String?) {
        val syncRunId = runId ?: UUID.randomUUID().toString()
        workManager.beginUniqueWork(
            SYNC_PIPELINE_WORK,
            ExistingWorkPolicy.KEEP,
            prefetchRequest(runId = syncRunId)
        ).then(aiOverviewPreloadRequest()).then(maintenanceRequest()).enqueue()
    }

    override fun request(intent: SyncIntent): SyncOperationId? {
        if (!backendPreferences.hasBackendCredentials()) return null
        val plan = syncCoordinator.plan(intent)
        val defaultTitleRes = when (intent) {
            SyncIntent.Resync -> R.string.notification_resync_title
            SyncIntent.PrepareOffline -> R.string.notification_offline_title
            SyncIntent.PrepareFullOffline -> R.string.notification_full_offline_title
            is SyncIntent.PrepareTravelMode -> if (intent.fullOffline) {
                R.string.notification_full_offline_title
            } else {
                R.string.notification_offline_title
            }
            else -> R.string.notification_sync_title
        }
        val policy = if (intent == SyncIntent.Background) {
            ExistingWorkPolicy.KEEP
        } else {
            ExistingWorkPolicy.REPLACE
        }
        val title = (intent as? SyncIntent.User)?.operationTitle
            ?: context.getString(defaultTitleRes)
        return enqueuePlan(plan, title, policy)
    }

    private fun enqueuePlan(
        plan: SyncPlan,
        operationTitle: String,
        policy: ExistingWorkPolicy
    ): SyncOperationId {
        val syncRunId = UUID.randomUUID().toString()
        val syncWork = syncRequest(plan, operationTitle, syncRunId)
        var continuation = workManager
            .beginUniqueWork(SYNC_PIPELINE_WORK, policy, syncWork)
            .then(prefetchRequest(plan, operationTitle, syncRunId))
            .then(aiOverviewPreloadRequest(plan))
        if (plan.includeFullPages) {
            continuation = continuation.then(fullPageRequest(plan, operationTitle, syncRunId))
        }
        continuation.then(maintenanceRequest()).enqueue()
        return SyncOperationId(syncWork.id.toString())
    }

    override fun observeRequestedSync(): Flow<SyncOperationStatus> =
        observeSyncPipeline().map { it.status }

    override fun observeOperation(operationId: SyncOperationId): Flow<SyncOperationStatus> {
        val workId = runCatching { UUID.fromString(operationId.value) }.getOrNull()
            ?: return flowOf(
                SyncOperationStatus(
                    state = SyncOperationState.FAILED,
                    operationIds = setOf(operationId)
                )
            )
        return workManager.getWorkInfoByIdFlow(workId)
            .map { info ->
                info?.let { operationStatus(it, operationId) }
                    ?: SyncOperationStatus(operationIds = setOf(operationId))
            }
            .distinctUntilChanged()
    }

    override fun observeSyncActivity(): Flow<Boolean> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(CONTENT_SYNC_WORK),
        workManager.getWorkInfosForUniqueWorkFlow(SYNC_PIPELINE_WORK)
    ) { periodic, pipeline ->
        periodic.any { it.state == WorkInfo.State.RUNNING } || pipeline.any { info ->
            info.state == WorkInfo.State.RUNNING ||
                info.state == WorkInfo.State.ENQUEUED ||
                info.state == WorkInfo.State.BLOCKED
        }
    }.distinctUntilChanged()

    override fun observeNextScheduledSync(): Flow<Long?> =
        workManager.getWorkInfosForUniqueWorkFlow(CONTENT_SYNC_WORK)
            .map { infos ->
                infos.firstOrNull {
                    it.periodicityInfo != null &&
                        (it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING)
                }
                    ?.nextScheduleTimeMillis
                    ?.takeIf { it > 0L }
            }
            .distinctUntilChanged()

    override suspend fun cancelAllSync() {
        listOf(CONTENT_SYNC_WORK, SYNC_PIPELINE_WORK, MAINTENANCE_WORK).forEach { workName ->
            workManager.cancelUniqueWork(workName).await()
            withContext(Dispatchers.IO) {
                workManager.getWorkInfosForUniqueWorkFlow(workName).first { infos ->
                    infos.none { !it.state.isFinished }
                }
            }
        }
    }

    override fun enqueueBackgroundSyncChain() {
        if (!backendPreferences.hasBackendCredentials()) return
        val now = System.currentTimeMillis()
        if (now - syncPreferences.getLastChainedSyncTimestamp() < CHAINED_SYNC_THROTTLE_MILLIS) return
        syncPreferences.setLastChainedSyncTimestamp(now)

        request(SyncIntent.Background)
    }

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
        plan: SyncPlan,
        operationTitle: String,
        runId: String
    ) = oneTimeRequest<ContentSyncWorker>(
        constraints = networkConstraints(
            avoidLowStorage = plan.travelMode,
            avoidLowBattery = plan.travelMode && !plan.expedited
        ),
        inputData = Data.Builder()
            .putBoolean(KEY_FORCE_FULL_SYNC, plan.forceFullSync)
            .putBoolean(KEY_ENQUEUE_PREFETCH, false)
            .putOperationData(plan, operationTitle, runId)
            .build(),
        expedited = plan.expedited,
        tags = planTags(plan, OFFLINE_SYNC_STAGE_TAG)
    )

    private fun maintenanceRequest() = oneTimeRequest<CacheMaintenanceWorker>(
        constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .setRequiresBatteryNotLow(true)
            .build()
    )

    private fun enqueueMaintenance() {
        workManager.enqueueUniqueWork(
            MAINTENANCE_WORK,
            ExistingWorkPolicy.KEEP,
            maintenanceRequest()
        )
    }

    private fun prefetchRequest(
        plan: SyncPlan = syncCoordinator.plan(SyncIntent.Periodic),
        operationTitle: String = context.getString(R.string.notification_sync_title),
        runId: String? = null
    ) = oneTimeRequest<ArticleContentSyncWorker>(
        constraints = networkConstraints(
            avoidLowStorage = true,
            avoidLowBattery = !plan.expedited
        ),
        inputData = Data.Builder()
            .putOperationData(plan, operationTitle, runId)
            .putBoolean(KEY_DRAIN_REMAINING, plan.drainRemaining)
            .putBoolean(KEY_DOWNLOAD_ALL_IMAGES, plan.fullOfflinePreparation)
            .build(),
        expedited = plan.expedited,
        tags = planTags(plan, OFFLINE_CONTENT_STAGE_TAG)
    )

    private fun aiOverviewPreloadRequest(
        plan: SyncPlan = syncCoordinator.plan(SyncIntent.Periodic)
    ): OneTimeWorkRequest {
        val modelId = aiPreferences.getAiModelId()
        return oneTimeRequest<ArticleAiOverviewPreloadWorker>(
            constraints = aiOverviewPreloadConstraints(
                modelId = modelId,
                avoidLowBattery = plan.travelMode && !plan.expedited
            ),
            inputData = Data.Builder().putString(KEY_AI_MODEL_ID, modelId).build()
        )
    }

    private fun aiOverviewPreloadConstraints(
        modelId: String,
        avoidLowBattery: Boolean
    ): Constraints = if (AiModel.providerFor(modelId) == AiProvider.OPENROUTER) {
        networkConstraints(avoidLowStorage = true, avoidLowBattery = avoidLowBattery)
    } else {
        Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .apply { if (avoidLowBattery) setRequiresBatteryNotLow(true) }
            .build()
    }

    private fun fullPageRequest(
        plan: SyncPlan,
        operationTitle: String,
        runId: String? = null
    ) = oneTimeRequest<FullPageSyncWorker>(
        constraints = networkConstraints(
            avoidLowStorage = true,
            avoidLowBattery = !plan.expedited
        ),
        inputData = Data.Builder()
            .putOperationData(plan, operationTitle, runId)
            .build(),
        expedited = plan.expedited,
        tags = listOf(
            OFFLINE_PREPARATION_TAG,
            FULL_OFFLINE_PREPARATION_TAG,
            OFFLINE_PAGES_STAGE_TAG
        )
    )

    private inline fun <reified Worker : ListenableWorker> oneTimeRequest(
        constraints: Constraints,
        inputData: Data = Data.EMPTY,
        expedited: Boolean = false,
        tags: List<String> = emptyList()
    ): OneTimeWorkRequest = OneTimeWorkRequestBuilder<Worker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
        .setInputData(inputData)
        .apply {
            if (expedited) {
                setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            tags.forEach(::addTag)
        }
        .build()

    private fun planTags(plan: SyncPlan, stageTag: String): List<String> = buildList {
        if (plan.offlinePreparation) {
            add(OFFLINE_PREPARATION_TAG)
            add(stageTag)
        }
        if (plan.fullOfflinePreparation) add(FULL_OFFLINE_PREPARATION_TAG)
    }

    private fun Data.Builder.putOperationData(
        plan: SyncPlan,
        operationTitle: String,
        runId: String?
    ): Data.Builder = apply {
        putBoolean(KEY_IGNORE_QUIET_HOURS, plan.ignoreQuietHours)
        putBoolean(KEY_USER_VISIBLE, plan.userVisible)
        putString(KEY_OPERATION_TITLE, operationTitle)
        runId?.let { putString(KEY_SYNC_RUN_ID, it) }
    }
}

internal fun operationStatus(infos: List<WorkInfo>): SyncOperationStatus {
    val operationIds = infos.map { SyncOperationId(it.id.toString()) }.toSet()
    if (infos.isEmpty()) return SyncOperationStatus(operationIds = operationIds)
    val failed = infos.firstOrNull { it.state == WorkInfo.State.FAILED }
    if (failed != null) {
        return SyncOperationStatus(
            state = SyncOperationState.FAILED,
            errorMessage = failed.outputData.getString(KEY_ERROR_MESSAGE),
            operationIds = operationIds
        )
    }
    if (infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) {
        return SyncOperationStatus(state = SyncOperationState.RUNNING, operationIds = operationIds)
    }
    if (infos.any { it.state == WorkInfo.State.CANCELLED }) {
        return SyncOperationStatus(state = SyncOperationState.CANCELLED, operationIds = operationIds)
    }
    return SyncOperationStatus(state = SyncOperationState.SUCCEEDED, operationIds = operationIds)
}

private fun operationStatus(
    info: WorkInfo,
    operationId: SyncOperationId
): SyncOperationStatus = when (info.state) {
    WorkInfo.State.FAILED -> SyncOperationStatus(
        state = SyncOperationState.FAILED,
        errorMessage = info.outputData.getString(KEY_ERROR_MESSAGE),
        operationIds = setOf(operationId)
    )
    WorkInfo.State.CANCELLED -> SyncOperationStatus(
        state = SyncOperationState.CANCELLED,
        operationIds = setOf(operationId)
    )
    WorkInfo.State.SUCCEEDED -> SyncOperationStatus(
        state = SyncOperationState.SUCCEEDED,
        operationIds = setOf(operationId)
    )
    WorkInfo.State.RUNNING,
    WorkInfo.State.ENQUEUED,
    WorkInfo.State.BLOCKED -> SyncOperationStatus(
        state = SyncOperationState.RUNNING,
        operationIds = setOf(operationId)
    )
}
