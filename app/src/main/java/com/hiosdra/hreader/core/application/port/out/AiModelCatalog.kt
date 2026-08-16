package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.SelectedModelStatus

interface AiModelCatalog {
    suspend fun getModels(forceRefresh: Boolean = false): List<AiModel>
    suspend fun checkSelectedModel(): SelectedModelStatus
}
