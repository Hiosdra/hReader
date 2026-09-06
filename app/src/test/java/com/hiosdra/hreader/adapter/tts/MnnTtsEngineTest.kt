package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.MnnTtsBackend
import org.junit.Assert.assertEquals
import org.junit.Test

class MnnTtsEngineTest {
    @Test
    fun `keeps short utterances within the minimum frame budget`() {
        assertEquals(128, mnnTtsMaxFrames("short"))
    }

    @Test
    fun `uses a bounded frame budget for an MNN chunk`() {
        assertEquals(240, mnnTtsMaxFrames("a".repeat(MNN_TTS_MAX_CHUNK_CHARACTERS)))
        assertEquals(384, mnnTtsMaxFrames("a".repeat(400)))
    }

    @Test
    fun `falls back from Vulkan through OpenCL to CPU`() {
        assertEquals(
            listOf(MnnTtsBackend.VULKAN, MnnTtsBackend.OPENCL, MnnTtsBackend.CPU),
            mnnTtsBackendCandidates(MnnTtsBackend.VULKAN)
        )
    }

    @Test
    fun `falls back from OpenCL to CPU`() {
        assertEquals(
            listOf(MnnTtsBackend.OPENCL, MnnTtsBackend.CPU),
            mnnTtsBackendCandidates(MnnTtsBackend.OPENCL)
        )
    }
}
