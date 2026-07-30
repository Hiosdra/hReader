package com.hiosdra.hreader.data.tts

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.GeneratedAudio

internal class SherpaTtsEngine(
    private val modelManager: TtsModelManager
) {
    private var loadedModel: TtsModel? = null
    private var tts: OfflineTts? = null

    @Synchronized
    fun generate(model: TtsModel, text: String, speed: Float, language: String): GeneratedAudio {
        val engine = engineFor(model)
        val config = GenerationConfig().apply {
            this.speed = speed
            sid = if (model == TtsModel.KOKORO) 3 else 0
            numSteps = 8
            if (model == TtsModel.SUPERTONIC) extra = mapOf("lang" to language)
        }
        return engine.generateWithConfig(text, config)
    }

    @Synchronized
    fun release() {
        tts?.release()
        tts = null
        loadedModel = null
    }

    private fun engineFor(model: TtsModel): OfflineTts {
        if (loadedModel == model) return checkNotNull(tts)
        release()
        return OfflineTts(null, OfflineTtsConfig(model = configFor(model))).also {
            tts = it
            loadedModel = model
        }
    }

    private fun configFor(model: TtsModel): OfflineTtsModelConfig {
        val config = OfflineTtsModelConfig().apply {
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
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
                    lexicon = "$root/lexicon-us-en.txt",
                    lang = "en-us"
                )
            }
            TtsModel.GOSIA -> {
                val root = modelManager.directory(model).absolutePath
                config.vits = OfflineTtsVitsModelConfig(
                    model = "$root/pl_PL-gosia-medium.onnx",
                    tokens = "$root/tokens.txt",
                    dataDir = "$root/espeak-ng-data"
                )
            }
            TtsModel.ANDROID -> error("Android TTS is not a sherpa model")
        }
        return config
    }
}
