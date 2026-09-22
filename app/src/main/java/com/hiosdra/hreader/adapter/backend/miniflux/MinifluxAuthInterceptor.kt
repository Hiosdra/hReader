package com.hiosdra.hreader.adapter.backend.miniflux

import android.content.Context
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.core.application.exception.BackendNotConfiguredException
import com.hiosdra.hreader.adapter.backend.common.ServerConfig
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class MinifluxAuthInterceptor(
    private val config: ServerConfig,
    context: Context
) : Interceptor {
    private val appContext = context.applicationContext

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val minifluxBaseUrl = config.minifluxBaseUrl()
        if (minifluxBaseUrl == null || !request.url.isSameOriginAs(minifluxBaseUrl)) {
            return chain.proceed(request.newBuilder().removeHeader(AUTH_HEADER).build())
        }

        val apiToken = config.secretFor(BackendType.MINIFLUX)
        if (apiToken.isEmpty()) {
            throw BackendNotConfiguredException(
                appContext.getString(R.string.backend_miniflux_token_missing)
            )
        }

        return chain.proceed(request.newBuilder().header(AUTH_HEADER, apiToken).build())
    }

    private fun HttpUrl.isSameOriginAs(other: HttpUrl): Boolean =
        scheme == other.scheme && host == other.host && port == other.port

    private companion object {
        const val AUTH_HEADER = "X-Auth-Token"
    }
}
