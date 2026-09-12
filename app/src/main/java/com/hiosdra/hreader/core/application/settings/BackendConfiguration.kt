package com.hiosdra.hreader.core.application.settings

import com.hiosdra.hreader.core.domain.model.BackendType

data class BackendConfiguration(
    val backendType: BackendType,
    val freshRssServerUrl: String = "",
    val freshRssUsername: String = "",
    val freshRssSecret: String = "",
    val minifluxServerUrl: String = "",
    val minifluxSecret: String = ""
) {
    fun serverUrlFor(backendType: BackendType): String = when (backendType) {
        BackendType.FRESHRSS -> freshRssServerUrl
        BackendType.MINIFLUX -> minifluxServerUrl
    }

    fun secretFor(backendType: BackendType): String = when (backendType) {
        BackendType.FRESHRSS -> freshRssSecret
        BackendType.MINIFLUX -> minifluxSecret
    }

    fun usernameFor(backendType: BackendType): String = when (backendType) {
        BackendType.FRESHRSS -> freshRssUsername
        BackendType.MINIFLUX -> ""
    }
}
