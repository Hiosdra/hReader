package com.hiosdra.hreader.data.remote

import android.util.Log
import com.hiosdra.hreader.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val modifiedRequest = originalRequest.newBuilder()
            .addHeader("X-Auth-Token", BuildConfig.MINIFLUX_API_KEY)
            .addHeader("CF-Access-Client-Id", BuildConfig.CLOUDFLARE_CLIENT_ID)
            .addHeader("CF-Access-Client-Secret", BuildConfig.CLOUDFLARE_CLIENT_SECRET)
            .build()

        // Log headers for debugging
        Log.d("AuthInterceptor", "Headers: ${modifiedRequest.headers}")

        return chain.proceed(modifiedRequest)
    }
}
