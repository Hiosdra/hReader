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
        storage.set(BackendPreferenceKeys.backendType, backendType.name)
    }

    override fun getServerUrl(backendType: BackendType): String =
        storage.get(backendType.serverUrlKey()).orEmpty()

    override fun setServerUrl(backendType: BackendType, url: String) {
        storage.set(backendType.serverUrlKey(), url)
    }

    override fun getBackendSecret(backendType: BackendType): String = secrets.get(backendType.secretKey())

    override fun setBackendSecret(backendType: BackendType, secret: String) {
        secrets.set(backendType.secretKey(), secret)
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
        return getServerUrl(backendType).isNotBlank() &&
            getBackendSecret(backendType).isNotBlank() &&
            (!backendType.requiresUsername || getFreshRssUsername().isNotBlank())
    }
}

private fun BackendType.serverUrlKey() = when (this) {
    BackendType.FRESHRSS -> BackendPreferenceKeys.freshRssServerUrl
    BackendType.MINIFLUX -> BackendPreferenceKeys.minifluxServerUrl
}

private fun BackendType.secretKey() = when (this) {
    BackendType.FRESHRSS -> SecretId.FRESHRSS_API_PASSWORD
    BackendType.MINIFLUX -> SecretId.MINIFLUX_API_TOKEN
}
