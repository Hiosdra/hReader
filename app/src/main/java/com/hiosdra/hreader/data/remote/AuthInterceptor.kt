package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("X-Auth-Token", BuildConfig.MINIFLUX_API_KEY)
            .addHeader("CF-Access-Client-Id", "7deac5772438964e5e8571c789fa32a7.access")
            .addHeader("CF-Access-Client-Secret", "cd2c46e1ad8514a31ac48ad25488adb66a2ca978d07436740b1a0559ac90df9b")
            .build()
        return chain.proceed(request)
    }
}
