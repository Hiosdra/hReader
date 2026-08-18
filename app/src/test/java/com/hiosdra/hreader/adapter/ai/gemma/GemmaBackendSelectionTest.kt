package com.hiosdra.hreader.adapter.ai.gemma

import com.hiosdra.hreader.core.application.ai.GemmaBackend
import org.junit.Assert.assertEquals
import org.junit.Test

class GemmaBackendSelectionTest {
    @Test
    fun automaticSelectionPrefersGpuThenCpu() {
        assertEquals(
            listOf(GemmaBackend.GPU, GemmaBackend.CPU),
            backendAttempts(GemmaBackend.AUTO)
        )
    }

    @Test
    fun explicitCpuDoesNotTryAnotherBackend() {
        assertEquals(listOf(GemmaBackend.CPU), backendAttempts(GemmaBackend.CPU))
    }

    @Test
    fun explicitGpuFallsBackToCpu() {
        assertEquals(
            listOf(GemmaBackend.GPU, GemmaBackend.CPU),
            backendAttempts(GemmaBackend.GPU)
        )
    }

    @Test
    fun explicitNpuFallsBackLocally() {
        assertEquals(
            listOf(GemmaBackend.NPU, GemmaBackend.GPU, GemmaBackend.CPU),
            backendAttempts(GemmaBackend.NPU)
        )
    }
}
