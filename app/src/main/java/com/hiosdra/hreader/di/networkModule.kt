package com.hiosdra.hreader.di

import com.hiosdra.hreader.BuildConfig
import com.hiosdra.hreader.data.remote.AuthInterceptor
import com.hiosdra.hreader.data.remote.MinifluxApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

val networkModule = module {
    single { AuthInterceptor() }
    single {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("X-Auth-Token")
            redactHeader("CF-Access-Client-Id")
            redactHeader("CF-Access-Client-Secret")
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(get<AuthInterceptor>())
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.MINIFLUX_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    single { get<Retrofit>().create(MinifluxApiService::class.java) }
}
