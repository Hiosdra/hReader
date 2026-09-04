package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel

internal interface NeuralTtsEngine {
    val supportedModels: Set<TtsModel>

    fun prepare(model: TtsModel, settings: TtsAdvancedSettings)

    fun generate(
        model: TtsModel,
        text: String,
        speed: Float,
        language: String,
        settings: TtsAdvancedSettings
    ): TtsAudio

    fun release()
}

internal class NeuralTtsEngineRegistry(
    engines: List<NeuralTtsEngine>
) : NeuralTtsEngine {
    private val enginesByModel = buildMap {
        engines.forEach { engine ->
            engine.supportedModels.forEach { model ->
                check(put(model, engine) == null) {
                    "Multiple neural TTS engines support ${model.name}"
                }
            }
        }
    }

    override val supportedModels: Set<TtsModel> = enginesByModel.keys

    override fun prepare(model: TtsModel, settings: TtsAdvancedSettings) {
        engineFor(model).prepare(model, settings)
    }

    override fun generate(
        model: TtsModel,
        text: String,
        speed: Float,
        language: String,
        settings: TtsAdvancedSettings
    ): TtsAudio = engineFor(model).generate(model, text, speed, language, settings)

    override fun release() {
        enginesByModel.values.distinct().forEach(NeuralTtsEngine::release)
    }

    private fun engineFor(model: TtsModel): NeuralTtsEngine = checkNotNull(enginesByModel[model]) {
        "No neural TTS engine registered for ${model.name}"
    }
}

internal data class TtsAudio(
    val samples: FloatArray,
    val sampleRate: Int,
    val playbackSpeed: Float = 1f
)
