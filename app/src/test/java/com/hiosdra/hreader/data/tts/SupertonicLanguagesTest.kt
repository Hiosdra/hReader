package com.hiosdra.hreader.data.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class SupertonicLanguagesTest {
    @Test
    fun `uses first supported detected language`() {
        assertEquals("pl", SupertonicLanguages.resolve(listOf("pl", "en"), "de"))
    }

    @Test
    fun `skips unsupported detected languages`() {
        assertEquals("de", SupertonicLanguages.resolve(listOf("he", "de"), "pl"))
    }

    @Test
    fun `uses supported device language when detection is empty`() {
        assertEquals("pl", SupertonicLanguages.resolve(emptyList(), "PL"))
    }

    @Test
    fun `falls back to english when no language is supported`() {
        assertEquals("en", SupertonicLanguages.resolve(listOf("he"), "he"))
    }

    @Test
    fun `normalizes legacy indonesian language code`() {
        assertEquals("id", SupertonicLanguages.resolve(listOf("in"), "en"))
    }
}
