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
        assertEquals(TtsEngineFamily.KOKORO, TtsModelCatalog.definition(TtsModel.KOKORO).model.family)
    }

    @Test
    fun `routes languages from catalog definitions`() {
        assertEquals(
            listOf(
                TtsModel.SUPERTONIC,
                TtsModel.COQUI_PL_MAI_FEMALE,
                TtsModel.ANDROID
            ),
            TtsModelCatalog.compatibleModels("PL")
        )
        assertEquals(
            listOf(TtsModel.KOKORO, TtsModel.ANDROID),
            TtsModelCatalog.compatibleModels("zh")
        )
        assertEquals(
            listOf(
                TtsModel.SUPERTONIC,
                TtsModel.KOKORO,
                TtsModel.ANDROID
            ),
            TtsModelCatalog.compatibleModels("en")
        )
    }

    @Test
    fun `exposes union of neural model languages`() {
        assertTrue("pl" in TtsModelCatalog.supportedLanguages)
        assertTrue("zh" in TtsModelCatalog.supportedLanguages)
    }

    @Test
    fun `exposes model-specific voice ranges`() {
        assertEquals(0..9, TtsModelCatalog.voiceIdRange(TtsModel.SUPERTONIC))
        assertEquals(0..53, TtsModelCatalog.voiceIdRange(TtsModel.KOKORO))
        assertEquals(0..0, TtsModelCatalog.voiceIdRange(TtsModel.COQUI_PL_MAI_FEMALE))
    }

    @Test
    fun `names all Supertonic voice presets`() {
        assertEquals("M1", TtsModelCatalog.supertonicVoiceName(0))
        assertEquals("F5", TtsModelCatalog.supertonicVoiceName(9))
        assertEquals("M1", TtsModelCatalog.supertonicVoiceName(-1))
        assertEquals("F5", TtsModelCatalog.supertonicVoiceName(10))
    }

    @Test
    fun `names Kokoro voices in model speaker order`() {
        assertEquals("af_alloy", TtsModelCatalog.voiceName(TtsModel.KOKORO, 0))
        assertEquals("af_heart", TtsModelCatalog.voiceName(TtsModel.KOKORO, 3))
        assertEquals("em_santa", TtsModelCatalog.voiceName(TtsModel.KOKORO, 53))
    }

    @Test
    fun `defaults Kokoro to the af_heart voice`() {
        val speaker = TtsAdvancedSettings().kokoroSpeaker

        assertEquals("af_heart", TtsModelCatalog.voiceName(TtsModel.KOKORO, speaker))
    }
}
