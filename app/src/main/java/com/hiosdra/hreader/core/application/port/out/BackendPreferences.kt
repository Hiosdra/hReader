package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.core.application.settings.BackendConfiguration

interface BackendPreferences {
    fun getBackendType(): BackendType
    fun setBackendType(backendType: BackendType)
    fun getServerUrl(backendType: BackendType): String
    fun setServerUrl(backendType: BackendType, url: String)
    fun getBackendSecret(backendType: BackendType): String
    fun setBackendSecret(backendType: BackendType, secret: String)
    fun getFreshRssUsername(): String
    fun setFreshRssUsername(username: String)
    fun hasBackendCredentials(): Boolean

    fun getBackendConfiguration(): BackendConfiguration = BackendConfiguration(
        backendType = getBackendType(),
        freshRssServerUrl = getServerUrl(BackendType.FRESHRSS),
        freshRssUsername = getFreshRssUsername(),
        freshRssSecret = getBackendSecret(BackendType.FRESHRSS),
        minifluxServerUrl = getServerUrl(BackendType.MINIFLUX),
        minifluxSecret = getBackendSecret(BackendType.MINIFLUX)
    )

    fun setBackendConfiguration(configuration: BackendConfiguration) {
        setBackendType(configuration.backendType)
        setServerUrl(BackendType.FRESHRSS, configuration.freshRssServerUrl)
        setFreshRssUsername(configuration.freshRssUsername)
        setBackendSecret(BackendType.FRESHRSS, configuration.freshRssSecret)
        setServerUrl(BackendType.MINIFLUX, configuration.minifluxServerUrl)
        setBackendSecret(BackendType.MINIFLUX, configuration.minifluxSecret)
    }
}
