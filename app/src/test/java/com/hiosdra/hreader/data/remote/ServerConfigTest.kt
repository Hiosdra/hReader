package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.model.BackendType
import com.hiosdra.hreader.data.preferences.PreferencesManager
import io.mockk.every
import io.mockk.mockk
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
        return ServerConfig(preferencesManager)
    }
}
