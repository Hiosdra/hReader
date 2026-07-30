package com.hiosdra.hreader.data.tts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val client = client.newBuilder().apply { interceptors().clear() }.build()
    private val modelRoot = File(context.filesDir, "tts_models")
    private val _statuses = MutableStateFlow(currentStatuses())
    val statuses: StateFlow<Map<TtsModel, TtsModelStatus>> = _statuses.asStateFlow()

    fun directory(model: TtsModel): File = File(modelRoot, model.directoryName)

    suspend fun download(model: TtsModel) = withContext(Dispatchers.IO) {
        val artifact = model.artifact ?: return@withContext
        if (_statuses.value[model] is TtsModelStatus.Downloading) return@withContext
        modelRoot.mkdirs()
        val archive = File(modelRoot, "${model.directoryName}.download")
        val staging = File(modelRoot, "${model.directoryName}.staging")
        runCatching {
            _statuses.value = _statuses.value + (model to TtsModelStatus.Downloading(0f))
            val request = Request.Builder().url(artifact.url).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed (${response.code})" }
                val body = checkNotNull(response.body)
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (total > 0) {
                                _statuses.value = _statuses.value + (
                                    model to TtsModelStatus.Downloading(downloaded.toFloat() / total)
                                )
                            }
                        }
                    }
                }
            }
            check(archive.sha256() == artifact.sha256) { "Downloaded model failed integrity check" }
            staging.deleteRecursively()
            staging.mkdirs()
            extract(archive, staging)
            val content = staging.singleFileOrSelf()
            check(artifact.requiredFiles.all { File(content, it).exists() }) { "Model archive is incomplete" }
            replaceInstallation(model, content)
            _statuses.value = _statuses.value + (model to TtsModelStatus.Available)
        }.onFailure {
            _statuses.value = _statuses.value + (
                model to TtsModelStatus.Failed(it.message ?: "Could not install model")
            )
        }
        archive.delete()
        staging.deleteRecursively()
    }

    suspend fun remove(model: TtsModel) = withContext(Dispatchers.IO) {
        if (model.bundled) return@withContext
        directory(model).deleteRecursively()
        _statuses.value = _statuses.value + (model to TtsModelStatus.NotInstalled)
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
        if (it.bundled || it.artifact?.requiredFiles?.all { file -> File(directory(it), file).exists() } == true) {
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
    val url: String,
    val sha256: String,
    val requiredFiles: List<String>
)

private val TtsModel.directoryName: String
    get() = name.lowercase()

private val TtsModel.artifact: ModelArtifact?
    get() = when (this) {
        TtsModel.KOKORO -> ModelArtifact(
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2",
            sha256 = "a1e94694776049035c4f2c6529f003aaece993c76aae9a78995831c3c4dcafc6",
            requiredFiles = listOf("model.int8.onnx", "voices.bin", "tokens.txt", "espeak-ng-data")
        )
        TtsModel.GOSIA -> ModelArtifact(
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pl_PL-gosia-medium-int8.tar.bz2",
            sha256 = "72acac4c4b031725c41a61b3af0314a3d30e1ec2cd83ee410ea5f9e6d2d9d4fb",
            requiredFiles = listOf("pl_PL-gosia-medium.onnx", "tokens.txt", "espeak-ng-data")
        )
        else -> null
    }
