package com.hiosdra.hreader.adapter.tts

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsEngineFamily
import java.io.File

internal object MatchaTtsModelAdapter : SherpaTtsModelAdapter {
    override val family: TtsEngineFamily = TtsEngineFamily.MATCHA

    override fun modelConfig(
        root: File,
        modelPackage: TtsModelPackage,
        settings: TtsAdvancedSettings
    ) = offlineTtsModelConfig(settings).apply {
        val files = modelPackage.engineFiles as? SherpaModelFiles.Matcha
            ?: error("Matcha model package has an incompatible engine configuration")
        matcha = OfflineTtsMatchaModelConfig(
            acousticModel = root.file(files.acousticModel),
            vocoder = root.file(files.vocoder),
            lexicon = root.optionalFile(files.lexicon),
            tokens = root.file(files.tokens),
            dataDir = root.file(files.dataDir),
            dictDir = root.optionalFile(files.dictDir),
            noiseScale = 0.667f,
            lengthScale = 1f
        )
    }

    override fun configureGeneration(
        config: GenerationConfig,
        settings: TtsAdvancedSettings,
        language: String
    ) = Unit
}

private fun File.file(name: String): String = File(this, name).absolutePath

private fun File.optionalFile(name: String): String = name
    .takeIf(String::isNotEmpty)
    ?.let { file(it) }
    .orEmpty()
