package com.hiosdra.hreader.adapter.preferences

import com.hiosdra.hreader.core.application.port.out.BackendPreferences
import com.hiosdra.hreader.core.application.settings.BackendConfiguration
import com.hiosdra.hreader.core.domain.model.BackendType

internal class BackendPreferencesStore(
    private val storage: PreferenceStorage,
    private val secrets: SecretPreferences
) : BackendPreferences {
    override fun getBackendType(): BackendType =
        BackendType.fromName(storage.get(BackendPreferenceKeys.backendType))

    override fun setBackendType(backendType: BackendType) {
        storage.update { this[BackendPreferenceKeys.backendType] = backendType.name }
    }

    override fun getServerUrl(backendType: BackendType): String = when (backendType) {
        BackendType.FRESHRSS -> storage.get(BackendPreferenceKeys.freshRssServerUrl).orEmpty()
        BackendType.MINIFLUX -> storage.get(BackendPreferenceKeys.minifluxServerUrl).orEmpty()
    }

    override fun setServerUrl(backendType: BackendType, url: String) {
        storage.update {
            when (backendType) {
                BackendType.FRESHRSS -> this[BackendPreferenceKeys.freshRssServerUrl] = url
                BackendType.MINIFLUX -> this[BackendPreferenceKeys.minifluxServerUrl] = url
            }
        }
    }

    override fun getBackendSecret(backendType: BackendType): String = when (backendType) {
        BackendType.FRESHRSS -> secrets.get(SecretId.FRESHRSS_API_PASSWORD)
        BackendType.MINIFLUX -> secrets.get(SecretId.MINIFLUX_API_TOKEN)
    }

    override fun setBackendSecret(backendType: BackendType, secret: String) {
        when (backendType) {
            BackendType.FRESHRSS -> secrets.set(SecretId.FRESHRSS_API_PASSWORD, secret)
            BackendType.MINIFLUX -> secrets.set(SecretId.MINIFLUX_API_TOKEN, secret)
        }
    }

    override fun getFreshRssUsername(): String = secrets.get(SecretId.FRESHRSS_USERNAME)

    override fun setFreshRssUsername(username: String) {
        secrets.set(SecretId.FRESHRSS_USERNAME, username)
    }

    override fun getBackendConfiguration(): BackendConfiguration = BackendConfiguration(
        backendType = getBackendType(),
        freshRssServerUrl = getServerUrl(BackendType.FRESHRSS),
        freshRssUsername = getFreshRssUsername(),
        freshRssSecret = getBackendSecret(BackendType.FRESHRSS),
        minifluxServerUrl = getServerUrl(BackendType.MINIFLUX),
        minifluxSecret = getBackendSecret(BackendType.MINIFLUX)
    )

    override fun setBackendConfiguration(configuration: BackendConfiguration) {
        storage.update {
            this[BackendPreferenceKeys.backendType] = configuration.backendType.name
            this[BackendPreferenceKeys.freshRssServerUrl] = configuration.freshRssServerUrl
            this[BackendPreferenceKeys.minifluxServerUrl] = configuration.minifluxServerUrl
        }
        secrets.update {
            secrets.writeSecretValue(
                this,
                SecretId.FRESHRSS_USERNAME,
                configuration.freshRssUsername
            )
            secrets.writeSecretValue(
                this,
                SecretId.FRESHRSS_API_PASSWORD,
                configuration.freshRssSecret
            )
            secrets.writeSecretValue(
                this,
                SecretId.MINIFLUX_API_TOKEN,
                configuration.minifluxSecret
            )
        }
    }

    override fun hasBackendCredentials(): Boolean {
        val backendType = getBackendType()
        if (getServerUrl(backendType).isBlank() || getBackendSecret(backendType).isBlank()) return false
        return !backendType.requiresUsername || getFreshRssUsername().isNotBlank()
    }
}
