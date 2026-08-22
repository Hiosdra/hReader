package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GemmaModelDownloadSchedulerTest {
    private val context = mockk<Context>()
    private val modelManager = mockk<GemmaModelGateway>(relaxed = true)
    private val aiPreferences = mockk<AiPreferences>()
    private val workManager = mockk<WorkManager>(relaxed = true)

    @Before
    fun setUp() {
        every { context.applicationContext } returns context
        every {
            workManager.enqueueUniqueWork(
                any<String>(),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>()
            )
        } returns mockk(relaxed = true)
    }

    @Test
    fun downloadDefaultsToAnUnmeteredNetwork() {
        every { aiPreferences.getGemmaDownloadOnUnmeteredOnly() } returns true
        val request = enqueueAndCapture()

        assertEquals(NetworkType.UNMETERED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun downloadCanUseConnectedNetworkAfterExplicitOptOut() {
        every { aiPreferences.getGemmaDownloadOnUnmeteredOnly() } returns false
        val request = enqueueAndCapture()

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    private fun enqueueAndCapture(): OneTimeWorkRequest {
        val request = slot<OneTimeWorkRequest>()
        val scheduler = GemmaModelDownloadScheduler(
            context = context,
            modelManager = modelManager,
            aiPreferences = aiPreferences,
            workManagerProvider = { workManager }
        )

        scheduler.enqueueDownload()

        verify { modelManager.markDownloadEnqueued() }
        verify {
            workManager.enqueueUniqueWork(
                "DownloadGemma4E2bModel",
                ExistingWorkPolicy.KEEP,
                capture(request)
            )
        }
        return request.captured
    }
}
