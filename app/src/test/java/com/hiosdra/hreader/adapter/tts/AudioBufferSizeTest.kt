package com.hiosdra.hreader.adapter.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBufferSizeTest {
    @Test
    fun `aligns Gosia buffer to float sample frames`() {
        val size = pcmFloatMonoBufferSize(sampleRate = 22_050, minimumSize = 8_192)

        assertEquals(0, size % Float.SIZE_BYTES)
        assertTrue(size >= 8_820)
    }

    @Test
    fun `preserves a larger platform minimum and aligns it`() {
        assertEquals(12_348, pcmFloatMonoBufferSize(sampleRate = 22_050, minimumSize = 12_345))
    }

    @Test
    fun `uses fallback when platform reports an error`() {
        assertEquals(8_820, pcmFloatMonoBufferSize(sampleRate = 22_050, minimumSize = -2))
    }
}
