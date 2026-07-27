package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.preferences.PreferencesManager
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val GOOGLE_READER_ENDPOINT = "api/greader.php"

class FreshRssServerConfig(private val preferencesManager: PreferencesManager) {

    fun serverUrl(): String = preferencesManager.getFreshRssServerUrl().trim()

    fun username(): String = preferencesManager.getFreshRssUsername().trim()

    fun apiPassword(): String = preferencesManager.getFreshRssApiPassword()

    fun isComplete(): Boolean =
        googleReaderBaseUrl() != null && username().isNotEmpty() && apiPassword().isNotEmpty()

    fun googleReaderBaseUrl(): HttpUrl? {
        val server = serverUrl()
        if (server.isEmpty()) return null
        val absolute = if (server.startsWith("http://") || server.startsWith("https://")) server else "https://$server"
        val root = absolute.trimEnd('/')
        val endpoint = if (root.endsWith(GOOGLE_READER_ENDPOINT)) root else "$root/$GOOGLE_READER_ENDPOINT"
        return "$endpoint/".toHttpUrlOrNull()
    }

    fun credentialsFingerprint(): String = "${serverUrl()}|${username()}|${apiPassword().hashCode()}"
}
