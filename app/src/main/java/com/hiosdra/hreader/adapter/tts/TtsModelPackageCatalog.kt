package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
import java.io.File

internal sealed interface SherpaModelFiles {
    data class Supertonic(
        val durationPredictor: String,
        val textEncoder: String,
        val vectorEstimator: String,
        val vocoder: String,
        val ttsJson: String,
        val unicodeIndexer: String,
        val voiceStyle: String
    ) : SherpaModelFiles

    data class Kokoro(
        val model: String,
        val voices: String,
        val tokens: String,
        val dataDir: String,
        val lexicon: String
    ) : SherpaModelFiles

    data class Vits(
        val model: String,
        val tokens: String,
        val dataDir: String
    ) : SherpaModelFiles
}

internal data class TtsModelPackage(
    val directoryName: String,
    val engineFiles: SherpaModelFiles,
    val requiredFiles: List<String>,
    val requiredDirectories: List<String> = emptyList(),
    val files: List<RemoteFile> = emptyList(),
    val archive: RemoteFile? = null
)

internal object TtsModelPackageCatalog {
    private val packages = mapOf(
        TtsModel.SUPERTONIC to TtsModelPackage(
            directoryName = "supertonic",
            engineFiles = SherpaModelFiles.Supertonic(
                durationPredictor = "duration_predictor.int8.onnx",
                textEncoder = "text_encoder.int8.onnx",
                vectorEstimator = "vector_estimator.int8.onnx",
                vocoder = "vocoder.int8.onnx",
                ttsJson = "tts.json",
                unicodeIndexer = "unicode_indexer.bin",
                voiceStyle = "voice.bin"
            ),
            requiredFiles = SUPERTONIC_FILES.map(RemoteFile::name),
            files = SUPERTONIC_FILES,
            archive = RemoteFile(
                name = "supertonic-3-int8-2026-05-11.tar.bz2",
                url = "https://github.com/Hiosdra/hReader/releases/download/tts-models-v1/supertonic-3-int8-2026-05-11.tar.bz2",
                sha256 = "2b9f11979b0b0f85a2653e63ea567bc819994822e0ff3b7b5ee1b06068fc5c78",
                size = 128_784_503
            )
        ),
        TtsModel.KOKORO to TtsModelPackage(
            directoryName = "kokoro",
            engineFiles = SherpaModelFiles.Kokoro(
                model = "model.int8.onnx",
                voices = "voices.bin",
                tokens = "tokens.txt",
                dataDir = "espeak-ng-data",
                lexicon = "lexicon-us-en.txt,lexicon-zh.txt"
            ),
            requiredFiles = listOf(
                "model.int8.onnx",
                "voices.bin",
                "tokens.txt",
                "lexicon-us-en.txt",
                "lexicon-zh.txt"
            ),
            requiredDirectories = listOf("espeak-ng-data"),
            archive = RemoteFile(
                name = "kokoro-int8-multi-lang-v1_1.tar.bz2",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2",
                sha256 = "a1e94694776049035c4f2c6529f003aaece993c76aae9a78995831c3c4dcafc6",
                size = 147_031_220
            )
        ),
        TtsModel.GOSIA to TtsModelPackage(
            directoryName = "gosia",
            engineFiles = SherpaModelFiles.Vits(
                model = "pl_PL-gosia-medium.onnx",
                tokens = "tokens.txt",
                dataDir = "espeak-ng-data"
            ),
            requiredFiles = listOf("pl_PL-gosia-medium.onnx", "tokens.txt"),
            requiredDirectories = listOf("espeak-ng-data"),
            archive = RemoteFile(
                name = "vits-piper-pl_PL-gosia-medium-int8.tar.bz2",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pl_PL-gosia-medium-int8.tar.bz2",
                sha256 = "72acac4c4b031725c41a61b3af0314a3d30e1ec2cd83ee410ea5f9e6d2d9d4fb",
                size = 21_109_262
            )
        ),
        TtsModel.PIPER_BASS_HIGH to piperPackage(
            directoryName = "piper-bass-high",
            modelName = "pl_PL-bass-high.onnx",
            archiveName = "vits-piper-pl_PL-bass-high-int8.tar.bz2",
            sha256 = "e6667ea284c15b90e5fd6350b8005a0a4d7c1ce1cc1ce647ee64d2650a57a12f",
            size = 35_321_483
        ),
        TtsModel.PIPER_DARKMAN_MEDIUM to piperPackage(
            directoryName = "piper-darkman-medium",
            modelName = "pl_PL-darkman-medium.onnx",
            archiveName = "vits-piper-pl_PL-darkman-medium-int8.tar.bz2",
            sha256 = "0ec47b7d591e48913da887ea7e81287f4ef621d9c5aa1848700d2d736ed3f99c",
            size = 21_078_264
        ),
        TtsModel.PIPER_JARVIS_MEDIUM to piperPackage(
            directoryName = "piper-jarvis-medium",
            modelName = "pl_PL-jarvis_wg_glos-medium.onnx",
            archiveName = "vits-piper-pl_PL-jarvis_wg_glos-medium-int8.tar.bz2",
            sha256 = "7d01e13ffe5a6773a99c7005e926d67122937b4333cb771e6217820145889921",
            size = 21_063_665
        ),
        TtsModel.PIPER_JUSTYNA_MEDIUM to piperPackage(
            directoryName = "piper-justyna-medium",
            modelName = "pl_PL-justyna_wg_glos-medium.onnx",
            archiveName = "vits-piper-pl_PL-justyna_wg_glos-medium-int8.tar.bz2",
            sha256 = "9051a597ff6b475ecec976ffcddcc411a06b96994f2c92cf74ff96499916dbc4",
            size = 21_068_261
        ),
        TtsModel.PIPER_MC_SPEECH_MEDIUM to piperPackage(
            directoryName = "piper-mc-speech-medium",
            modelName = "pl_PL-mc_speech-medium.onnx",
            archiveName = "vits-piper-pl_PL-mc_speech-medium-int8.tar.bz2",
            sha256 = "c67a1b152bced5cb0d8aaa97fa12700aab743feaa756e1ac903cc7e92174f171",
            size = 20_938_473
        ),
        TtsModel.PIPER_MESKI_MEDIUM to piperPackage(
            directoryName = "piper-meski-medium",
            modelName = "pl_PL-meski_wg_glos-medium.onnx",
            archiveName = "vits-piper-pl_PL-meski_wg_glos-medium-int8.tar.bz2",
            sha256 = "e611de2d9f4d99650d90a40e1587957ecf08e5e479e644c8a94e5f76cae91036",
            size = 21_108_038
        ),
        TtsModel.PIPER_ZENSKI_MEDIUM to piperPackage(
            directoryName = "piper-zenski-medium",
            modelName = "pl_PL-zenski_wg_glos-medium.onnx",
            archiveName = "vits-piper-pl_PL-zenski_wg_glos-medium-int8.tar.bz2",
            sha256 = "b37ee12e4ee6369f73f735feb5fd34ef33ae2479b12baff9a62c717c43f02c6c",
            size = 21_028_352
        )
    )

    fun packageFor(model: TtsModel): TtsModelPackage? = packages[model]

    fun directoryName(model: TtsModel): String =
        packageFor(model)?.directoryName ?: model.name.lowercase()
}

private fun piperPackage(
    directoryName: String,
    modelName: String,
    archiveName: String,
    sha256: String,
    size: Long
) = TtsModelPackage(
    directoryName = directoryName,
    engineFiles = SherpaModelFiles.Vits(
        model = modelName,
        tokens = "tokens.txt",
        dataDir = "espeak-ng-data"
    ),
    requiredFiles = listOf(modelName, "tokens.txt"),
    requiredDirectories = listOf("espeak-ng-data"),
    archive = RemoteFile(
        name = archiveName,
        url = "$PIPER_RELEASE_ROOT/$archiveName",
        sha256 = sha256,
        size = size
    )
)

internal data class RemoteFile(
    val name: String,
    val url: String,
    val sha256: String,
    val size: Long
)

internal fun TtsModelPackage.isComplete(directory: File): Boolean =
    requiredFiles.all { File(directory, it).isFile } &&
        requiredDirectories.all {
            val required = File(directory, it)
            required.isDirectory && required.walkTopDown().any(File::isFile)
        }

private const val SUPERTONIC_HF_REVISION = "cca5a0e6c96e1d2c720986bf7e75fcc81dee3ae4"
private const val PIPER_RELEASE_ROOT =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
private const val SUPERTONIC_HF_ROOT =
    "https://huggingface.co/csukuangfj2/sherpa-onnx-supertonic-3-tts-int8-2026-05-11/resolve/$SUPERTONIC_HF_REVISION"

private val SUPERTONIC_FILES = listOf(
    RemoteFile(
        "LICENSE",
        "https://huggingface.co/Supertone/supertonic-3/resolve/724fb5abbf5502583fb520898d45929e62f02c0b/LICENSE",
        "0d944a9110fed9a9602d60e0423a272903e7bd21ab060490774efc77c2275e9f",
        15_007
    ),
    RemoteFile(
        "duration_predictor.int8.onnx",
        "$SUPERTONIC_HF_ROOT/duration_predictor.int8.onnx",
        "c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db",
        3_700_147
    ),
    RemoteFile(
        "text_encoder.int8.onnx",
        "$SUPERTONIC_HF_ROOT/text_encoder.int8.onnx",
        "c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff",
        36_416_150
    ),
    RemoteFile(
        "tts.json",
        "$SUPERTONIC_HF_ROOT/tts.json",
        "42078d3aef1cd43ab43021f3c54f47d2d75ceb4e75f627f118890128b06a0d09",
        8_253
    ),
    RemoteFile(
        "unicode_indexer.bin",
        "$SUPERTONIC_HF_ROOT/unicode_indexer.bin",
        "8402ca48e5189a8950138580b0fff64db6f072f24ac07cd54ba8b2fbb9883b30",
        262_144
    ),
    RemoteFile(
        "vector_estimator.int8.onnx",
        "$SUPERTONIC_HF_ROOT/vector_estimator.int8.onnx",
        "20cd86fa5c6effedfda0e7cffe5b0569ca401c440a0c3a1d72bf39286c0db3fd",
        78_400_833
    ),
    RemoteFile(
        "vocoder.int8.onnx",
        "$SUPERTONIC_HF_ROOT/vocoder.int8.onnx",
        "e923d60f53f95eb1ce235f1dc33ec56d9c057823c96fa6f8acf98f32b0da6152",
        25_991_073
    ),
    RemoteFile(
        "voice.bin",
        "$SUPERTONIC_HF_ROOT/voice.bin",
        "67d5209b0ee8ce6c74105ffbe12fe6a7628aea3b4ba2fcb308a4a67938a93ce8",
        517_168
    )
)
