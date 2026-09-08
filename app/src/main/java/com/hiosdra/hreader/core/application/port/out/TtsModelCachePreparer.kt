package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.tts.MnnTtsBackend
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel

interface TtsModelCachePreparer {
    fun prepareCache(
        model: TtsModel,
        settings: TtsAdvancedSettings,
        forceRefresh: Boolean
    ): MnnTtsBackend
}
