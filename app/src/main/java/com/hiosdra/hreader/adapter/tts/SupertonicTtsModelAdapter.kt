package com.hiosdra.hreader.adapter.tts

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsEngineFamily
import java.io.File

internal object SupertonicTtsModelAdapter : SherpaTtsModelAdapter {
    override val family: TtsEngineFamily = TtsEngineFamily.SUPERTONIC

    override fun modelConfig(
        root: File,
        modelPackage: TtsModelPackage,
        settings: TtsAdvancedSettings
    ) = offlineTtsModelConfig(settings).apply {
        val files = modelPackage.engineFiles as? SherpaModelFiles.Supertonic
            ?: error("Supertonic model package has an incompatible engine configuration")
        supertonic = com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig(
            durationPredictor = root.file(files.durationPredictor),
            textEncoder = root.file(files.textEncoder),
            vectorEstimator = root.file(files.vectorEstimator),
            vocoder = root.file(files.vocoder),
            ttsJson = root.file(files.ttsJson),
            unicodeIndexer = root.file(files.unicodeIndexer),
            voiceStyle = root.file(files.voiceStyle)
        )
    }

    override fun configureGeneration(
        config: GenerationConfig,
        settings: TtsAdvancedSettings,
        language: String
    ) {
        config.sid = settings.supertonicSpeaker
        config.numSteps = settings.supertonicSteps
        config.extra = mapOf("lang" to language)
    }
}

private fun File.file(name: String): String = File(this, name).absolutePath
