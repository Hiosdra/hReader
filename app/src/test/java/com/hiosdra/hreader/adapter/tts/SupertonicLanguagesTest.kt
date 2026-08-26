package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsLanguages
import org.junit.Assert.assertEquals
import org.junit.Test

class SupertonicLanguagesTest {
    @Test
    fun `uses first supported detected language`() {
        assertEquals("pl", TtsLanguages.resolve(listOf("pl", "en"), "de"))
    }

    @Test
    fun `skips unsupported detected languages`() {
        assertEquals("de", TtsLanguages.resolve(listOf("xx", "de"), "pl"))
    }

    @Test
    fun `uses supported device language when detection is empty`() {
        assertEquals("pl", TtsLanguages.resolve(emptyList(), "PL"))
    }

    @Test
    fun `falls back to english when no language is supported`() {
        assertEquals("en", TtsLanguages.resolve(listOf("xx"), "xx"))
    }

    @Test
    fun `normalizes legacy indonesian language code`() {
        assertEquals("id", TtsLanguages.resolve(listOf("in"), "en"))
    }

    @Test
    fun `routes Chinese to Kokoro without offering Supertonic`() {
        assertEquals("zh", TtsLanguages.resolve(listOf("zh"), "en"))
        assertEquals(
            listOf(TtsModel.KOKORO, TtsModel.CHATTERBOX_EXECUTORCH, TtsModel.ANDROID),
            TtsLanguages.compatibleModels("zh")
        )
    }

    @Test
    fun `routes English to all compatible neural voices`() {
        assertEquals(
            listOf(
                TtsModel.SUPERTONIC,
                TtsModel.KOKORO,
                TtsModel.PIPER_LESSAC_HIGH,
                TtsModel.KITTEN_MINI,
                TtsModel.MATCHA_LJSPEECH,
                TtsModel.CHATTERBOX_EXECUTORCH,
                TtsModel.ANDROID
            ),
            TtsLanguages.compatibleModels("en")
        )
    }

    @Test
    fun `routes Polish only to compatible neural voices`() {
        assertEquals(
            listOf(
                TtsModel.SUPERTONIC,
                TtsModel.GOSIA,
                TtsModel.CHATTERBOX_EXECUTORCH,
                TtsModel.ANDROID
            ),
            TtsLanguages.compatibleModels("pl")
        )
    }
}
