package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.tts.TtsModel

interface TtsModelDownloadRequester {
    fun enqueueDownload(model: TtsModel)
    fun cancelDownload(model: TtsModel)
}
