package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.BackendType

data class CacheScope(
    val backendType: BackendType,
    val server: String,
    val account: String
) {
    val key: String
        get() = "$backendType|$server|$account"
}
