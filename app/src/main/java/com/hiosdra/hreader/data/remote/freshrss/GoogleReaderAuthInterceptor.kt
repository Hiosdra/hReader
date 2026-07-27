package com.hiosdra.hreader.data.remote.freshrss

import com.hiosdra.hreader.data.remote.FRESHRSS_PLACEHOLDER_HOST
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.HttpURLConnection

class GoogleReaderAuthInterceptor(
    private val authenticator: GoogleReaderAuthenticator
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != FRESHRSS_PLACEHOLDER_HOST) return chain.proceed(request)

        val response = chain.proceed(request.withGoogleLogin(authenticator.authToken()))
        if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) return response

        response.close()
        authenticator.invalidate()
        return chain.proceed(request.withGoogleLogin(authenticator.authToken()))
    }
}

private fun Request.withGoogleLogin(token: String): Request =
    newBuilder().header("Authorization", "GoogleLogin auth=$token").build()
