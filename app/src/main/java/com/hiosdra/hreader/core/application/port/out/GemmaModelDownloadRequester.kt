package com.hiosdra.hreader.core.application.port.out

interface GemmaModelDownloadRequester {
    fun enqueueDownload()
    fun cancelDownload()
}
