package com.hiosdra.hreader.adapter.tts

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsEngineFamily
import java.io.File

internal object KittenTtsModelAdapter : SherpaTtsModelAdapter {
    override val family: TtsEngineFamily = TtsEngineFamily.KITTEN

    override fun modelConfig(
        root: File,
        modelPackage: TtsModelPackage,
        settings: TtsAdvancedSettings
    ) = offlineTtsModelConfig(settings).apply {
        val files = modelPackage.engineFiles as? SherpaModelFiles.Kitten
            ?: error("Kitten model package has an incompatible engine configuration")
        kitten = OfflineTtsKittenModelConfig(
            model = root.file(files.model),
            voices = root.file(files.voices),
            tokens = root.file(files.tokens),
            dataDir = root.file(files.dataDir)
        )
    }

    override fun configureGeneration(
        config: GenerationConfig,
        settings: TtsAdvancedSettings,
        language: String
    ) {
        config.sid = settings.kittenSpeaker
    }
}

private fun File.file(name: String): String = File(this, name).absolutePath
