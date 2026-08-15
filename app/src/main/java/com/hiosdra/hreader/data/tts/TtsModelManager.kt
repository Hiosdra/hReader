package com.hiosdra.hreader.data.tts

import android.content.Context
import com.hiosdra.hreader.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class TtsModelManager(
    context: Context,
    client: OkHttpClient
) {
    private val appContext = context.applicationContext
    private val client = client.newBuilder().apply { interceptors().clear() }.build()
    private val modelRoot = File(appContext.filesDir, "tts_models")
    private val modelLocks = TtsModel.entries.associateWith { Mutex() }
    private val _statuses = MutableStateFlow(currentStatuses())
    val statuses: StateFlow<Map<TtsModel, TtsModelStatus>> = _statuses.asStateFlow()

    fun directory(model: TtsModel): File = File(modelRoot, model.directoryName)

    internal fun markDownloadEnqueued(model: TtsModel) {
        _statuses.value = _statuses.value + (model to TtsModelStatus.Downloading(0f))
    }

    internal fun markDownloadCancelled(model: TtsModel) {
        if (_statuses.value[model] is TtsModelStatus.Downloading) {
            _statuses.value = _statuses.value + (model to TtsModelStatus.NotInstalled)
        }
    }

    internal fun markDownloadFailed(model: TtsModel, message: String) {
        _statuses.value = _statuses.value + (model to TtsModelStatus.Failed(message))
    }

    internal fun markDownloadRetrying(model: TtsModel) {
        val progress = (_statuses.value[model] as? TtsModelStatus.Downloading)?.progress ?: 0f
        _statuses.value = _statuses.value + (model to TtsModelStatus.Downloading(progress))
    }

    suspend fun download(model: TtsModel) = withContext(Dispatchers.IO) {
        val artifact = model.artifact ?: return@withContext
        modelLocks.getValue(model).withLock {
            if (_statuses.value[model] == TtsModelStatus.Available) return@withLock
            modelRoot.mkdirs()
            val archive = File(modelRoot, "${model.directoryName}.download")
            val staging = File(modelRoot, "${model.directoryName}.staging")
            try {
                _statuses.value = _statuses.value + (model to TtsModelStatus.Downloading(0f))
                staging.deleteRecursively()
                staging.mkdirs()
                if (artifact.files.isEmpty()) {
                    downloadArchive(model, artifact, archive, staging)
                } else {
                    try {
                        downloadFiles(model, artifact, staging)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        staging.deleteRecursively()
                        staging.mkdirs()
                        downloadArchive(model, artifact, archive, staging)
                    }
                }
                val content = staging.singleFileOrSelf()
                check(artifact.isComplete(content)) {
                    "Model download is incomplete"
                }
                replaceInstallation(model, content)
                _statuses.value = _statuses.value + (model to TtsModelStatus.Available)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markDownloadFailed(model, appContext.getString(R.string.tts_model_install_failed))
                throw e
            } finally {
                archive.delete()
                staging.deleteRecursively()
            }
        }
    }

    private suspend fun downloadFiles(model: TtsModel, artifact: ModelArtifact, staging: File) {
        val total = artifact.files.sumOf { it.size }
        var downloaded = 0L
        artifact.files.forEach { remote ->
            val output = File(staging, remote.name)
            downloadTo(remote.url, output) { count ->
                downloaded += count
                updateProgress(model, downloaded, total)
            }
            check(output.sha256() == remote.sha256) {
                "Downloaded ${remote.name} failed integrity check"
            }
        }
    }

    private suspend fun downloadArchive(
        model: TtsModel,
        artifact: ModelArtifact,
        archive: File,
        staging: File
    ) {
        val source = checkNotNull(artifact.archive)
        var downloaded = 0L
        downloadTo(source.url, archive) { count ->
            downloaded += count
            updateProgress(model, downloaded, source.size)
        }
        check(archive.sha256() == source.sha256) { "Downloaded model failed integrity check" }
        extract(archive, staging)
    }

    private suspend fun downloadTo(url: String, output: File, onBytes: (Long) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed (${response.code})" }
            val body = checkNotNull(response.body)
            body.byteStream().use { input ->
                FileOutputStream(output).use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                        onBytes(count.toLong())
                    }
                }
            }
        }
    }

    private fun updateProgress(model: TtsModel, downloaded: Long, total: Long) {
        if (total <= 0) return
        _statuses.value = _statuses.value + (
            model to TtsModelStatus.Downloading((downloaded.toFloat() / total).coerceIn(0f, 1f))
        )
    }

    suspend fun remove(model: TtsModel) = withContext(Dispatchers.IO) {
        if (model.bundled) return@withContext
        modelLocks.getValue(model).withLock {
            runCatching {
                val target = directory(model)
                check(!target.exists() || target.deleteRecursively()) {
                    "Could not remove model files"
                }
            }.fold(
                onSuccess = {
                    _statuses.value = _statuses.value + (model to TtsModelStatus.NotInstalled)
                },
                onFailure = {
                    _statuses.value = _statuses.value + (
                        model to TtsModelStatus.Failed(
                            appContext.getString(R.string.tts_model_remove_failed)
                        )
                    )
                }
            )
        }
    }

    private fun extract(archive: File, destination: File) {
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive)))).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val output = File(destination, entry.name).canonicalFile
                check(output.path.startsWith(destination.canonicalPath + File.separator)) {
                    "Unsafe path in model archive"
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    check(entry.isFile) { "Unsupported entry in model archive" }
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { tar.copyTo(it) }
                }
                entry = tar.nextEntry
            }
        }
    }

    private fun replaceInstallation(model: TtsModel, content: File) {
        val target = directory(model)
        val backup = File(modelRoot, "${model.directoryName}.backup")
        backup.deleteRecursively()
        if (target.exists()) check(target.renameTo(backup)) { "Could not preserve existing model" }
        val installed = content.renameTo(target) || content.copyRecursively(target, overwrite = true)
        if (!installed) {
            target.deleteRecursively()
            backup.renameTo(target)
            error("Could not install model")
        }
        backup.deleteRecursively()
    }

    private fun currentStatuses() = TtsModel.entries.associateWith {
        if (it.bundled || it.artifact?.isComplete(directory(it)) == true) {
            TtsModelStatus.Available
        } else {
            TtsModelStatus.NotInstalled
        }
    }

    private fun File.singleFileOrSelf(): File = listFiles()?.singleOrNull()?.takeIf { it.isDirectory } ?: this

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private data class ModelArtifact(
    val requiredFiles: List<String>,
    val requiredDirectories: List<String> = emptyList(),
    val files: List<RemoteFile> = emptyList(),
    val archive: RemoteFile? = null
)

private fun ModelArtifact.isComplete(directory: File): Boolean =
    requiredFiles.all { File(directory, it).isFile } &&
        requiredDirectories.all {
            val required = File(directory, it)
            required.isDirectory && required.walkTopDown().any(File::isFile)
        }

private data class RemoteFile(
    val name: String,
    val url: String,
    val sha256: String,
    val size: Long
)

private val TtsModel.directoryName: String
    get() = name.lowercase()

private val TtsModel.artifact: ModelArtifact?
    get() = when (this) {
        TtsModel.SUPERTONIC -> ModelArtifact(
            requiredFiles = SUPERTONIC_FILES.map { it.name },
            files = SUPERTONIC_FILES,
            archive = RemoteFile(
                name = "supertonic-3-int8-2026-05-11.tar.bz2",
                url = "https://github.com/Hiosdra/hReader/releases/download/tts-models-v1/supertonic-3-int8-2026-05-11.tar.bz2",
                sha256 = "2b9f11979b0b0f85a2653e63ea567bc819994822e0ff3b7b5ee1b06068fc5c78",
                size = 128_784_503
            )
        )
        TtsModel.KOKORO -> ModelArtifact(
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
        )
        TtsModel.GOSIA -> ModelArtifact(
            requiredFiles = listOf("pl_PL-gosia-medium.onnx", "tokens.txt"),
            requiredDirectories = listOf("espeak-ng-data"),
            archive = RemoteFile(
                name = "vits-piper-pl_PL-gosia-medium-int8.tar.bz2",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pl_PL-gosia-medium-int8.tar.bz2",
                sha256 = "72acac4c4b031725c41a61b3af0314a3d30e1ec2cd83ee410ea5f9e6d2d9d4fb",
                size = 21_109_262
            )
        )
        else -> null
    }

private const val SUPERTONIC_HF_REVISION = "cca5a0e6c96e1d2c720986bf7e75fcc81dee3ae4"
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
