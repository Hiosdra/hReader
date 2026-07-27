package com.hiosdra.hreader.data.remote.freshrss

import com.hiosdra.hreader.data.model.BackendType
import com.hiosdra.hreader.data.remote.ServerConfig
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val CLIENT_LOGIN_PATH = "accounts/ClientLogin"
private const val AUTH_LINE_PREFIX = "Auth="

class GoogleReaderAuthenticator(
    private val config: ServerConfig,
    private val clientProvider: () -> OkHttpClient
) {
    private var cachedToken: String? = null
    private var cachedFingerprint: String? = null

    @Synchronized
    fun authToken(): String {
        val fingerprint = config.credentialsFingerprint()
        cachedToken?.takeIf { cachedFingerprint == fingerprint }?.let { return it }
        return login().also {
            cachedToken = it
            cachedFingerprint = fingerprint
        }
    }

    @Synchronized
    fun invalidate() {
        cachedToken = null
        cachedFingerprint = null
    }

    private fun login(): String {
        val baseUrl = config.googleReaderBaseUrl()
            ?: throw IOException("FreshRSS server address is not configured")
        val username = config.username()
        val apiPassword = config.secretFor(BackendType.FRESHRSS)
        if (username.isEmpty() || apiPassword.isEmpty()) {
            throw IOException("FreshRSS username or API password is not configured")
        }
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(CLIENT_LOGIN_PATH).build())
            .post(
                FormBody.Builder()
                    .add("Email", username)
                    .add("Passwd", apiPassword)
                    .build()
            )
            .build()
        clientProvider().newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("FreshRSS login failed with HTTP ${response.code}")
            }
            return body.lineSequence()
                .firstOrNull { it.startsWith(AUTH_LINE_PREFIX) }
                ?.removePrefix(AUTH_LINE_PREFIX)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IOException("FreshRSS login response did not contain an auth token")
        }
    }
}
