package com.hiosdra.hreader.adapter.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextProcessorTest {
    @Test
    fun `extracts readable text and removes non-content elements`() {
        val chunks = TtsTextProcessor.fromHtml(
            "Title",
            "<article><p>Hello <b>world</b>.</p><script>bad()</script><footer>menu</footer></article>"
        )

        assertEquals("Title. Hello world.", chunks.single())
        assertFalse(chunks.single().contains("bad"))
        assertFalse(chunks.single().contains("menu"))
    }

    @Test
    fun `chunks long text without dropping sentences`() {
        val chunks = TtsTextProcessor.chunks("First sentence. Second sentence. Third sentence.", 32)

        assertTrue(chunks.size > 1)
        assertEquals(
            "First sentence. Second sentence. Third sentence.",
            chunks.joinToString(" ")
        )
    }

    @Test
    fun `keeps default synthesis chunks short`() {
        val chunks = TtsTextProcessor.chunks("word ".repeat(200))

        assertTrue(chunks.all { it.length <= 350 })
    }

    @Test
    fun `splits long sentences on word boundaries`() {
        val text = "one two three four five six seven eight nine ten"
        val chunks = TtsTextProcessor.chunks(text, 20)

        assertTrue(chunks.all { it.length <= 20 })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun `MNN chunks stay within the low latency budget`() {
        val chunks = TtsTextProcessor.chunks("word ".repeat(100), MNN_TTS_MAX_CHUNK_CHARACTERS)

        assertTrue(chunks.all { it.length <= MNN_TTS_MAX_CHUNK_CHARACTERS })
    }
}
