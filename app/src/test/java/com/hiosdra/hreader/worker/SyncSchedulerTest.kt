package com.hiosdra.hreader.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.util.NetworkMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SyncSchedulerTest {
    private val context = mockk<Context>(relaxed = true)
    private val preferencesManager = mockk<PreferencesManager>(relaxed = true)
    private val networkMonitor = mockk<NetworkMonitor>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val workContinuation = mockk<WorkContinuation>(relaxed = true)
    private val scheduler = SyncScheduler(
        context = context,
        preferencesManager = preferencesManager,
        networkMonitor = networkMonitor,
        workManagerProvider = { workManager }
    )

    init {
        every { preferencesManager.hasBackendCredentials() } returns true
        every { preferencesManager.getSyncWhileRoaming() } returns true
        every { context.getString(any()) } returns "Sync"
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
        verify { workManager.cancelUniqueWork("RequestedSync") }
        verify {
            workManager.beginUniqueWork(
                "SyncPipeline",
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>()
            )
        }
        verify(exactly = 1) { workContinuation.then(any<OneTimeWorkRequest>()) }
        assertTrue(request.captured.tags.none { it == "FullOfflinePreparation" })
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
        verify(exactly = 2) { workContinuation.then(any<OneTimeWorkRequest>()) }
    }
}
