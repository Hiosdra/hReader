package com.hiosdra.hreader.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

const val FRESHRSS_PLACEHOLDER_HOST = "freshrss.invalid"
const val FRESHRSS_PLACEHOLDER_BASE_URL = "https://$FRESHRSS_PLACEHOLDER_HOST/"

class FreshRssUrlInterceptor(private val config: FreshRssServerConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != FRESHRSS_PLACEHOLDER_HOST) return chain.proceed(request)

        val baseUrl = config.googleReaderBaseUrl()
            ?: throw IOException("FreshRSS server address is not configured")
        val target = request.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .encodedPath(baseUrl.encodedPath + request.url.encodedPath.removePrefix("/"))
            .build()
        return chain.proceed(request.newBuilder().url(target).build())
    }
}
