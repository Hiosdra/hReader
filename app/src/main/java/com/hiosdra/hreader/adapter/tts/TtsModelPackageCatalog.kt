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

internal data class MnnModelFiles(
    val config: String,
    val referenceAudio: String
) : TtsModelFiles

internal data class TtsModelPackage(
    val directoryName: String,
    val engineFiles: TtsModelFiles,
    val requiredFiles: List<String>,
    val requiredDirectories: List<String> = emptyList(),
    val files: List<RemoteFile> = emptyList(),
    val supplementalFiles: List<RemoteFile> = emptyList(),
    val archive: RemoteFile? = null,
    val generatedFiles: List<GeneratedFile> = emptyList()
)

internal data class GeneratedFile(
    val name: String,
    val content: String
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
        TtsModel.MNN_0_6B_BASE_INT8 to mnnPackage(
            directoryName = "mnn-0_6b-base-int8",
            files = MNN_INT8_FILES
        ),
        TtsModel.MNN_0_6B_BASE_FP16 to mnnPackage(
            directoryName = "mnn-0_6b-base-fp16",
            files = MNN_FP16_FILES
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
        url = "$TTS_RELEASE_ROOT/$archiveName",
        sha256 = sha256,
        size = size
    )
)

private fun mnnPackage(
    directoryName: String,
    files: List<RemoteFile>
) = TtsModelPackage(
    directoryName = directoryName,
    engineFiles = MnnModelFiles(
        config = "config.json",
        referenceAudio = MNN_REFERENCE_AUDIO.name
    ),
    requiredFiles = files.map(RemoteFile::name) + listOf("config.json", MNN_REFERENCE_AUDIO.name),
    files = files + MNN_REFERENCE_AUDIO,
    generatedFiles = listOf(GeneratedFile("config.json", MNN_CONFIG))
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

internal fun TtsModelPackage.requiredStorageBytes(): Long {
    val downloadBytes = (archive?.size ?: 0L) +
        files.sumOf(RemoteFile::size) +
        supplementalFiles.sumOf(RemoteFile::size)
    val extractionHeadroom = archive?.size ?: 0L
    return downloadBytes + extractionHeadroom + TTS_STORAGE_HEADROOM_BYTES
}

internal fun hasEnoughTtsModelStorage(availableBytes: Long, requiredBytes: Long): Boolean =
    availableBytes >= requiredBytes

private const val SUPERTONIC_HF_REVISION = "cca5a0e6c96e1d2c720986bf7e75fcc81dee3ae4"
private const val TTS_STORAGE_HEADROOM_BYTES = 128L * 1024 * 1024
private const val TTS_RELEASE_ROOT =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
private const val SUPERTONIC_HF_ROOT =
    "https://huggingface.co/csukuangfj2/sherpa-onnx-supertonic-3-tts-int8-2026-05-11/resolve/$SUPERTONIC_HF_REVISION"
private const val MNN_INT8_ROOT =
    "https://www.modelscope.cn/models/huangzhengxiang/Qwen3-TTS-0.6B-Base-INT8-MNN"
private const val MNN_FP16_ROOT =
    "https://www.modelscope.cn/models/huangzhengxiang/Qwen3-TTS-0.6B-Base-FP16-MNN"

private fun mnnFile(
    root: String,
    name: String,
    revision: String,
    sha256: String,
    size: Long
) = RemoteFile(
    name = name,
    url = "$root/resolve/$revision/$name",
    sha256 = sha256,
    size = size
)

private val MNN_REFERENCE_AUDIO = RemoteFile(
    name = "qwen3_tts_ref.wav",
    url = "https://modelscope.cn/datasets/huangzhengxiang/qwen3-tts-ref/resolve/master/qwen3_tts_ref.wav",
    sha256 = "26c7f5a1a51ae462c941e50b8b20b97127125209abe5bd750dfdedaf2d80e495",
    size = 307_244
)

private const val MNN_CONFIG = """
{
  "backend_type": "cpu",
  "thread_num": 4,
  "precision": "low",
  "memory": "low",
  "llm_model": "talker.mnn",
  "llm_weight": "talker.mnn.weight",
  "llm_config": "llm_config.json",
  "tokenizer_file": "tokenizer.txt",
  "mllm": {
    "backend_type": "cpu",
    "thread_num": 4,
    "precision": "low",
    "memory": "low"
  },
  "async": false
}
"""

private val MNN_INT8_FILES = listOf(
    mnnFile(MNN_INT8_ROOT, "code_predictor.mnn", "83cbbeb99230dc4df587899097e5c8de757cd73d", "997dccf02e17dbc883ba2d2980f99d2b336ae796530d681a3b1e4cf9849aec8c", 96_392),
    mnnFile(MNN_INT8_ROOT, "code_predictor.mnn.weight", "5d613f59e85ef86994d95a155829222513bc24cd", "a5035668d71a8149685aadc734bc45267a8c6aab04c81e6047a742c3a2b88e7a", 117_095_212),
    mnnFile(MNN_INT8_ROOT, "code_predictor_embeddings_bf16.bin", "f88264e2e8b409a391d5dbd9a0b0fb593ce51261", "331c7a2779ccbeca7d5ff2d59e8877da59bc59238982bdf7d6d4bd85f6c9c8a0", 62_914_560),
    mnnFile(MNN_INT8_ROOT, "codec_embedder.mnn", "a7de264a9ff4d452cba1ad4d95c4c97ddf189468", "53d3ef6819ed01bcc39315549a43e5893dcbf60771e438a954075646af5400ee", 960),
    mnnFile(MNN_INT8_ROOT, "codec_embedder.mnn.weight", "cb0b4c4739bd74fdebad4ed5d77288543e69d6b2", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
    mnnFile(MNN_INT8_ROOT, "llm_config.json", "efaa3e10560af0ffe011f14a32ddfdd5bdabb406", "1aa8c7f672c701089903ae0807b6f212929883f5ab3fca8ee16806b9f8ffe61a", 2_344),
    mnnFile(MNN_INT8_ROOT, "speaker_encoder.mnn", "75499d188a8b3b91752d1cc1a61809b199d24101", "39960423842e0490f371f367f981d5c4c44ff654f73b7b5fbaea5a50d0372dbe", 64_648),
    mnnFile(MNN_INT8_ROOT, "speaker_encoder.mnn.weight", "0f944c63d500b6aa1992ea708e176a3eff07e705", "917fa16df957daec832c3be8a245d6232ed743bbea157d447f6c74418067e0f1", 9_948_644),
    mnnFile(MNN_INT8_ROOT, "speech_decoder.mnn", "3d5a8535bace523a06264d08c0f6f409ba9a468a", "a8873a83218afe89f032004d611f4332d962df1eb8216cc70259dff5ec976404", 607_112),
    mnnFile(MNN_INT8_ROOT, "speech_decoder.mnn.weight", "b3827d3a42c82bd36c419a8ce955e901aebb5424", "b0f80f9e365395cdecf0aa2e633fc4b535dcc4f94b991e85c9ab837a271cf739", 146_811_022),
    mnnFile(MNN_INT8_ROOT, "talker.mnn", "f8452def577159f1ad40d67407dedcf89eece90e", "354ae0ba9f57261e6ee61be96252b56c152b11322803bec5c8ad417de9e3f476", 468_080),
    mnnFile(MNN_INT8_ROOT, "talker.mnn.weight", "200d657f09bd56f00bf2782c2dcad866404bdd5e", "51973f76a0202a4ff0463da77fbd7598741949eef02b0b119733c1c93541783c", 478_542_762),
    mnnFile(MNN_INT8_ROOT, "talker_embeddings_bf16.bin", "be76186e21b1bfca22b1c8bc601889a87bc067be", "733b4f6c4afcce1d4880e65691c3284046a5f01382398a3008d4bdfc7156d41d", 6_291_456),
    mnnFile(MNN_INT8_ROOT, "talker_text_embeddings_bf16.bin", "45417ef5f1a0b62a9adbf3f9f49e601cc0f261cf", "1ed4fbb339ef48327faee2aa2154756320fb54e9131976c7b063827ed984edf4", 622_329_856),
    mnnFile(MNN_INT8_ROOT, "tokenizer.txt", "cfc9c9ff6f976fbde0be10b97d3d927c2c4a6049", "837c69c8566f5ed0892e0ce089e3292f5826c9256433324cb65b7ec833fbbc39", 3_193_703)
)

private val MNN_FP16_FILES = listOf(
    mnnFile(MNN_FP16_ROOT, "code_predictor.mnn", "b4a2af0e8a4582449bca1c7a9ffa93c1725b8e4c", "0c1129309586ab8abc60fa545e0eddb5b889939fe0ae8229fab3270ce0405ef8", 96_392),
    mnnFile(MNN_FP16_ROOT, "code_predictor.mnn.weight", "65e62d4cd3b808b03149fbbbdb3555851d151f3d", "20b9064b77cfab0969605f1e804f0b1215ed37d1c8865aa743f3b470b35670f2", 220_301_312),
    mnnFile(MNN_FP16_ROOT, "code_predictor_embeddings_bf16.bin", "d245ffe8cdcf4b90e1efd5c7447cce07ca0c1728", "331c7a2779ccbeca7d5ff2d59e8877da59bc59238982bdf7d6d4bd85f6c9c8a0", 62_914_560),
    mnnFile(MNN_FP16_ROOT, "codec_embedder.mnn", "615de23ad6ceb342925a1af9f0fe9f9fcdb3d08a", "a1d541a210eb83312157891dada184f7e6ecaa189fd3f1c682059815cde7e216", 960),
    mnnFile(MNN_FP16_ROOT, "codec_embedder.mnn.weight", "ee9c3e0b0a9f36e56ab31637fb3db054d2ed5b31", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
    mnnFile(MNN_FP16_ROOT, "llm_config.json", "d6a5c6bb218bc6ae16e8266f8ca1789db41f71b1", "1aa8c7f672c701089903ae0807b6f212929883f5ab3fca8ee16806b9f8ffe61a", 2_344),
    mnnFile(MNN_FP16_ROOT, "speaker_encoder.mnn", "b3eb26d8eb9ed126a1d40ec56764332e3b347417", "b5a67c7ff226349e50abaf593954ff91c5b557ed35cfe7386c6470e678b272b3", 64_632),
    mnnFile(MNN_FP16_ROOT, "speaker_encoder.mnn.weight", "ff4a869a9c5fb0c61c0fab5ae3915b56a0ba73a0", "038ee4a2b6830730a6b02e371f3a9bb1962802094366e8e4d4e51afd6d527d43", 17_730_816),
    mnnFile(MNN_FP16_ROOT, "speech_decoder.mnn", "f1bc2e80a97017a2ae821060c14968b6f689ed7d", "403d4fcba6990b04d79e12e28853bbaabb68ac76de21fdc375295d3280933b1c", 17_329_240),
    mnnFile(MNN_FP16_ROOT, "speech_decoder.mnn.weight", "91f9f595a5688bfd2268bcad58064b4c0e64ea65", "b4e7fc215369ae7afae008f559692bd39df477a84250602933a2ca6f55e44e00", 211_530_436),
    mnnFile(MNN_FP16_ROOT, "talker.mnn", "d047ee039aa691f0737220eeb4c255294330a88b", "f1a996ae1ad771c39dc949031e0093e95a122e0a834cdf4dcb793c62fa0a226f", 468_080),
    mnnFile(MNN_FP16_ROOT, "talker.mnn.weight", "3333ca3e54e3f1349396601974570ace172a612b", "173a28c31f4e2a66641d7f6b415e56c4c93f9c42f9857fd9e5e213fe2adb5ff9", 900_214_784),
    mnnFile(MNN_FP16_ROOT, "talker_embeddings_bf16.bin", "933852695c287020bd455ade0fbff7f7efc5653b", "733b4f6c4afcce1d4880e65691c3284046a5f01382398a3008d4bdfc7156d41d", 6_291_456),
    mnnFile(MNN_FP16_ROOT, "talker_text_embeddings_bf16.bin", "cf962620a534173bf6803d9d7448730ce8748048", "1ed4fbb339ef48327faee2aa2154756320fb54e9131976c7b063827ed984edf4", 622_329_856),
    mnnFile(MNN_FP16_ROOT, "tokenizer.txt", "502b2ec13a92ec92f0e24d2580ffccce6d095962", "837c69c8566f5ed0892e0ce089e3292f5826c9256433324cb65b7ec833fbbc39", 3_193_703)
)

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
