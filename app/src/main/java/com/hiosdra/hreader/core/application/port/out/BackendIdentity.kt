package com.hiosdra.hreader.core.application.port.out

interface BackendIdentity {
    fun isComplete(): Boolean
    fun cacheOwnerKey(): String
}
