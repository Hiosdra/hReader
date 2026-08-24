package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NeuralTtsEngineRegistryTest {
    @Test
    fun `delegates calls by model`() {
        val engine = FakeEngine(setOf(TtsModel.SUPERTONIC))
        val registry = NeuralTtsEngineRegistry(listOf(engine))

        registry.prepare(TtsModel.SUPERTONIC, TtsAdvancedSettings())
        val audio = registry.generate(
            model = TtsModel.SUPERTONIC,
            text = "text",
            speed = 1f,
            language = "pl",
            settings = TtsAdvancedSettings()
        )

        assertEquals(1, engine.prepareCalls)
        assertEquals(1, engine.generateCalls)
        assertEquals(22_050, audio.sampleRate)
    }

    @Test
    fun `rejects duplicate model ownership`() {
        val first = FakeEngine(setOf(TtsModel.SUPERTONIC))
        val second = FakeEngine(setOf(TtsModel.SUPERTONIC))

        assertThrows(IllegalStateException::class.java) {
            NeuralTtsEngineRegistry(listOf(first, second))
        }
    }

    private class FakeEngine(
        override val supportedModels: Set<TtsModel>
    ) : NeuralTtsEngine {
        var prepareCalls = 0
        var generateCalls = 0

        override fun prepare(model: TtsModel, settings: TtsAdvancedSettings) {
            prepareCalls++
        }

        override fun generate(
            model: TtsModel,
            text: String,
            speed: Float,
            language: String,
            settings: TtsAdvancedSettings
        ): TtsAudio {
            generateCalls++
            return TtsAudio(FloatArray(1), 22_050)
        }

        override fun release() = Unit
    }
}
