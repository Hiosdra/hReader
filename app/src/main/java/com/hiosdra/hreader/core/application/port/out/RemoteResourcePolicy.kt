package com.hiosdra.hreader.core.application.port.out

interface RemoteResourcePolicy {
    fun allows(url: String): Boolean
}
