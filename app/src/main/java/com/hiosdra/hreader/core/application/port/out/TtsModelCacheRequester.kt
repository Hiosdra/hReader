package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.tts.MnnTtsBackend
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCacheStatus
import kotlinx.coroutines.flow.Flow

interface TtsModelCacheRequester {
    fun observe(model: TtsModel, backend: MnnTtsBackend): Flow<TtsModelCacheStatus>
    fun enqueuePreparation(
        model: TtsModel,
        settings: TtsAdvancedSettings,
        forceRefresh: Boolean
    )
    fun cancelPreparation(model: TtsModel, backend: MnnTtsBackend)
}
