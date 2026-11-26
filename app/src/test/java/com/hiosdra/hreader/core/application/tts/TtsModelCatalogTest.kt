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
        assertEquals(TtsEngineFamily.KITTEN, TtsModelCatalog.definition(TtsModel.KITTEN_MINI).model.family)
        assertEquals(TtsEngineFamily.KOKORO, TtsModelCatalog.definition(TtsModel.KOKORO_V1_0).model.family)
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
            listOf(TtsModel.KOKORO, TtsModel.KOKORO_V1_0, TtsModel.ANDROID),
            TtsModelCatalog.compatibleModels("zh")
        )
        assertEquals(
            listOf(
                TtsModel.SUPERTONIC,
                TtsModel.KOKORO,
                TtsModel.KOKORO_V1_0,
                TtsModel.KITTEN_MINI,
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
    fun `exposes model-specific Kokoro voice ranges`() {
        assertEquals(0..9, TtsModelCatalog.voiceIdRange(TtsModel.SUPERTONIC))
        assertEquals(0..102, TtsModelCatalog.voiceIdRange(TtsModel.KOKORO))
        assertEquals(0..53, TtsModelCatalog.voiceIdRange(TtsModel.KOKORO_V1_0))
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
        assertEquals("af_alloy", TtsModelCatalog.voiceName(TtsModel.KOKORO_V1_0, 0))
        assertEquals("af_heart", TtsModelCatalog.voiceName(TtsModel.KOKORO_V1_0, 3))
        assertEquals("em_santa", TtsModelCatalog.voiceName(TtsModel.KOKORO_V1_0, 53))
        assertEquals("af_maple", TtsModelCatalog.voiceName(TtsModel.KOKORO, 0))
        assertEquals("zf_001", TtsModelCatalog.voiceName(TtsModel.KOKORO, 3))
        assertEquals("zm_009", TtsModelCatalog.voiceName(TtsModel.KOKORO, 58))
        assertEquals("zm_100", TtsModelCatalog.voiceName(TtsModel.KOKORO, 102))
    }

    @Test
    fun `keeps a separate Kokoro voice per model version`() {
        val settings = TtsAdvancedSettings()

        assertEquals(0, settings.kokoroSpeakerFor(TtsModel.KOKORO))
        assertEquals(3, settings.kokoroSpeakerFor(TtsModel.KOKORO_V1_0))

        val changed = settings.withKokoroSpeaker(TtsModel.KOKORO_V1_0, 16)

        assertEquals(16, changed.kokoroSpeakerFor(TtsModel.KOKORO_V1_0))
        assertEquals(0, changed.kokoroSpeakerFor(TtsModel.KOKORO))
        assertEquals(53, TtsAdvancedSettings(kokoroV10Speaker = 80).kokoroSpeakerFor(TtsModel.KOKORO_V1_0))
    }
}
