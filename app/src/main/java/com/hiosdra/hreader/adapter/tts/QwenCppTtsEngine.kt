package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog

internal class QwenCppTtsEngine(
    private val modelManager: TtsModelManager
) : NeuralTtsEngine {
    private val runtime = QwenTtsNative()
    private var loadedConfiguration: LoadedConfiguration? = null

    override val supportedModels: Set<TtsModel> = TtsModelCatalog.models
        .filter {
            it.family == TtsModel.QWEN_CPP_0_6B_BASE_Q4.family &&
                TtsModelPackageCatalog.packageFor(it)?.engineFiles is QwenCppModelFiles
        }
        .toSet()

    @Synchronized
    override fun prepare(model: TtsModel, settings: TtsAdvancedSettings) {
        ensureLoaded(model, settings)
    }

    @Synchronized
    override fun generate(
        model: TtsModel,
        text: String,
        speed: Float,
        language: String,
        settings: TtsAdvancedSettings
    ): TtsAudio {
        val files = modelFiles(model)
        ensureLoaded(model, settings)
        val samples = runtime.synthesize(
            text = text,
            languageId = QwenTtsLanguage.cppId(language),
            maxAudioTokens = MAX_AUDIO_TOKENS,
            numThreads = settings.numThreads,
            speaker = files.speaker,
            instruction = files.instruction
        )
        return TtsAudio(
            samples = samples,
            sampleRate = SAMPLE_RATE,
            playbackSpeed = speed.coerceIn(0.7f, 1.4f)
        )
    }

    @Synchronized
    override fun release() {
        runtime.release()
        loadedConfiguration = null
    }

    private fun ensureLoaded(model: TtsModel, settings: TtsAdvancedSettings) {
        check(model in supportedModels) { "Qwen.cpp does not support ${model.name}" }
        val configuration = LoadedConfiguration(model, settings.numThreads)
        if (configuration == loadedConfiguration) return
        val files = modelFiles(model)
        runtime.release()
        loadedConfiguration = null
        runtime.load(
            modelDirectory = modelManager.directory(model).absolutePath,
            modelName = files.talker,
            numThreads = settings.numThreads
        )
        loadedConfiguration = configuration
    }

    private fun modelFiles(model: TtsModel): QwenCppModelFiles =
        checkNotNull(TtsModelPackageCatalog.packageFor(model)?.engineFiles as? QwenCppModelFiles) {
            "No Qwen.cpp model package registered for ${model.name}"
        }

    private data class LoadedConfiguration(
        val model: TtsModel,
        val numThreads: Int
    )

    private companion object {
        const val SAMPLE_RATE = 24_000
        const val MAX_AUDIO_TOKENS = 4_096
    }
}
