package com.hiosdra.hreader.adapter.tts

import android.content.Context
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
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
) : TtsModelGateway {
    private val appContext = context.applicationContext
    private val client = client.newBuilder().apply { interceptors().clear() }.build()
    private val modelRoot = File(appContext.filesDir, "tts_models")
    private val modelLocks = TtsModelCatalog.models.associateWith { Mutex() }
    private val _statuses = MutableStateFlow(currentStatuses())
    override val statuses: StateFlow<Map<TtsModel, TtsModelStatus>> = _statuses.asStateFlow()

    fun directory(model: TtsModel): File = File(modelRoot, TtsModelPackageCatalog.directoryName(model))

    override fun markDownloadEnqueued(model: TtsModel) {
        _statuses.value = _statuses.value + (model to TtsModelStatus.Downloading(0f))
    }

    override fun markDownloadCancelled(model: TtsModel) {
        if (_statuses.value[model] is TtsModelStatus.Downloading) {
            _statuses.value = _statuses.value + (model to TtsModelStatus.NotInstalled)
        }
    }

    override fun markDownloadFailed(model: TtsModel, message: String) {
        _statuses.value = _statuses.value + (model to TtsModelStatus.Failed(message))
    }

    override fun markDownloadRetrying(model: TtsModel) {
        val progress = (_statuses.value[model] as? TtsModelStatus.Downloading)?.progress ?: 0f
        _statuses.value = _statuses.value + (model to TtsModelStatus.Downloading(progress))
    }

    override suspend fun download(model: TtsModel) = withContext(Dispatchers.IO) {
        val artifact = TtsModelPackageCatalog.packageFor(model) ?: return@withContext
        modelLocks.getValue(model).withLock {
            if (_statuses.value[model] == TtsModelStatus.Available) return@withLock
            modelRoot.mkdirs()
            val directoryName = TtsModelPackageCatalog.directoryName(model)
            val archive = File(modelRoot, "$directoryName.download")
            val staging = File(modelRoot, "$directoryName.staging")
            try {
                _statuses.value = _statuses.value + (model to TtsModelStatus.Downloading(0f))
                staging.deleteRecursively()
                staging.mkdirs()
                if (artifact.files.isEmpty()) {
                    val archiveSize = checkNotNull(artifact.archive).size
                    downloadArchive(
                        model = model,
                        artifact = artifact,
                        archive = archive,
                        staging = staging,
                        progressTotal = archiveSize + artifact.supplementalFiles.sumOf(RemoteFile::size)
                    )
                } else {
                    try {
                        downloadFiles(model, artifact.files, staging)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        staging.deleteRecursively()
                        staging.mkdirs()
                        downloadArchive(
                            model = model,
                            artifact = artifact,
                            archive = archive,
                            staging = staging,
                            progressTotal = checkNotNull(artifact.archive).size
                        )
                    }
                }
                val content = staging.singleFileOrSelf()
                if (artifact.supplementalFiles.isNotEmpty()) {
                    val archiveSize = checkNotNull(artifact.archive).size
                    downloadFiles(
                        model = model,
                        files = artifact.supplementalFiles,
                        staging = content,
                        progressBase = archiveSize,
                        progressTotal = archiveSize + artifact.supplementalFiles.sumOf(RemoteFile::size)
                    )
                }
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

    private suspend fun downloadFiles(
        model: TtsModel,
        files: List<RemoteFile>,
        staging: File,
        progressBase: Long = 0L,
        progressTotal: Long = files.sumOf(RemoteFile::size)
    ) {
        var downloaded = 0L
        files.forEach { remote ->
            val output = File(staging, remote.name)
            downloadTo(remote.url, output, remote.size) { count ->
                downloaded += count
                updateProgress(model, progressBase + downloaded, progressTotal)
            }
            check(output.sha256() == remote.sha256) {
                "Downloaded ${remote.name} failed integrity check"
            }
        }
    }

    private suspend fun downloadArchive(
        model: TtsModel,
        artifact: TtsModelPackage,
        archive: File,
        staging: File,
        progressBase: Long = 0L,
        progressTotal: Long
    ) {
        val source = checkNotNull(artifact.archive)
        var downloaded = 0L
        downloadTo(source.url, archive, source.size) { count ->
            downloaded += count
            updateProgress(model, progressBase + downloaded, progressTotal)
        }
        check(archive.sha256() == source.sha256) { "Downloaded model failed integrity check" }
        extract(archive, staging)
    }

    private suspend fun downloadTo(url: String, output: File, expectedSize: Long, onBytes: (Long) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed (${response.code})" }
            val body = checkNotNull(response.body)
            check(body.contentLength() < 0 || body.contentLength() <= expectedSize) {
                "Download exceeds the expected model size"
            }
            body.byteStream().use { input ->
                FileOutputStream(output).use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        check(downloaded <= expectedSize) {
                            "Download exceeds the expected model size"
                        }
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

    override suspend fun remove(model: TtsModel) = withContext(Dispatchers.IO) {
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
        val backup = File(modelRoot, "${TtsModelPackageCatalog.directoryName(model)}.backup")
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

    private fun currentStatuses() = TtsModelCatalog.models.associateWith {
        if (it.bundled || TtsModelPackageCatalog.packageFor(it)?.isComplete(directory(it)) == true) {
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
