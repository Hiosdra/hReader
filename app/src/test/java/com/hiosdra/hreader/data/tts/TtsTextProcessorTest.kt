package com.hiosdra.hreader.data.tts

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
}
