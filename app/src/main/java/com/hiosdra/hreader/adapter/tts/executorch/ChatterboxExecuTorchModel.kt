package com.hiosdra.hreader.adapter.tts.executorch

import java.io.File

internal enum class ChatterboxExecuTorchModule(
    val fileName: String
) {
    T3_COND_SPEECH_EMB("t3_cond_speech_emb.pte"),
    T3_COND_ENCODER("t3_cond_enc.pte"),
    T3_PREFILL("t3_prefill.pte"),
    T3_DECODE("t3_decode.pte"),
    S3GEN_ENCODER("s3gen_encoder.pte"),
    CFM_STEP("cfm_step.pte"),
    HIFIGAN("hifigan.pte")
}

internal object ChatterboxExecuTorchModel {
    const val sampleRate = 24_000
    const val textStartToken = 255
    const val maxTextTokens = 256
    const val textSequenceLength = maxTextTokens + 2
    const val conditioningSpeechTokens = 150
    const val conditioningLength = 34
    const val prefillLength = conditioningLength + textSequenceLength + 1
    const val maxSpeechTokens = 1_000
    const val maxGeneratedSpeechTokens = 200
    const val maxKvLength = prefillLength + maxSpeechTokens
    const val layers = 30
    const val heads = 16
    const val headDimension = 64
    const val speechVocabulary = 8_194
    const val speechStartToken = 6_561
    const val speechEndToken = 6_562
    const val cfmMelLength = 2_200
    const val hifiMelLength = 300
    const val hifiUpsample = 480
    const val hifiHarmonics = 9

    val requiredModules: List<ChatterboxExecuTorchModule> =
        ChatterboxExecuTorchModule.entries

    val kvShape = longArrayOf(layers.toLong(), 1, heads.toLong(), maxKvLength.toLong(), headDimension.toLong())

    fun missingFiles(root: File): List<String> = requiredModules
        .map(ChatterboxExecuTorchModule::fileName)
        .filter { !File(root, it).isFile }

    fun isComplete(root: File): Boolean = root.isDirectory && missingFiles(root).isEmpty()
}
