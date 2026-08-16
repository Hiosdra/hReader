package com.hiosdra.hreader.adapter.tts

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel

internal class SherpaTtsEngine(
    private val modelManager: TtsModelManager
) {
    private var loadedConfiguration: LoadedConfiguration? = null
    private var tts: OfflineTts? = null

    @Synchronized
    fun prepare(model: TtsModel, settings: TtsAdvancedSettings) {
        engineFor(model, settings)
    }

    @Synchronized
    fun generate(
        model: TtsModel,
        text: String,
        speed: Float,
        language: String,
        settings: TtsAdvancedSettings
    ): GeneratedAudio {
        val engine = engineFor(model, settings)
        val config = GenerationConfig().apply {
            this.speed = speed
            silenceScale = settings.silenceScale
            sid = when (model) {
                TtsModel.SUPERTONIC -> settings.supertonicSpeaker
                TtsModel.KOKORO -> settings.kokoroSpeaker
                else -> 0
            }
            numSteps = settings.supertonicSteps
            if (model == TtsModel.SUPERTONIC) extra = mapOf("lang" to language)
        }
        return engine.generateWithConfig(text, config)
    }

    @Synchronized
    fun release() {
        tts?.release()
        tts = null
        loadedConfiguration = null
    }

    private fun engineFor(model: TtsModel, settings: TtsAdvancedSettings): OfflineTts {
        val configuration = LoadedConfiguration(
            model = model,
            numThreads = settings.numThreads,
            gosiaNoiseScale = settings.gosiaNoiseScale.takeIf { model == TtsModel.GOSIA },
            gosiaDurationNoiseScale = settings.gosiaDurationNoiseScale.takeIf { model == TtsModel.GOSIA }
        )
        if (loadedConfiguration == configuration) return checkNotNull(tts)
        release()
        return OfflineTts(null, OfflineTtsConfig(model = configFor(model, settings))).also {
            tts = it
            loadedConfiguration = configuration
        }
    }

    private fun configFor(model: TtsModel, settings: TtsAdvancedSettings): OfflineTtsModelConfig {
        val config = OfflineTtsModelConfig().apply {
            numThreads = settings.numThreads
            provider = "cpu"
        }
        when (model) {
            TtsModel.SUPERTONIC -> {
                val root = modelManager.directory(model).absolutePath
                config.supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = "$root/duration_predictor.int8.onnx",
                    textEncoder = "$root/text_encoder.int8.onnx",
                    vectorEstimator = "$root/vector_estimator.int8.onnx",
                    vocoder = "$root/vocoder.int8.onnx",
                    ttsJson = "$root/tts.json",
                    unicodeIndexer = "$root/unicode_indexer.bin",
                    voiceStyle = "$root/voice.bin"
                )
            }
            TtsModel.KOKORO -> {
                val root = modelManager.directory(model).absolutePath
                config.kokoro = OfflineTtsKokoroModelConfig(
                    model = "$root/model.int8.onnx",
                    voices = "$root/voices.bin",
                    tokens = "$root/tokens.txt",
                    dataDir = "$root/espeak-ng-data",
                    lexicon = "$root/lexicon-us-en.txt,$root/lexicon-zh.txt"
                )
            }
            TtsModel.GOSIA -> {
                val root = modelManager.directory(model).absolutePath
                config.vits = OfflineTtsVitsModelConfig(
                    model = "$root/pl_PL-gosia-medium.onnx",
                    tokens = "$root/tokens.txt",
                    dataDir = "$root/espeak-ng-data",
                    noiseScale = settings.gosiaNoiseScale,
                    noiseScaleW = settings.gosiaDurationNoiseScale
                )
            }
            TtsModel.ANDROID -> error("Android TTS is not a sherpa model")
        }
        return config
    }

    private data class LoadedConfiguration(
        val model: TtsModel,
        val numThreads: Int,
        val gosiaNoiseScale: Float?,
        val gosiaDurationNoiseScale: Float?
    )
}
