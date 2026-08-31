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
        assertEquals(TtsEngineFamily.KITTEN, TtsModelCatalog.definition(TtsModel.KITTEN_MINI).model.family)
        assertEquals(TtsEngineFamily.MATCHA, TtsModelCatalog.definition(TtsModel.MATCHA_LJSPEECH).model.family)
        assertEquals(TtsEngineFamily.QWEN_CPP, TtsModelCatalog.definition(TtsModel.QWEN_CPP_0_6B_BASE_Q4).model.family)
        assertEquals(TtsEngineFamily.MNN, TtsModelCatalog.definition(TtsModel.MNN_0_6B_BASE_INT8).model.family)
    }

    @Test
    fun `routes languages from catalog definitions`() {
        assertEquals(
            listOf(
                TtsModel.SUPERTONIC,
                TtsModel.GOSIA,
                TtsModel.ANDROID
            ),
            TtsModelCatalog.compatibleModels("PL")
        )
        assertEquals(
            listOf(
                TtsModel.KOKORO,
                TtsModel.QWEN_CPP_0_6B_BASE_Q4,
                TtsModel.QWEN_CPP_0_6B_BASE_Q8,
                TtsModel.QWEN_CPP_0_6B_CUSTOM_VOICE_Q4,
                TtsModel.QWEN_CPP_0_6B_CUSTOM_VOICE_Q8,
                TtsModel.QWEN_CPP_1_7B_BASE_Q4,
                TtsModel.QWEN_CPP_1_7B_BASE_Q8,
                TtsModel.QWEN_CPP_1_7B_CUSTOM_VOICE_Q4,
                TtsModel.QWEN_CPP_1_7B_CUSTOM_VOICE_Q8,
                TtsModel.QWEN_CPP_1_7B_VOICE_DESIGN_Q4,
                TtsModel.QWEN_CPP_1_7B_VOICE_DESIGN_Q8,
                TtsModel.MNN_0_6B_BASE_INT8,
                TtsModel.MNN_0_6B_BASE_FP16,
                TtsModel.ANDROID
            ),
            TtsModelCatalog.compatibleModels("zh")
        )
        assertEquals(
            listOf(
                TtsModel.SUPERTONIC,
                TtsModel.KOKORO,
                TtsModel.PIPER_LESSAC_HIGH,
                TtsModel.KITTEN_MINI,
                TtsModel.MATCHA_LJSPEECH,
                TtsModel.QWEN_CPP_0_6B_BASE_Q4,
                TtsModel.QWEN_CPP_0_6B_BASE_Q8,
                TtsModel.QWEN_CPP_0_6B_CUSTOM_VOICE_Q4,
                TtsModel.QWEN_CPP_0_6B_CUSTOM_VOICE_Q8,
                TtsModel.QWEN_CPP_1_7B_BASE_Q4,
                TtsModel.QWEN_CPP_1_7B_BASE_Q8,
                TtsModel.QWEN_CPP_1_7B_CUSTOM_VOICE_Q4,
                TtsModel.QWEN_CPP_1_7B_CUSTOM_VOICE_Q8,
                TtsModel.QWEN_CPP_1_7B_VOICE_DESIGN_Q4,
                TtsModel.QWEN_CPP_1_7B_VOICE_DESIGN_Q8,
                TtsModel.MNN_0_6B_BASE_INT8,
                TtsModel.MNN_0_6B_BASE_FP16,
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
}
