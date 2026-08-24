package com.hiosdra.hreader.adapter.tts

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsEngineFamily
import java.io.File

internal object KokoroTtsModelAdapter : SherpaTtsModelAdapter {
    override val family: TtsEngineFamily = TtsEngineFamily.KOKORO

    override fun modelConfig(
        root: File,
        modelPackage: TtsModelPackage,
        settings: TtsAdvancedSettings
    ) = offlineTtsModelConfig(settings).apply {
        val files = modelPackage.engineFiles as? SherpaModelFiles.Kokoro
            ?: error("Kokoro model package has an incompatible engine configuration")
        kokoro = OfflineTtsKokoroModelConfig(
            model = root.file(files.model),
            voices = root.file(files.voices),
            tokens = root.file(files.tokens),
            dataDir = root.file(files.dataDir),
            lexicon = files.lexicon.split(',').joinToString(",") { root.file(it) }
        )
    }

    override fun configureGeneration(
        config: GenerationConfig,
        settings: TtsAdvancedSettings,
        language: String
    ) {
        config.sid = settings.kokoroSpeaker
    }
}

private fun File.file(name: String): String = File(this, name).absolutePath
