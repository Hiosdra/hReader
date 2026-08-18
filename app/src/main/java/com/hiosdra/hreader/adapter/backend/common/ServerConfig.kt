package com.hiosdra.hreader.adapter.backend.common

import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.core.application.port.out.BackendPreferences
import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val GOOGLE_READER_ENDPOINT = "api/greader.php"

class ServerConfig(private val preferencesManager: BackendPreferences) : BackendIdentity {

    fun backendType(): BackendType = preferencesManager.getBackendType()

    fun serverUrlFor(backendType: BackendType): String = preferencesManager.getServerUrl(backendType).trim()

    fun secretFor(backendType: BackendType): String = preferencesManager.getBackendSecret(backendType)

    fun username(): String = preferencesManager.getFreshRssUsername().trim()

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
            ?: serverUrlFor(backend).trim().lowercase().trimEnd('/')
        val identity = if (backend.requiresUsername) username() else ""
        return "$backend|$server|$identity|${secretFor(backend).sha256()}"
    }

    private fun normalizedRootFor(backendType: BackendType): String? {
        val server = serverUrlFor(backendType)
        if (server.isEmpty()) return null
        val absolute = if (server.startsWith("http://") || server.startsWith("https://")) server else "https://$server"
        return absolute.trimEnd('/')
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
