package com.hiosdra.hreader.adapter.tts.executorch

import java.io.File

internal enum class ChatterboxExecuTorchModule(
    val fileName: String
) {
    VOICE_ENCODER("voice_encoder.pte"),
    XVECTOR_ENCODER("xvector_encoder.pte"),
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

    val requiredModules: List<ChatterboxExecuTorchModule> =
        ChatterboxExecuTorchModule.entries

    fun missingFiles(root: File): List<String> = requiredModules
        .map(ChatterboxExecuTorchModule::fileName)
        .filter { !File(root, it).isFile }

    fun isComplete(root: File): Boolean = root.isDirectory && missingFiles(root).isEmpty()
}
