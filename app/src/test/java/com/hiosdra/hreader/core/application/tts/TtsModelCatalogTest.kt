package com.hiosdra.hreader.core.application.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsModelCatalogTest {
    @Test
    fun `registers every model enum value`() {
        assertEquals(TtsModel.entries.toSet(), TtsModelCatalog.models.toSet())
    }

    @Test
    fun `keeps model family separate from model id`() {
        assertEquals(TtsEngineFamily.SUPERTONIC, TtsModelCatalog.definition(TtsModel.SUPERTONIC).model.family)
        assertEquals(TtsEngineFamily.VITS, TtsModelCatalog.definition(TtsModel.GOSIA).model.family)
        assertEquals(TtsEngineFamily.VITS, TtsModelCatalog.definition(TtsModel.PIPER_BASS_HIGH).model.family)
    }

    @Test
    fun `routes languages from catalog definitions`() {
        assertEquals(
            listOf(
                TtsModel.SUPERTONIC,
                TtsModel.GOSIA,
                TtsModel.PIPER_BASS_HIGH,
                TtsModel.PIPER_DARKMAN_MEDIUM,
                TtsModel.PIPER_JARVIS_MEDIUM,
                TtsModel.PIPER_JUSTYNA_MEDIUM,
                TtsModel.PIPER_MC_SPEECH_MEDIUM,
                TtsModel.PIPER_MESKI_MEDIUM,
                TtsModel.PIPER_ZENSKI_MEDIUM,
                TtsModel.ANDROID
            ),
            TtsModelCatalog.compatibleModels("PL")
        )
        assertEquals(
            listOf(TtsModel.KOKORO, TtsModel.ANDROID),
            TtsModelCatalog.compatibleModels("zh")
        )
    }

    @Test
    fun `exposes union of neural model languages`() {
        assertTrue("pl" in TtsModelCatalog.supportedLanguages)
        assertTrue("zh" in TtsModelCatalog.supportedLanguages)
    }
}
