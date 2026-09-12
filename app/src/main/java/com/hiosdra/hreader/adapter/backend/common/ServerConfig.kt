package com.hiosdra.hreader.adapter.backend.common

import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.core.application.port.out.BackendPreferences
import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.BackendSessionStore
import com.hiosdra.hreader.core.application.settings.BackendConfiguration
import com.hiosdra.hreader.core.application.port.out.CacheScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val GOOGLE_READER_ENDPOINT = "api/greader.php"

class ServerConfig(private val preferencesManager: BackendPreferences) : BackendIdentity, BackendSessionStore {
    private val activeConfiguration = AtomicReference<BackendConfiguration?>(null)
    private val configurationMutex = Mutex()

    fun backendType(): BackendType = configuration().backendType

    fun serverUrlFor(backendType: BackendType): String = configuration().serverUrlFor(backendType).trim()

    fun secretFor(backendType: BackendType): String = configuration().secretFor(backendType)

    fun username(): String = configuration().freshRssUsername.trim()

    override fun getBackendConfiguration(): BackendConfiguration = configuration()

    override suspend fun commitBackendConfiguration(configuration: BackendConfiguration) {
        configurationMutex.withLock {
            activeConfiguration.set(configuration)
            preferencesManager.setBackendConfiguration(configuration)
        }
    }

    override suspend fun <T> withTemporaryBackendConfiguration(
        configuration: BackendConfiguration,
        block: suspend () -> T
    ): T = configurationMutex.withLock {
        val previous = activeConfiguration.get()
        activeConfiguration.set(configuration)
        try {
            block()
        } finally {
            activeConfiguration.set(previous)
        }
    }

    override fun isComplete(): Boolean = when (val backendType = backendType()) {
        BackendType.FRESHRSS ->
            googleReaderBaseUrl() != null && username().isNotEmpty() && secretFor(backendType).isNotEmpty()
        BackendType.MINIFLUX ->
            minifluxBaseUrl() != null && secretFor(backendType).isNotEmpty()
    }

    fun googleReaderBaseUrl(): HttpUrl? {
        val root = normalizedRootFor(BackendType.FRESHRSS) ?: return null
        val endpoint = if (root.endsWith(GOOGLE_READER_ENDPOINT)) root else "$root/$GOOGLE_READER_ENDPOINT"
        return "$endpoint/".toHttpUrlOrNull()
    }

    fun minifluxBaseUrl(): HttpUrl? {
        val root = normalizedRootFor(BackendType.MINIFLUX) ?: return null
        return "$root/".toHttpUrlOrNull()
    }

    fun credentialsFingerprint(): String {
        val backendType = backendType()
        return "$backendType|${serverUrlFor(backendType)}|${username()}|${secretFor(backendType).sha256()}"
    }

    override fun cacheOwnerKey(): String {
        val backend = backendType()
        val server = normalizedRootFor(backend)
            ?.let { root ->
                if (backend == BackendType.FRESHRSS) {
                    root.removeSuffix("/$GOOGLE_READER_ENDPOINT")
                } else {
                    root
                }
            }
            ?: serverUrlFor(backend).trim().lowercase().trimEnd('/')
        val identity = if (backend.requiresUsername) {
            username()
        } else {
            "token:${secretFor(backend).sha256()}"
        }
        return CacheScope(backend, server, identity).key
    }

    private fun normalizedRootFor(backendType: BackendType): String? {
        val server = serverUrlFor(backendType)
        if (server.isEmpty()) return null
        val absolute = if (server.startsWith("http://") || server.startsWith("https://")) server else "https://$server"
        return absolute.toHttpUrlOrNull()?.toString()?.trimEnd('/') ?: absolute.trimEnd('/')
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun configuration(): BackendConfiguration =
        activeConfiguration.get() ?: preferencesManager.getBackendConfiguration()
}
