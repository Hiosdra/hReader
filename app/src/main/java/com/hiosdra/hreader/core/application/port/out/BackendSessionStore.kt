package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.settings.BackendConfiguration

interface BackendSessionStore {
    fun getBackendConfiguration(): BackendConfiguration

    suspend fun commitBackendConfiguration(configuration: BackendConfiguration)

    suspend fun <T> withTemporaryBackendConfiguration(
        configuration: BackendConfiguration,
        block: suspend () -> T
    ): T
}
