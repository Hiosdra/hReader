package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.preferences.PreferencesManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshRssServerConfigTest {

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
    fun `blank server url yields no base url`() {
        val config = configFor(serverUrl = "   ")

        assertNull(config.googleReaderBaseUrl())
        assertFalse(config.isComplete())
    }

    @Test
    fun `configuration is complete only when all fields are present`() {
        assertTrue(configFor(serverUrl = "rss.example.com").isComplete())
        assertFalse(configFor(serverUrl = "rss.example.com", username = "").isComplete())
        assertFalse(configFor(serverUrl = "rss.example.com", apiPassword = "").isComplete())
    }

    @Test
    fun `fingerprint changes when credentials change`() {
        val before = configFor(serverUrl = "rss.example.com", apiPassword = "one").credentialsFingerprint()
        val after = configFor(serverUrl = "rss.example.com", apiPassword = "two").credentialsFingerprint()

        assertTrue(before != after)
    }

    private fun configFor(
        serverUrl: String,
        username: String = "reader",
        apiPassword: String = "secret"
    ): FreshRssServerConfig {
        val preferencesManager = mockk<PreferencesManager>()
        every { preferencesManager.getFreshRssServerUrl() } returns serverUrl
        every { preferencesManager.getFreshRssUsername() } returns username
        every { preferencesManager.getFreshRssApiPassword() } returns apiPassword
        return FreshRssServerConfig(preferencesManager)
    }
}
