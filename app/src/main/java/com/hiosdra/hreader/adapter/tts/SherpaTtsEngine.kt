package com.hiosdra.hreader.adapter.tts

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog

internal class SherpaTtsEngine(
    private val modelManager: TtsModelManager
) : NeuralTtsEngine {
    private val adapters = listOf(
        SupertonicTtsModelAdapter,
        KokoroTtsModelAdapter,
        VitsTtsModelAdapter,
        KittenTtsModelAdapter,
        MatchaTtsModelAdapter
    ).associateBy(SherpaTtsModelAdapter::family)
    override val supportedModels: Set<TtsModel> = TtsModelCatalog.models
        .filter { it.family in adapters && TtsModelPackageCatalog.packageFor(it) != null }
        .toSet()
    private var loadedConfiguration: LoadedConfiguration? = null
    private var tts: OfflineTts? = null

    @Synchronized
    override fun prepare(model: TtsModel, settings: TtsAdvancedSettings) {
        engineFor(model, settings)
    }

    @Synchronized
    override fun generate(
        model: TtsModel,
        text: String,
        speed: Float,
        language: String,
        settings: TtsAdvancedSettings
    ): TtsAudio {
        val engine = engineFor(model, settings)
        val adapter = adapterFor(model)
        val config = GenerationConfig().apply {
            this.speed = speed
            silenceScale = settings.silenceScale
            adapter.configureGeneration(this, settings, language)
        }
        val audio = engine.generateWithConfig(text, config)
        return TtsAudio(samples = audio.samples, sampleRate = audio.sampleRate)
    }

    @Synchronized
    override fun release() {
        tts?.release()
        tts = null
        loadedConfiguration = null
    }

    private fun engineFor(model: TtsModel, settings: TtsAdvancedSettings): OfflineTts {
        val adapter = adapterFor(model)
        val configuration = LoadedConfiguration(
            model = model,
            numThreads = settings.numThreads,
            modelConfiguration = adapter.modelConfigurationKey(settings)
        )
        if (loadedConfiguration == configuration) return checkNotNull(tts)
        release()
        val modelPackage = checkNotNull(TtsModelPackageCatalog.packageFor(model)) {
            "No model package registered for ${model.name}"
        }
        return OfflineTts(
            null,
            OfflineTtsConfig(
                model = adapter.modelConfig(modelManager.directory(model), modelPackage, settings)
            )
        ).also {
            tts = it
            loadedConfiguration = configuration
        }
    }

    private fun adapterFor(model: TtsModel): SherpaTtsModelAdapter =
        checkNotNull(adapters[model.family]) {
            "No sherpa adapter registered for ${model.family}"
        }

    private data class LoadedConfiguration(
        val model: TtsModel,
        val numThreads: Int,
        val modelConfiguration: Any?
    )
}
