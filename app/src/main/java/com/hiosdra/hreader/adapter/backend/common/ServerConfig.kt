package com.hiosdra.hreader.adapter.backend.common

import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.core.application.port.out.BackendPreferences
import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.BackendSessionStore
import com.hiosdra.hreader.core.application.settings.BackendConfiguration
import com.hiosdra.hreader.core.application.port.out.CacheScope
import com.hiosdra.hreader.core.application.port.out.SyncSession
import com.hiosdra.hreader.core.application.port.out.SyncSessionGate
import com.hiosdra.hreader.core.application.exception.StaleSyncSessionException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val GOOGLE_READER_ENDPOINT = "api/greader.php"

class ServerConfig(private val preferencesManager: BackendPreferences) :
    BackendIdentity,
    BackendSessionStore,
    SyncSessionGate {
    private val committedConfiguration = AtomicReference<BackendConfiguration?>(null)
    private val sessionGeneration = AtomicLong(0L)
    private val sessionMutex = Mutex()
    private val sessionLockOwner = Any()

    fun backendType(): BackendType = configuration().backendType

    fun serverUrlFor(backendType: BackendType): String = configuration().serverUrlFor(backendType).trim()

    fun secretFor(backendType: BackendType): String = configuration().secretFor(backendType)

    fun username(): String = configuration().freshRssUsername.trim()

    override fun getBackendConfiguration(): BackendConfiguration = configuration()

    override suspend fun commitBackendConfiguration(configuration: BackendConfiguration) {
        withSessionLock(permitsConfigurationChanges = true) {
            committedConfiguration.set(configuration)
            sessionGeneration.incrementAndGet()
            preferencesManager.setBackendConfiguration(configuration)
        }
    }

    override suspend fun <T> withTemporaryBackendConfiguration(
        configuration: BackendConfiguration,
        block: suspend () -> T
    ): T = withSessionLock(permitsConfigurationChanges = true) {
        val previous = committedConfiguration.get()
        val previousGeneration = sessionGeneration.get()
        committedConfiguration.set(configuration)
        sessionGeneration.incrementAndGet()
        try {
            block()
        } finally {
            committedConfiguration.set(previous)
            sessionGeneration.set(previousGeneration)
        }
    }

    override fun currentSession(): SyncSession = SyncSession(
        ownerKey = cacheOwnerKey(configuration()),
        generation = sessionGeneration.get()
    )

    override fun isCurrent(session: SyncSession): Boolean {
        val current = configuration()
        return session.generation == sessionGeneration.get() &&
            session.ownerKey == cacheOwnerKey(current)
    }

    override suspend fun <T> withSession(
        session: SyncSession,
        block: suspend () -> T
    ): T = withSessionLock(permitsConfigurationChanges = false) {
        if (!isCurrent(session)) throw StaleSyncSessionException()
        block()
    }

    override suspend fun <T> withSessionChange(block: suspend () -> T): T =
        withSessionLock(permitsConfigurationChanges = true, block = block)

    private suspend fun <T> withSessionLock(
        permitsConfigurationChanges: Boolean,
        block: suspend () -> T
    ): T {
        val context = currentCoroutineContext()[SessionLockContext]
        if (context?.owner === sessionLockOwner) {
            if (permitsConfigurationChanges && !context.permitsConfigurationChanges) {
                error("A session change cannot start inside a session operation")
            }
            return block()
        }
        return sessionMutex.withLock {
            withContext(
                SessionLockContext(
                    owner = sessionLockOwner,
                    permitsConfigurationChanges = permitsConfigurationChanges
                )
            ) {
                block()
            }
        }
    }

    private class SessionLockContext(
        val owner: Any,
        val permitsConfigurationChanges: Boolean
    ) : CoroutineContext.Element {
        companion object Key : CoroutineContext.Key<SessionLockContext>

        override val key: CoroutineContext.Key<SessionLockContext>
            get() = Key
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
        val configuration = configuration()
        val backendType = configuration.backendType
        return "$backendType|${configuration.serverUrlFor(backendType).trim()}|" +
            "${configuration.usernameFor(backendType)}|${configuration.secretFor(backendType).sha256()}"
    }

    override fun cacheOwnerKey(): String {
        return cacheOwnerKey(configuration())
    }

    private fun configuration(): BackendConfiguration =
        committedConfiguration.get() ?: preferencesManager.getBackendConfiguration()

    private fun cacheOwnerKey(configuration: BackendConfiguration): String {
        val backend = configuration.backendType
        val server = normalizedRootFor(backend, configuration)
            ?.let { root -> if (backend == BackendType.FRESHRSS) root.removeSuffix("/$GOOGLE_READER_ENDPOINT") else root }
            ?: configuration.serverUrlFor(backend).trim().lowercase().trimEnd('/')
        val identity = if (backend.requiresUsername) {
            configuration.usernameFor(backend).trim()
        } else {
            "token:${configuration.secretFor(backend).sha256()}"
        }
        return CacheScope(backend, server, identity).key
    }

    private fun normalizedRootFor(
        backendType: BackendType,
        config: BackendConfiguration = configuration()
    ): String? {
        val server = config.serverUrlFor(backendType).trim()
        if (server.isEmpty()) return null
        val absolute = if (server.startsWith("http://") || server.startsWith("https://")) server else "https://$server"
        return absolute.toHttpUrlOrNull()?.toString()?.trimEnd('/') ?: absolute.trimEnd('/')
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
