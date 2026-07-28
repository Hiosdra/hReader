package com.hiosdra.hreader.data.remote.miniflux

import com.hiosdra.hreader.data.model.BackendType
import com.hiosdra.hreader.data.remote.BackendNotConfiguredException
import com.hiosdra.hreader.data.remote.MINIFLUX_PLACEHOLDER_HOST
import com.hiosdra.hreader.data.remote.ServerConfig
import okhttp3.Interceptor
import okhttp3.Response

class MinifluxAuthInterceptor(private val config: ServerConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != MINIFLUX_PLACEHOLDER_HOST) return chain.proceed(request)

        val apiToken = config.secretFor(BackendType.MINIFLUX)
        if (apiToken.isEmpty()) throw BackendNotConfiguredException("Miniflux API token is not configured")

        return chain.proceed(request.newBuilder().header("X-Auth-Token", apiToken).build())
    }
}
