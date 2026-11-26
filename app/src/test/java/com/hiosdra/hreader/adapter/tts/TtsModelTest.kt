package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.parseTtsLanguageOverrides
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsModelTest {
    @Test
    fun `falls back when a stored model has been removed`() {
        assertEquals(TtsModel.SUPERTONIC, TtsModel.fromName("GOSIA"))
    }

    @Test
    fun `ignores malformed removed and unknown language overrides`() {
        assertEquals(
            emptyMap<String, TtsModel>(),
            parseTtsLanguageOverrides(
                setOf("pl=GOSIA", "en=REMOVED_MODEL", "invalid", "=KOKORO")
            )
        )
    }

    @Test
    fun `moves retired Kokoro and Kitten selections to Kokoro`() {
        assertEquals(TtsModel.KOKORO, TtsModel.fromName("KOKORO_V1_0"))
        assertEquals(TtsModel.KOKORO, TtsModel.fromName("KITTEN_MINI"))
        assertEquals(
            mapOf("en" to TtsModel.KOKORO, "zh" to TtsModel.KOKORO),
            parseTtsLanguageOverrides(setOf("en=KITTEN_MINI", "zh=KOKORO_V1_0"))
        )
    }
}
