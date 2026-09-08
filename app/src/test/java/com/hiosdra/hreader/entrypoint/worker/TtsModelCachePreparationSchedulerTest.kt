package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.hiosdra.hreader.core.application.port.out.TtsModelCacheGateway
import com.hiosdra.hreader.core.application.tts.MnnTtsBackend
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCacheStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsModelCachePreparationSchedulerTest {
    private val context = mockk<Context>()
    private val cacheGateway = mockk<TtsModelCacheGateway>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    @Test
    fun `preparation uses low battery and storage constraints`() {
        every { context.applicationContext } returns context
        val request = slot<OneTimeWorkRequest>()
        every {
            workManager.enqueueUniqueWork(
                cacheWorkName(TtsModel.MNN_0_6B_BASE_INT8, MnnTtsBackend.OPENCL),
                ExistingWorkPolicy.REPLACE,
                capture(request)
            )
        } returns mockk(relaxed = true)
        val scheduler = scheduler()

        scheduler.enqueuePreparation(
            model = TtsModel.MNN_0_6B_BASE_INT8,
            settings = TtsAdvancedSettings(
                numThreads = 2,
                mnnBackend = MnnTtsBackend.OPENCL
            ),
            forceRefresh = true
        )

        assertTrue(request.captured.workSpec.constraints.requiresStorageNotLow())
        assertTrue(request.captured.workSpec.constraints.requiresBatteryNotLow())
        assertEquals(
            TtsModel.MNN_0_6B_BASE_INT8.name,
            request.captured.workSpec.input.getString(TtsModelCachePreparationWorker.KEY_MODEL)
        )
        assertEquals(
            MnnTtsBackend.OPENCL.wireName,
            request.captured.workSpec.input.getString(TtsModelCachePreparationWorker.KEY_BACKEND)
        )
        assertEquals(
            2,
            request.captured.workSpec.input.getInt(TtsModelCachePreparationWorker.KEY_THREADS, 0)
        )
        assertTrue(
            request.captured.workSpec.input.getBoolean(
                TtsModelCachePreparationWorker.KEY_FORCE_REFRESH,
                false
            )
        )
    }

    @Test
    fun `cache status prefers active work then ready cache then failure`() {
        val active = workInfo(
            state = WorkInfo.State.RUNNING,
            progress = Data.Builder()
                .putFloat(TtsModelCachePreparationWorker.KEY_PROGRESS, 0.4f)
                .build()
        )
        val failed = workInfo(
            state = WorkInfo.State.FAILED,
            output = Data.Builder().putString(KEY_ERROR_MESSAGE, "failed").build()
        )

        assertEquals(
            TtsModelCacheStatus.Preparing(0.4f),
            ttsModelCacheStatus(listOf(failed, active), cacheReady = true)
        )
        assertEquals(
            TtsModelCacheStatus.Ready,
            ttsModelCacheStatus(listOf(failed), cacheReady = true)
        )
        assertEquals(
            TtsModelCacheStatus.Failed("failed"),
            ttsModelCacheStatus(listOf(failed), cacheReady = false)
        )
        assertEquals(
            TtsModelCacheStatus.NotPrepared,
            ttsModelCacheStatus(emptyList(), cacheReady = false)
        )
    }

    @Test
    fun `cancelling preparation targets selected model and backend`() {
        every { context.applicationContext } returns context
        val scheduler = scheduler()

        scheduler.cancelPreparation(TtsModel.MNN_0_6B_BASE_FP16, MnnTtsBackend.VULKAN)

        verify {
            workManager.cancelUniqueWork(
                cacheWorkName(TtsModel.MNN_0_6B_BASE_FP16, MnnTtsBackend.VULKAN)
            )
        }
    }

    private fun scheduler() = TtsModelCachePreparationScheduler(
        context = context,
        cacheGateway = cacheGateway,
        workManagerProvider = { workManager }
    )

    private fun workInfo(
        state: WorkInfo.State,
        progress: Data = Data.Builder().build(),
        output: Data = Data.Builder().build()
    ): WorkInfo = mockk {
        every { this@mockk.state } returns state
        every { this@mockk.progress } returns progress
        every { this@mockk.outputData } returns output
    }
}
