package com.hiosdra.hreader.adapter.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QwenTtsLanguageTest {

    @Test
    fun `maps qwen cpp language ids`() {
        assertEquals(2050, QwenTtsLanguage.cppId("EN"))
        assertEquals(2071, QwenTtsLanguage.cppId("pt"))
    }

    @Test
    fun `maps mnn language names`() {
        assertEquals("russian", QwenTtsLanguage.mnnName("RU"))
        assertEquals("chinese", QwenTtsLanguage.mnnName("zh"))
    }

    @Test
    fun `rejects unsupported language`() {
        assertThrows(IllegalArgumentException::class.java) {
            QwenTtsLanguage.cppId("pl")
        }
    }
}
