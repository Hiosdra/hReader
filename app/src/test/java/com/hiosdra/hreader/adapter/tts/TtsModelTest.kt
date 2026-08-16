package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.parseTtsLanguageOverrides
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsModelTest {
    @Test
    fun `ignores malformed and unknown language overrides`() {
        assertEquals(
            mapOf("pl" to TtsModel.GOSIA),
            parseTtsLanguageOverrides(
                setOf("pl=GOSIA", "en=REMOVED_MODEL", "invalid", "=KOKORO")
            )
        )
    }
}
