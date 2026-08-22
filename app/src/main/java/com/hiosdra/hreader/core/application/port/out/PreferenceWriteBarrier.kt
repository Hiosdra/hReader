package com.hiosdra.hreader.core.application.port.out

interface PreferenceWriteBarrier {
    suspend fun awaitReady()
    suspend fun awaitWrites()
}
