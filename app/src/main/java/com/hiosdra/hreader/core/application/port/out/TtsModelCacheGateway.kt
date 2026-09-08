package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.tts.MnnTtsBackend
import com.hiosdra.hreader.core.application.tts.TtsModel

interface TtsModelCacheGateway {
    fun isReady(model: TtsModel, backend: MnnTtsBackend): Boolean
    fun invalidate(model: TtsModel, backend: MnnTtsBackend)
    fun markReady(model: TtsModel, backend: MnnTtsBackend)
}
