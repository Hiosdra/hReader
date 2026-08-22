package com.hiosdra.hreader.adapter.ai.gemma

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.GemmaModelDownloadPreflight
import com.hiosdra.hreader.core.application.ai.GemmaModelInsufficientStorageException
import com.hiosdra.hreader.core.application.ai.GemmaModelStatus
import com.hiosdra.hreader.core.application.ai.requiredGemmaDownloadBytes
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
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
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class GemmaModelManager(
    context: Context,
    client: OkHttpClient
) : GemmaModelGateway {
    private val appContext = context.applicationContext
    private val client = client.newBuilder().apply {
        interceptors().clear()
        networkInterceptors().clear()
    }.build()
    private val modelRoot = File(appContext.filesDir, "ai_models")
    private val modelFile = File(modelRoot, "gemma-4-e2b-it.litertlm")
    private val partialFile = File(modelRoot, "gemma-4-e2b-it.litertlm.download")
    private val markerFile = File(modelRoot, "gemma-4-e2b-it.litertlm.sha256")
    private val backupFile = File(modelRoot, "gemma-4-e2b-it.litertlm.backup")
    private val lock = Mutex()
    private val _status = MutableStateFlow(currentStatus())

    override val status: StateFlow<GemmaModelStatus> = _status.asStateFlow()
    override val modelSizeBytes: Long = Gemma4E2bModel.MODEL_SIZE_BYTES

    override fun downloadPreflight(): GemmaModelDownloadPreflight =
        downloadPreflight(partialFile.length())

    fun modelPath(): String {
        check(isInstalled()) { "Gemma 4 E2B is not installed" }
        return modelFile.absolutePath
    }

    fun isInstalled(): Boolean = _status.value == GemmaModelStatus.Available

    override fun markDownloadEnqueued() {
        if (_status.value == GemmaModelStatus.Available) return
        _status.value = GemmaModelStatus.Downloading(currentProgress())
    }

    override fun markDownloadCancelled() {
        if (_status.value is GemmaModelStatus.Downloading) {
            _status.value = GemmaModelStatus.NotInstalled
        }
    }

    override fun markDownloadFailed(message: String) {
        _status.value = GemmaModelStatus.Failed(message)
    }

    override fun markDownloadRetrying() {
        _status.value = GemmaModelStatus.Downloading(currentProgress())
    }

    override suspend fun download() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (isInstalled()) {
                _status.value = GemmaModelStatus.Available
                return@withLock
            }
            check(
                modelRoot.isDirectory || modelRoot.mkdirs()
            ) { "Could not create Gemma model directory" }
            try {
                downloadModel()
                installModel()
                _status.value = GemmaModelStatus.Available
            } catch (e: CancellationException) {
                throw e
            } catch (e: GemmaModelInsufficientStorageException) {
                _status.value = GemmaModelStatus.Failed(
                    appContext.getString(
                        R.string.ai_model_insufficient_storage,
                        e.requiredBytes.toGigabytes(),
                        e.availableBytes.toGigabytes()
                    )
                )
                throw e
            } catch (e: Exception) {
                _status.value = GemmaModelStatus.Failed(
                    appContext.getString(R.string.ai_model_install_failed)
                )
                throw e
            }
        }
    }

    override suspend fun remove() = withContext(Dispatchers.IO) {
        lock.withLock {
            check(!modelFile.exists() || modelFile.delete()) { "Could not remove Gemma model" }
            check(!markerFile.exists() || markerFile.delete()) { "Could not remove Gemma model marker" }
            check(!partialFile.exists() || partialFile.delete()) { "Could not remove Gemma model download" }
            check(!backupFile.exists() || backupFile.delete()) { "Could not remove Gemma model backup" }
            _status.value = GemmaModelStatus.NotInstalled
        }
    }

    private suspend fun downloadModel() {
        var partialBytes = partialFile.length()
        if (partialBytes > Gemma4E2bModel.MODEL_SIZE_BYTES) {
            partialFile.delete()
            partialBytes = 0L
        }
        ensureStorageAvailable(partialBytes)

        var offset = partialBytes
        if (offset == Gemma4E2bModel.MODEL_SIZE_BYTES && partialFile.sha256() == Gemma4E2bModel.MODEL_SHA256) {
            updateProgress(offset)
            return
        }
        if (offset == Gemma4E2bModel.MODEL_SIZE_BYTES) {
            partialFile.delete()
            offset = 0L
            ensureStorageAvailable(offset)
        }

        val request = Request.Builder()
            .url(Gemma4E2bModel.MODEL_URL)
            .apply { if (offset > 0) addHeader("Range", "bytes=$offset-") }
            .build()
        client.newCall(request).execute().use { response ->
            val append = offset > 0 && response.code == 206
            if (!append && response.code != 200) {
                error("Gemma model download failed (${response.code})")
            }
            if (!append && offset > 0) {
                ensureFullDownloadStorage(offset)
            }
            val start = if (append) offset else 0L
            if (!append) {
                partialFile.delete()
            }
            val body = checkNotNull(response.body)
            val expectedBytes = Gemma4E2bModel.MODEL_SIZE_BYTES - start
            check(body.contentLength() < 0 || body.contentLength() <= expectedBytes) {
                "Gemma model response exceeds the expected size"
            }
            body.byteStream().use { input ->
                FileOutputStream(partialFile, append).use { output ->
                    var downloaded = start
                    updateProgress(downloaded)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(downloaded + count <= Gemma4E2bModel.MODEL_SIZE_BYTES) {
                            "Gemma model response exceeds the expected size"
                        }
                        output.write(buffer, 0, count)
                        downloaded += count
                        updateProgress(downloaded)
                    }
                    output.fd.sync()
                }
            }
        }
        check(partialFile.length() == Gemma4E2bModel.MODEL_SIZE_BYTES) {
            "Gemma model download is incomplete"
        }
        check(partialFile.sha256() == Gemma4E2bModel.MODEL_SHA256) {
            "Gemma model failed integrity check"
        }
    }

    private fun ensureStorageAvailable(partialBytes: Long) {
        val preflight = downloadPreflight(partialBytes)
        if (!preflight.hasEnoughStorage) {
            throw GemmaModelInsufficientStorageException(
                requiredBytes = preflight.requiredBytes,
                availableBytes = preflight.availableBytes
            )
        }
    }

    private fun ensureFullDownloadStorage(partialBytes: Long) {
        val preflight = downloadPreflight(0L)
        val availableAfterDiscardingPartial = preflight.availableBytes + partialBytes
        if (availableAfterDiscardingPartial < preflight.requiredBytes) {
            throw GemmaModelInsufficientStorageException(
                requiredBytes = preflight.requiredBytes,
                availableBytes = availableAfterDiscardingPartial
            )
        }
    }

    private fun downloadPreflight(partialBytes: Long): GemmaModelDownloadPreflight {
        val availableBytes = StatFs(appContext.filesDir.path).availableBytes
        val isLowRamDevice = appContext
            .getSystemService(ActivityManager::class.java)
            ?.isLowRamDevice == true
        return GemmaModelDownloadPreflight(
            availableBytes = availableBytes,
            requiredBytes = requiredGemmaDownloadBytes(modelSizeBytes, partialBytes),
            isLowRamDevice = isLowRamDevice
        )
    }

    private fun installModel() {
        check(!backupFile.exists() || backupFile.delete()) { "Could not clear Gemma model backup" }
        if (modelFile.exists()) check(modelFile.renameTo(backupFile)) { "Could not preserve Gemma model" }
        try {
            check(partialFile.renameTo(modelFile)) { "Could not install Gemma model" }
            markerFile.writeText(Gemma4E2bModel.MODEL_SHA256)
            check(!backupFile.exists() || backupFile.delete()) {
                "Could not remove old Gemma model"
            }
        } catch (e: Exception) {
            modelFile.delete()
            backupFile.renameTo(modelFile)
            throw e
        }
    }

    private fun currentStatus(): GemmaModelStatus = runCatching {
        if (
            modelFile.isFile &&
            modelFile.length() == Gemma4E2bModel.MODEL_SIZE_BYTES &&
            markerFile.readText() == Gemma4E2bModel.MODEL_SHA256
        ) {
            GemmaModelStatus.Available
        } else {
            GemmaModelStatus.NotInstalled
        }
    }.getOrDefault(GemmaModelStatus.NotInstalled)

    private fun currentProgress(): Float =
        (partialFile.length().toFloat() / Gemma4E2bModel.MODEL_SIZE_BYTES).coerceIn(0f, 1f)

    private fun updateProgress(downloaded: Long) {
        _status.value = GemmaModelStatus.Downloading(
            (downloaded.toFloat() / Gemma4E2bModel.MODEL_SIZE_BYTES).coerceIn(0f, 1f)
        )
    }

    private fun Long.toGigabytes(): Float = this / 1_000_000_000f

    private suspend fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
