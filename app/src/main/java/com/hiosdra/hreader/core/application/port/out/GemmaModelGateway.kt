package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.ai.GemmaModelStatus
import com.hiosdra.hreader.core.application.ai.GemmaModelDownloadPreflight
import kotlinx.coroutines.flow.StateFlow

interface GemmaModelGateway {
    val status: StateFlow<GemmaModelStatus>
    val modelSizeBytes: Long

    fun downloadPreflight(): GemmaModelDownloadPreflight
    fun markDownloadEnqueued()
    fun markDownloadCancelled()
    fun markDownloadFailed(message: String)
    fun markDownloadRetrying()
    suspend fun download()
    suspend fun remove()
}
