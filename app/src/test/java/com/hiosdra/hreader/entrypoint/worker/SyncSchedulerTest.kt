package com.hiosdra.hreader.entrypoint.worker

import com.hiosdra.hreader.core.application.sync.OfflinePreparationStage
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.WorkContinuation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.hiosdra.hreader.adapter.system.NetworkMonitor
import com.hiosdra.hreader.core.application.port.out.BackendPreferences
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

@RunWith(JUnit4::class)
class SyncSchedulerTest {
    private fun <T> completedFuture(value: T) = SettableFuture.create<T>().also { it.set(value) }

    private val context = mockk<Context>(relaxed = true)
    private val backendPreferences = mockk<BackendPreferences>(relaxed = true)
    private val syncPreferences = mockk<SyncPreferences>(relaxed = true)
    private val networkMonitor = mockk<NetworkMonitor>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val workContinuation = mockk<WorkContinuation>(relaxed = true)
    private val cancelOperation = mockk<Operation>()
    private val scheduler = SyncScheduler(
        context = context,
        backendPreferences = backendPreferences,
        syncPreferences = syncPreferences,
        networkMonitor = networkMonitor,
        workManagerProvider = { workManager }
    )

    init {
        every { backendPreferences.hasBackendCredentials() } returns true
        every { syncPreferences.getSyncWhileRoaming() } returns true
        every { context.getString(any()) } returns "Sync"
        every { cancelOperation.result } returns completedFuture(Operation.SUCCESS)
        every { workManager.cancelUniqueWork(any()) } returns cancelOperation
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
        every {
            workManager.beginUniqueWork(
                any<String>(),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>()
            )
        } returns workContinuation
        every { workContinuation.then(any<OneTimeWorkRequest>()) } returns workContinuation
    }

    @Test
    fun prepareForOffline_replacesTheExistingSyncPipeline() {
        val request = slot<OneTimeWorkRequest>()

        every {
            workManager.beginUniqueWork(
                "SyncPipeline",
                ExistingWorkPolicy.REPLACE,
                capture(request)
            )
        } returns workContinuation

        assertNotNull(scheduler.prepareForOffline())

        assertTrue(request.captured.tags.contains("OfflinePreparation"))
        verify {
            workManager.beginUniqueWork(
                "SyncPipeline",
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>()
            )
        }
        verify(exactly = 2) { workContinuation.then(any<OneTimeWorkRequest>()) }
        assertTrue(request.captured.tags.none { it == "FullOfflinePreparation" })
    }

    @Test
    fun cancelAllSync_cancelsTheActiveWorkOnly() = runBlocking {
        scheduler.cancelAllSync()

        verify { workManager.cancelUniqueWork("ContentSyncWorker") }
        verify { workManager.cancelUniqueWork("SyncPipeline") }
        verify { workManager.cancelUniqueWork("SyncMaintenance") }
        verify(exactly = 3) { workManager.cancelUniqueWork(any()) }
    }

    @Test
    fun backgroundSync_keepsAnExistingUserPipeline() {
        scheduler.enqueueBackgroundSyncChain()

        verify {
            workManager.beginUniqueWork(
                "SyncPipeline",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun resync_expeditesEveryStage() {
        val syncRequest = slot<OneTimeWorkRequest>()
        val stageRequests = mutableListOf<OneTimeWorkRequest>()

        every {
            workManager.beginUniqueWork(
                "SyncPipeline",
                ExistingWorkPolicy.REPLACE,
                capture(syncRequest)
            )
        } returns workContinuation
        every { workContinuation.then(capture(stageRequests)) } returns workContinuation

        assertNotNull(scheduler.resyncNow())

        assertTrue(syncRequest.captured.workSpec.expedited)
        assertTrue(stageRequests.first().workSpec.expedited)
    }

    @Test
    fun regularSync_doesNotBecomeExpedited() {
        val syncRequest = slot<OneTimeWorkRequest>()
        val stageRequests = mutableListOf<OneTimeWorkRequest>()

        every {
            workManager.beginUniqueWork(
                "SyncPipeline",
                ExistingWorkPolicy.REPLACE,
                capture(syncRequest)
            )
        } returns workContinuation
        every { workContinuation.then(capture(stageRequests)) } returns workContinuation

        assertNotNull(scheduler.syncNow())

        assertFalse(syncRequest.captured.workSpec.expedited)
        assertFalse(stageRequests.first().workSpec.expedited)
    }

    @Test
    fun fullOfflinePreparation_addsFullPageStageToThePipeline() {
        val request = slot<OneTimeWorkRequest>()

        every {
            workManager.beginUniqueWork(
                "SyncPipeline",
                ExistingWorkPolicy.REPLACE,
                capture(request)
            )
        } returns workContinuation

        assertNotNull(scheduler.prepareFullOffline())

        assertTrue(request.captured.tags.contains("FullOfflinePreparation"))
        verify(exactly = 3) { workContinuation.then(any<OneTimeWorkRequest>()) }
    }

    @Test
    fun identifies_each_offline_preparation_stage() {
        assertEquals(
            OfflinePreparationStage.SYNCING,
            offlinePreparationStage(setOf("OfflineSyncStage"))
        )
        assertEquals(
            OfflinePreparationStage.DOWNLOADING_CONTENT,
            offlinePreparationStage(setOf("OfflineContentStage"))
        )
        assertEquals(
            OfflinePreparationStage.ARCHIVING_PAGES,
            offlinePreparationStage(setOf("OfflinePagesStage"))
        )
        assertEquals(OfflinePreparationStage.IDLE, offlinePreparationStage(emptySet()))
    }

    @Test
    fun operationStatus_prefersFailureOverCompletedWork() {
        val status = operationStatus(
            listOf(
                workInfo(WorkInfo.State.SUCCEEDED),
                workInfo(WorkInfo.State.FAILED, "network")
            )
        )

        assertEquals(SyncOperationState.FAILED, status.state)
        assertEquals("network", status.errorMessage)
    }

    @Test
    fun operationStatus_reportsRunningWhenAnyStageIsActive() {
        val status = operationStatus(
            listOf(
                workInfo(WorkInfo.State.SUCCEEDED),
                workInfo(WorkInfo.State.ENQUEUED)
            )
        )

        assertEquals(SyncOperationState.RUNNING, status.state)
    }

    @Test
    fun operationStatus_reportsCancellationWhenNoStageIsActive() {
        val status = operationStatus(listOf(workInfo(WorkInfo.State.CANCELLED)))

        assertEquals(SyncOperationState.CANCELLED, status.state)
    }

    private fun workInfo(workState: WorkInfo.State, errorMessage: String? = null): WorkInfo =
        mockk<WorkInfo> {
            every { id } returns UUID.randomUUID()
            every { state } returns workState
            every { outputData } returns Data.Builder()
                .putString(KEY_ERROR_MESSAGE, errorMessage)
                .build()
        }
}
