package com.hiosdra.hreader.adapter.backend.common

import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.core.application.port.out.BackendPreferences
import com.hiosdra.hreader.core.application.settings.BackendConfiguration
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.hiosdra.hreader.adapter.preferences.PreferencesManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigTest {

    @Test
    fun `google reader endpoint is appended to a bare host`() {
        val config = configFor(serverUrl = "rss.example.com")

        assertEquals("https://rss.example.com/api/greader.php/", config.googleReaderBaseUrl().toString())
    }

    @Test
    fun `trailing slash does not duplicate the endpoint`() {
        val config = configFor(serverUrl = "https://rss.example.com/")

        assertEquals("https://rss.example.com/api/greader.php/", config.googleReaderBaseUrl().toString())
    }

    @Test
    fun `endpoint already present in the configured url is kept`() {
        val config = configFor(serverUrl = "https://rss.example.com/api/greader.php")

        assertEquals("https://rss.example.com/api/greader.php/", config.googleReaderBaseUrl().toString())
    }

    @Test
    fun `subdirectory installations are supported`() {
        val config = configFor(serverUrl = "https://example.com/freshrss")

        assertEquals("https://example.com/freshrss/api/greader.php/", config.googleReaderBaseUrl().toString())
    }

    @Test
    fun `miniflux base url is the plain server root`() {
        val config = configFor(backendType = BackendType.MINIFLUX, serverUrl = "miniflux.example.com")

        assertEquals("https://miniflux.example.com/", config.minifluxBaseUrl().toString())
    }

    @Test
    fun `blank server url yields no base url`() {
        val config = configFor(serverUrl = "   ")

        assertNull(config.googleReaderBaseUrl())
        assertFalse(config.isComplete())
    }

    @Test
    fun `freshrss configuration requires a username`() {
        assertTrue(configFor(serverUrl = "rss.example.com").isComplete())
        assertFalse(configFor(serverUrl = "rss.example.com", username = "").isComplete())
        assertFalse(configFor(serverUrl = "rss.example.com", secret = "").isComplete())
    }

    @Test
    fun `miniflux configuration does not require a username`() {
        val config = configFor(backendType = BackendType.MINIFLUX, serverUrl = "miniflux.example.com", username = "")

        assertTrue(config.isComplete())
    }

    @Test
    fun `fingerprint changes when credentials change`() {
        val before = configFor(serverUrl = "rss.example.com", secret = "one").credentialsFingerprint()
        val after = configFor(serverUrl = "rss.example.com", secret = "two").credentialsFingerprint()

        assertTrue(before != after)
    }

    @Test
    fun `fingerprint does not rely on colliding string hash codes`() {
        val before = configFor(serverUrl = "rss.example.com", secret = "FB").credentialsFingerprint()
        val after = configFor(serverUrl = "rss.example.com", secret = "Ea").credentialsFingerprint()

        assertTrue(before != after)
    }

    @Test
    fun `fingerprint changes when the backend changes`() {
        val freshRss = configFor(backendType = BackendType.FRESHRSS, serverUrl = "rss.example.com")
        val miniflux = configFor(backendType = BackendType.MINIFLUX, serverUrl = "rss.example.com")

        assertTrue(freshRss.credentialsFingerprint() != miniflux.credentialsFingerprint())
    }

    @Test
    fun `cache owner stays stable when credentials rotate`() {
        val before = configFor(serverUrl = "rss.example.com", secret = "one").cacheOwnerKey()
        val after = configFor(serverUrl = "rss.example.com", secret = "two").cacheOwnerKey()

        assertEquals(before, after)
    }

    @Test
    fun `miniflux cache owner changes when its token changes`() {
        val before = configFor(
            backendType = BackendType.MINIFLUX,
            serverUrl = "miniflux.example.com",
            secret = "token-one"
        ).cacheOwnerKey()
        val after = configFor(
            backendType = BackendType.MINIFLUX,
            serverUrl = "miniflux.example.com",
            secret = "token-two"
        ).cacheOwnerKey()

        assertTrue(before != after)
        assertFalse(before.contains("token-one"))
        assertFalse(after.contains("token-two"))
    }

    @Test
    fun `cache owner treats a configured reader endpoint as the same server`() {
        val root = configFor(serverUrl = "https://rss.example.com")
        val endpoint = configFor(serverUrl = "https://rss.example.com/api/greader.php")

        assertEquals(root.cacheOwnerKey(), endpoint.cacheOwnerKey())
    }

    @Test
    fun `committing a configuration invalidates the previous session`() = runBlocking {
        val preferencesManager = mockk<BackendPreferences>(relaxed = true)
        val first = BackendConfiguration(
            backendType = BackendType.FRESHRSS,
            freshRssServerUrl = "https://rss.example.com",
            freshRssUsername = "reader",
            freshRssSecret = "secret"
        )
        val second = first.copy(freshRssUsername = "other-reader")
        every { preferencesManager.getBackendConfiguration() } returns first
        every { preferencesManager.setBackendConfiguration(second) } returns Unit
        val config = ServerConfig(preferencesManager)
        val oldSession = config.currentSession()

        config.commitBackendConfiguration(second)

        assertFalse(config.isCurrent(oldSession))
        assertTrue(config.isCurrent(config.currentSession()))
    }

    @Test
    fun `temporary configuration is restored without persisting it`() = runBlocking {
        val preferencesManager = mockk<BackendPreferences>(relaxed = true)
        val first = BackendConfiguration(
            backendType = BackendType.FRESHRSS,
            freshRssServerUrl = "https://rss.example.com",
            freshRssUsername = "reader",
            freshRssSecret = "secret"
        )
        val second = first.copy(freshRssUsername = "other-reader")
        every { preferencesManager.getBackendConfiguration() } returns first
        val config = ServerConfig(preferencesManager)
        val originalSession = config.currentSession()

        val observed: String = config.withTemporaryBackendConfiguration(second) {
            assertEquals(second, config.getBackendConfiguration())
            assertFalse(config.isCurrent(originalSession))
            "verified"
        }

        assertEquals("verified", observed)
        assertEquals(first, config.getBackendConfiguration())
        assertTrue(config.isCurrent(originalSession))
        verify(exactly = 0) { preferencesManager.setBackendConfiguration(any()) }
    }

    @Test
    fun `session operations can be composed inside a configuration change`() = runBlocking {
        val config = configFor(serverUrl = "rss.example.com")

        val observed = withTimeout(1_000) {
            config.withSessionChange {
                val session = config.currentSession()
                config.withSession(session) {
                    config.withSessionChange { config.getBackendConfiguration() }
                }
            }
        }

        assertEquals(config.getBackendConfiguration(), observed)
    }

    private fun configFor(
        backendType: BackendType = BackendType.FRESHRSS,
        serverUrl: String,
        username: String = "reader",
        secret: String = "secret"
    ): ServerConfig {
        val preferencesManager = mockk<PreferencesManager>()
        every { preferencesManager.getBackendType() } returns backendType
        every { preferencesManager.getServerUrl(any()) } returns serverUrl
        every { preferencesManager.getFreshRssUsername() } returns username
        every { preferencesManager.getBackendSecret(any()) } returns secret
        every { preferencesManager.getBackendConfiguration() } returns BackendConfiguration(
            backendType = backendType,
            freshRssServerUrl = serverUrl,
            freshRssUsername = username,
            freshRssSecret = secret,
            minifluxServerUrl = serverUrl,
            minifluxSecret = secret
        )
        return ServerConfig(preferencesManager)
    }
}
