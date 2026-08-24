package com.hiosdra.hreader.adapter.tts

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsEngineFamily
import java.io.File

internal interface SherpaTtsModelAdapter {
    val family: TtsEngineFamily

    fun modelConfig(
        root: File,
        modelPackage: TtsModelPackage,
        settings: TtsAdvancedSettings
    ): OfflineTtsModelConfig

    fun configureGeneration(
        config: GenerationConfig,
        settings: TtsAdvancedSettings,
        language: String
    )

    fun modelConfigurationKey(settings: TtsAdvancedSettings): Any? = null
}

internal fun offlineTtsModelConfig(settings: TtsAdvancedSettings): OfflineTtsModelConfig =
    OfflineTtsModelConfig().apply {
        numThreads = settings.numThreads
        provider = "cpu"
    }
