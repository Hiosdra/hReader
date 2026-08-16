package com.hiosdra.hreader.adapter.backend.miniflux

import android.content.Context
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.core.application.exception.BackendNotConfiguredException
import com.hiosdra.hreader.adapter.backend.common.MINIFLUX_PLACEHOLDER_HOST
import com.hiosdra.hreader.adapter.backend.common.ServerConfig
import okhttp3.Interceptor
import okhttp3.Response

class MinifluxAuthInterceptor(
    private val config: ServerConfig,
    context: Context
) : Interceptor {
    private val appContext = context.applicationContext

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != MINIFLUX_PLACEHOLDER_HOST) return chain.proceed(request)

        val apiToken = config.secretFor(BackendType.MINIFLUX)
        if (apiToken.isEmpty()) {
            throw BackendNotConfiguredException(
                appContext.getString(R.string.backend_miniflux_token_missing)
            )
        }

        return chain.proceed(request.newBuilder().header("X-Auth-Token", apiToken).build())
    }
}
