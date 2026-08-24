package com.hiosdra.hreader.adapter.tts

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsEngineFamily
import java.io.File

internal object VitsTtsModelAdapter : SherpaTtsModelAdapter {
    override val family: TtsEngineFamily = TtsEngineFamily.VITS

    override fun modelConfig(
        root: File,
        modelPackage: TtsModelPackage,
        settings: TtsAdvancedSettings
    ) = offlineTtsModelConfig(settings).apply {
        val files = modelPackage.engineFiles as? SherpaModelFiles.Vits
            ?: error("VITS model package has an incompatible engine configuration")
        vits = OfflineTtsVitsModelConfig(
            model = root.file(files.model),
            tokens = root.file(files.tokens),
            dataDir = root.file(files.dataDir),
            noiseScale = settings.vitsNoiseScale,
            noiseScaleW = settings.vitsDurationNoiseScale
        )
    }

    override fun configureGeneration(
        config: GenerationConfig,
        settings: TtsAdvancedSettings,
        language: String
    ) = Unit

    override fun modelConfigurationKey(settings: TtsAdvancedSettings): Any =
        settings.vitsNoiseScale to settings.vitsDurationNoiseScale
}

private fun File.file(name: String): String = File(this, name).absolutePath
