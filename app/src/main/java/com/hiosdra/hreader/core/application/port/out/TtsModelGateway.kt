package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
import kotlinx.coroutines.flow.StateFlow

interface TtsModelGateway {
    val statuses: StateFlow<Map<TtsModel, TtsModelStatus>>
    fun markDownloadEnqueued(model: TtsModel)
    fun markDownloadCancelled(model: TtsModel)
    fun markDownloadFailed(model: TtsModel, message: String)
    fun markDownloadRetrying(model: TtsModel)
    suspend fun download(model: TtsModel)
    suspend fun remove(model: TtsModel)
}
