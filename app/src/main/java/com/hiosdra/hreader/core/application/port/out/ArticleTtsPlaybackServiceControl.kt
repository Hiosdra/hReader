package com.hiosdra.hreader.core.application.port.out

interface ArticleTtsPlaybackServiceControl {
    fun start(): Boolean
    fun stop()
}
