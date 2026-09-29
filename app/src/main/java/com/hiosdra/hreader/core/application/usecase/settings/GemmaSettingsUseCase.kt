package com.hiosdra.hreader.core.application.usecase.settings

import com.hiosdra.hreader.core.application.ai.GemmaModelDownloadPreflight
import com.hiosdra.hreader.core.application.ai.GemmaModelStatus
import com.hiosdra.hreader.core.application.port.out.GemmaModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
import com.hiosdra.hreader.core.application.port.out.GemmaModelLifecycle
import kotlinx.coroutines.flow.StateFlow

data class GemmaSettingsSnapshot(
    val status: GemmaModelStatus,
    val modelSizeBytes: Long,
    val preflight: GemmaModelDownloadPreflight
)

class GemmaSettingsUseCase(
    private val model: GemmaModelGateway,
    private val downloads: GemmaModelDownloadRequester,
    private val lifecycle: GemmaModelLifecycle
) {
    val status: StateFlow<GemmaModelStatus> = model.status
    val modelSizeBytes: Long = model.modelSizeBytes

    fun current(): GemmaSettingsSnapshot = GemmaSettingsSnapshot(
        status = model.status.value,
        modelSizeBytes = model.modelSizeBytes,
        preflight = model.downloadPreflight()
    )

    fun downloadPreflight(): GemmaModelDownloadPreflight = model.downloadPreflight()
    fun enqueueDownload() = downloads.enqueueDownload()
    fun cancelDownload() = downloads.cancelDownload()

    suspend fun removeModel() {
        downloads.cancelDownload()
        lifecycle.close()
        model.remove()
    }
}
