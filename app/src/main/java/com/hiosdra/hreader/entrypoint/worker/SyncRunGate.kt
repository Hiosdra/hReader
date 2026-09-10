package com.hiosdra.hreader.entrypoint.worker

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncRunGate {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
