package com.hiosdra.hreader.data.remote

import android.content.Context
import com.hiosdra.hreader.R
import okhttp3.Interceptor
import okhttp3.Response

const val FRESHRSS_PLACEHOLDER_HOST = "freshrss.invalid"
const val MINIFLUX_PLACEHOLDER_HOST = "miniflux.invalid"
const val FRESHRSS_PLACEHOLDER_BASE_URL = "https://$FRESHRSS_PLACEHOLDER_HOST/"
const val MINIFLUX_PLACEHOLDER_BASE_URL = "https://$MINIFLUX_PLACEHOLDER_HOST/"

class BackendUrlInterceptor(
    private val config: ServerConfig,
    context: Context
) : Interceptor {
    private val appContext = context.applicationContext

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val baseUrl = when (request.url.host) {
            FRESHRSS_PLACEHOLDER_HOST -> config.googleReaderBaseUrl()
                ?: throw BackendNotConfiguredException(
                    appContext.getString(
                        R.string.backend_not_configured,
                        appContext.getString(R.string.backend_freshrss)
                    )
                )
            MINIFLUX_PLACEHOLDER_HOST -> config.minifluxBaseUrl()
                ?: throw BackendNotConfiguredException(
                    appContext.getString(
                        R.string.backend_not_configured,
                        appContext.getString(R.string.backend_miniflux)
                    )
                )
            else -> return chain.proceed(request)
        }
        val target = request.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .encodedPath(baseUrl.encodedPath + request.url.encodedPath.removePrefix("/"))
            .build()
        return chain.proceed(request.newBuilder().url(target).build())
    }
}
