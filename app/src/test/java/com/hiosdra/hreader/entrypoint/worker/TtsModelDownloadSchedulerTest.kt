package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.tts.TtsModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsModelDownloadSchedulerTest {
    private val context = mockk<Context>()
    private val modelManager = mockk<TtsModelGateway>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    @Test
    fun `download requires an unmetered network`() {
        every { context.applicationContext } returns context
        val request = slot<OneTimeWorkRequest>()
        every {
            workManager.enqueueUniqueWork(
                any<String>(),
                any<ExistingWorkPolicy>(),
                capture(request)
            )
        } returns mockk(relaxed = true)
        val scheduler = TtsModelDownloadScheduler(
            context = context,
            modelManager = modelManager,
            workManagerProvider = { workManager }
        )

        scheduler.enqueueDownload(TtsModel.MNN_0_6B_BASE_INT8)

        assertEquals(NetworkType.UNMETERED, request.captured.workSpec.constraints.requiredNetworkType)
        verify { modelManager.markDownloadEnqueued(TtsModel.MNN_0_6B_BASE_INT8) }
    }
}
