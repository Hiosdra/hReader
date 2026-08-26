package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
import java.io.File

internal sealed interface TtsModelFiles

internal sealed interface SherpaModelFiles : TtsModelFiles {
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

    data class Kitten(
        val model: String,
        val voices: String,
        val tokens: String,
        val dataDir: String
    ) : SherpaModelFiles

    data class Matcha(
        val acousticModel: String,
        val vocoder: String,
        val lexicon: String,
        val tokens: String,
        val dataDir: String,
        val dictDir: String
    ) : SherpaModelFiles
}

internal data class ChatterboxModelFiles(
    val tokenizer: String,
    val conditionals: String,
    val modules: List<String>
) : TtsModelFiles

internal data class TtsModelPackage(
    val directoryName: String,
    val engineFiles: TtsModelFiles,
    val requiredFiles: List<String>,
    val requiredDirectories: List<String> = emptyList(),
    val files: List<RemoteFile> = emptyList(),
    val supplementalFiles: List<RemoteFile> = emptyList(),
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
        TtsModel.PIPER_LESSAC_HIGH to piperPackage(
            directoryName = "piper-lessac-high",
            modelName = "en_US-lessac-high.onnx",
            archiveName = "vits-piper-en_US-lessac-high-int8.tar.bz2",
            sha256 = "f06e85ae07ca11adae6d3b8cedff4b600fa1181a354f1f86779866b86b2fdb33",
            size = 35_022_847
        ),
        TtsModel.KITTEN_MINI to TtsModelPackage(
            directoryName = "kitten-mini-en-v0_8",
            engineFiles = SherpaModelFiles.Kitten(
                model = "model.onnx",
                voices = "voices.bin",
                tokens = "tokens.txt",
                dataDir = "espeak-ng-data"
            ),
            requiredFiles = listOf("model.onnx", "voices.bin", "tokens.txt"),
            requiredDirectories = listOf("espeak-ng-data"),
            archive = RemoteFile(
                name = "kitten-mini-en-v0_8.tar.bz2",
                url = "$TTS_RELEASE_ROOT/kitten-mini-en-v0_8.tar.bz2",
                sha256 = "518f9b130320f690d5b5476df77bde4215fca67773cda16710318e5081234b9d",
                size = 67_547_594
            )
        ),
        TtsModel.MATCHA_LJSPEECH to TtsModelPackage(
            directoryName = "matcha-icefall-en_US-ljspeech",
            engineFiles = SherpaModelFiles.Matcha(
                acousticModel = "model-steps-3.onnx",
                vocoder = VOCOS_FILE.name,
                lexicon = "",
                tokens = "tokens.txt",
                dataDir = "espeak-ng-data",
                dictDir = ""
            ),
            requiredFiles = listOf("model-steps-3.onnx", VOCOS_FILE.name, "tokens.txt"),
            requiredDirectories = listOf("espeak-ng-data"),
            supplementalFiles = listOf(VOCOS_FILE),
            archive = RemoteFile(
                name = "matcha-icefall-en_US-ljspeech.tar.bz2",
                url = "$TTS_RELEASE_ROOT/matcha-icefall-en_US-ljspeech.tar.bz2",
                sha256 = "ea75702da7456a8b1874728278a835220dc8a26f4e8bd93c83bf53dc27679845",
                size = 76_741_121
            )
        ),
        TtsModel.CHATTERBOX_EXECUTORCH to chatterboxPackage()
    )

    private fun chatterboxPackage() = TtsModelPackage(
        directoryName = "chatterbox-executorch",
        engineFiles = ChatterboxModelFiles(
            tokenizer = CHATTERBOX_TOKENIZER.name,
            conditionals = CHATTERBOX_CONDITIONALS.name,
            modules = CHATTERBOX_MODULES.map(RemoteFile::name)
        ),
        requiredFiles = listOf(
            CHATTERBOX_TOKENIZER.name,
            CHATTERBOX_CONDITIONALS.name
        ) + CHATTERBOX_MODULES.map(RemoteFile::name),
        files = listOf(CHATTERBOX_TOKENIZER, CHATTERBOX_CONDITIONALS) + CHATTERBOX_MODULES
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
        url = "$TTS_RELEASE_ROOT/$archiveName",
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
private const val CHATTERBOX_EXECUTORCH_REVISION = "e444904352a22332a8083d6fb7b9354ee5e96daf"
private const val CHATTERBOX_DATA_REVISION = "5bb1f6ee58e50c3b8d408bc82a6d3740c2db6e18"
private const val TTS_RELEASE_ROOT =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
private const val SUPERTONIC_HF_ROOT =
    "https://huggingface.co/csukuangfj2/sherpa-onnx-supertonic-3-tts-int8-2026-05-11/resolve/$SUPERTONIC_HF_REVISION"

private val VOCOS_FILE = RemoteFile(
    name = "vocos-22khz-univ.onnx",
    url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos-22khz-univ.onnx",
    sha256 = "0574a135aa1db2de6e181050db2ec528496cacd4a4701fc5d7faf9f9804c0081",
    size = 53_884_024
)

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

private val CHATTERBOX_TOKENIZER = RemoteFile(
    name = "grapheme_mtl_merged_expanded_v1.json",
    url = "https://huggingface.co/ResembleAI/chatterbox/resolve/$CHATTERBOX_DATA_REVISION/grapheme_mtl_merged_expanded_v1.json",
    sha256 = "69632f47220a788a52ce2661d096453c5655e9bf25289d89a8d832c46ee07dbf",
    size = 69_989
)

private val CHATTERBOX_CONDITIONALS = RemoteFile(
    name = "conds.pt",
    url = "https://huggingface.co/ResembleAI/chatterbox/resolve/$CHATTERBOX_DATA_REVISION/conds.pt",
    sha256 = "6552d70568833628ba019c6b03459e77fe71ca197d5c560cef9411bee9d87f4e",
    size = 107_374
)

private val CHATTERBOX_MODULES = listOf(
    RemoteFile(
        name = "t3_cond_speech_emb.pte",
        url = "https://huggingface.co/acul3/chatterbox-executorch/resolve/$CHATTERBOX_EXECUTORCH_REVISION/t3_cond_speech_emb.pte",
        sha256 = "f443eed95cdb990151365c76f5096bce49c42800b81f0c2b76bcdcad574653a0",
        size = 50_358_400
    ),
    RemoteFile(
        name = "t3_cond_enc.pte",
        url = "https://huggingface.co/acul3/chatterbox-executorch/resolve/$CHATTERBOX_EXECUTORCH_REVISION/t3_cond_enc.pte",
        sha256 = "2e953bfa6e9289a7a97ca527592aed0e6d2cb58b29126d5780410455f091ac3d",
        size = 18_011_520
    ),
    RemoteFile(
        name = "t3_prefill.pte",
        url = "https://huggingface.co/acul3/chatterbox-executorch/resolve/$CHATTERBOX_EXECUTORCH_REVISION/t3_prefill.pte",
        sha256 = "721d268943818b5a90530e7dadfae0edb9d3fd0227f93557c2a153ecee675e51",
        size = 2_116_538_624
    ),
    RemoteFile(
        name = "t3_decode.pte",
        url = "https://huggingface.co/acul3/chatterbox-executorch/resolve/$CHATTERBOX_EXECUTORCH_REVISION/t3_decode.pte",
        sha256 = "0704783d9c4c7c621f9f398de40d8fc3829ad2769bca8194505cd86f0aee9ca3",
        size = 2_098_677_504
    ),
    RemoteFile(
        name = "s3gen_encoder.pte",
        url = "https://huggingface.co/acul3/chatterbox-executorch/resolve/$CHATTERBOX_EXECUTORCH_REVISION/s3gen_encoder.pte",
        sha256 = "4f087015bb08526d0689eed1f94baa60761d1777d37b25f7944d6f4944ecce88",
        size = 185_724_864
    ),
    RemoteFile(
        name = "cfm_step.pte",
        url = "https://huggingface.co/acul3/chatterbox-executorch/resolve/$CHATTERBOX_EXECUTORCH_REVISION/cfm_step.pte",
        sha256 = "5ffb90558c0dbada80ac94bd9a6864101cc07826c0f9192dfd0363d190922079",
        size = 286_434_240
    ),
    RemoteFile(
        name = "hifigan.pte",
        url = "https://huggingface.co/acul3/chatterbox-executorch/resolve/$CHATTERBOX_EXECUTORCH_REVISION/hifigan.pte",
        sha256 = "f5239799e82fb2be2aeb63db2de9bef676d2decf7905692a5ccafac7ae3530e2",
        size = 83_634_944
    )
)
